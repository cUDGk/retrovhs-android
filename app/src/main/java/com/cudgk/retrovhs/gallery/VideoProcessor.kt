package com.cudgk.retrovhs.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGLSurface
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decodes a video, applies the VHS shader to each frame via OpenGL,
 * and re-encodes to MP4 (H.264, no audio in v1).
 *
 * Audio passthrough is not yet implemented — the muxer cannot start until
 * the encoder emits its OUTPUT_FORMAT_CHANGED event, which complicates
 * single-pass interleaving. To be added in a follow-up.
 */
object VideoProcessor {
    private const val TAG = "VhsVideoProc"

    fun process(context: Context, source: Uri, intensity: Float, displayName: String): Boolean {
        return try {
            VideoProcessingPipeline(context, source, intensity, displayName).run()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "process failed", t)
            false
        }
    }
}

private class VideoProcessingPipeline(
    private val context: Context,
    private val source: Uri,
    private val intensity: Float,
    private val displayName: String,
) {
    private val extractor = MediaExtractor()
    private var videoTrackIdx = -1
    private lateinit var inputFormat: MediaFormat
    private var outW = 0
    private var outH = 0
    private var fps = 30
    private var bitRate = 8_000_000

    private lateinit var encoder: MediaCodec
    private lateinit var encoderInputSurface: Surface
    private lateinit var decoder: MediaCodec

    private lateinit var gl: OffscreenGl
    private var encoderEglSurface: EGLSurface? = null
    private val renderer = OesRenderer()
    private var oesTexId = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var decoderSurface: Surface

    private val frameAvailable = AtomicBoolean(false)
    private val frameLock = Object()
    private lateinit var frameThread: HandlerThread

    private lateinit var muxer: MediaMuxer
    private lateinit var pfd: ParcelFileDescriptor
    private lateinit var outputUri: Uri
    private var muxerVideoTrack = -1
    private var muxerStarted = false

    fun run() {
        try {
            setup()
            loop()
        } finally {
            cleanup()
        }
    }

    private fun setup() {
        extractor.setDataSource(context, source, null)
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoTrackIdx < 0) {
                videoTrackIdx = i
                inputFormat = fmt
            }
        }
        check(videoTrackIdx >= 0) { "no video track" }
        extractor.selectTrack(videoTrackIdx)

        val rotation = if (inputFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            inputFormat.getInteger(MediaFormat.KEY_ROTATION)
        } else 0
        val rawW = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        val rawH = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        // SurfaceTexture transform handles rotation; output dims should match the on-screen orientation.
        outW = if (rotation % 180 == 0) rawW else rawH
        outH = if (rotation % 180 == 0) rawH else rawW
        fps = if (inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else 30
        bitRate = if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
            inputFormat.getInteger(MediaFormat.KEY_BIT_RATE).coerceAtLeast(4_000_000)
        } else 8_000_000

        // Encoder
        val encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        encoderInputSurface = encoder.createInputSurface()
        encoder.start()

        // GL
        gl = OffscreenGl(width = outW, height = outH, recordable = true)
        encoderEglSurface = gl.createWindowSurface(encoderInputSurface)
        gl.makeCurrent(encoderEglSurface!!)
        renderer.init()

        oesTexId = renderer.createOesTexture()
        frameThread = HandlerThread("VhsVideoProc-frame").apply { start() }
        surfaceTexture = SurfaceTexture(oesTexId).apply {
            setOnFrameAvailableListener({
                synchronized(frameLock) {
                    frameAvailable.set(true)
                    (frameLock as Object).notifyAll()
                }
            }, Handler(frameThread.looper))
        }
        decoderSurface = Surface(surfaceTexture)

        // Decoder
        decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!).apply {
            configure(inputFormat, decoderSurface, null, 0)
            start()
        }

        // Muxer
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RetroVHS")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore.insert failed")
        pfd = resolver.openFileDescriptor(outputUri, "w")
            ?: error("openFileDescriptor failed")
        muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun loop() {
        val timeoutUs = 10_000L
        val info = MediaCodec.BufferInfo()
        val texMatrix = FloatArray(16)
        var inputDone = false
        var outputDone = false
        val startMs = System.currentTimeMillis()

        while (!outputDone) {
            // Feed decoder input
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)
                    if (buf != null) {
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            // Drain decoder; render to encoder; drain encoder.
            var decoderBusy = true
            while (decoderBusy) {
                val outIdx = decoder.dequeueOutputBuffer(info, timeoutUs)
                when {
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> decoderBusy = false
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore */ }
                    outIdx >= 0 -> {
                        val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val render = info.size > 0
                        decoder.releaseOutputBuffer(outIdx, render)
                        if (render) {
                            awaitFrameAvailable(timeoutMs = 2000)
                            surfaceTexture.updateTexImage()
                            surfaceTexture.getTransformMatrix(texMatrix)
                            gl.makeCurrent(encoderEglSurface!!)
                            renderer.draw(
                                oesTexId = oesTexId,
                                texMatrix = texMatrix,
                                intensity = intensity,
                                timeSec = info.presentationTimeUs / 1_000_000f,
                                width = outW,
                                height = outH,
                            )
                            gl.setPresentationTime(encoderEglSurface!!, info.presentationTimeUs * 1000)
                            gl.swapBuffers(encoderEglSurface!!)
                        }
                        drainEncoder(false)
                        if (isEos) {
                            encoder.signalEndOfInputStream()
                            drainEncoder(true)
                            outputDone = true
                            decoderBusy = false
                        }
                    }
                }
                if (System.currentTimeMillis() - startMs > 60_000 * 5) {
                    error("video processing timeout")
                }
            }
        }
    }

    private fun awaitFrameAvailable(timeoutMs: Long) {
        synchronized(frameLock) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!frameAvailable.get()) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) error("timed out waiting for frame")
                (frameLock as Object).wait(left)
            }
            frameAvailable.set(false)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "format changed twice" }
                    muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIdx >= 0 -> {
                    val buf = encoder.getOutputBuffer(outIdx) ?: continue
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(muxerVideoTrack, buf, info)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun cleanup() {
        runCatching { decoder.stop() }
        runCatching { decoder.release() }
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { extractor.release() }

        if (this::surfaceTexture.isInitialized) runCatching { surfaceTexture.release() }
        if (this::decoderSurface.isInitialized) runCatching { decoderSurface.release() }

        encoderEglSurface?.let { runCatching { gl.destroyWindowSurface(it) } }
        runCatching { renderer.release() }
        if (this::gl.isInitialized) runCatching { gl.release() }
        if (this::frameThread.isInitialized) runCatching { frameThread.quitSafely() }

        if (muxerStarted) runCatching { muxer.stop() }
        if (this::muxer.isInitialized) runCatching { muxer.release() }
        if (this::pfd.isInitialized) runCatching { pfd.close() }
        if (this::outputUri.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            runCatching { context.contentResolver.update(outputUri, values, null, null) }
        }
    }
}

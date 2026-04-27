package com.cudgk.retrovhs.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

/**
 * Video MediaCodec encoder writing to a shared MuxerWrapper.
 * Drain is called on the GL thread after each presented frame.
 */
class VideoEncoder(
    private val muxerWrapper: MuxerWrapper,
    val width: Int,
    val height: Int,
    val fps: Int,
    bitRate: Int = 8_000_000,
) {
    val inputSurface: Surface
    private val codec: MediaCodec
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var endOfStreamSignaled = false

    init {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        inputSurface = codec.createInputSurface()
        codec.start()
    }

    fun drain(endOfStream: Boolean) {
        if (endOfStream && !endOfStreamSignaled) {
            codec.signalEndOfInputStream()
            endOfStreamSignaled = true
        }
        while (true) {
            val id = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                id == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                id == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxerWrapper.addTrack(codec.outputFormat)
                }
                id >= 0 -> {
                    val buf = codec.getOutputBuffer(id) ?: continue
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && trackIndex >= 0) {
                        buf.position(bufferInfo.offset)
                        buf.limit(bufferInfo.offset + bufferInfo.size)
                        muxerWrapper.writeSampleData(trackIndex, buf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(id, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }
}

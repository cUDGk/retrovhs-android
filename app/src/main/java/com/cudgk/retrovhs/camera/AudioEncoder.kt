package com.cudgk.retrovhs.camera

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder

/**
 * AAC encoder fed by AudioRecord (mic). Runs on its own thread.
 * Writes to the shared MuxerWrapper.
 */
class AudioEncoder(
    private val muxerWrapper: MuxerWrapper,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 1,
    private val bitRate: Int = 128_000,
) {
    private val codec: MediaCodec
    private val record: AudioRecord
    private val bufferInfo = MediaCodec.BufferInfo()
    private val pcmBufSize: Int
    private var trackIndex = -1
    private var thread: Thread? = null
    @Volatile private var stopFlag = false
    private var startTimeUs: Long = 0

    init {
        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuf > 0) { "AudioRecord.getMinBufferSize failed" }
        pcmBufSize = minBuf * 2

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, pcmBufSize)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        record = createAudioRecord(channelMask)
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(channelMask: Int): AudioRecord {
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
            pcmBufSize,
        )
    }

    fun start() {
        codec.start()
        record.startRecording()
        startTimeUs = System.nanoTime() / 1000
        thread = Thread({ runLoop() }, "RetroVHS-Audio").apply { start() }
    }

    fun stop() {
        stopFlag = true
        thread?.join(2000)
        if (thread?.isAlive == true) thread?.interrupt()
        runCatching { record.stop() }
        runCatching { record.release() }
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    private fun runLoop() {
        val pcm = ByteArray(pcmBufSize)
        while (!stopFlag) {
            val read = record.read(pcm, 0, pcm.size)
            if (read > 0) feed(pcm, read, false)
            drain(false)
        }
        // Retry EOS feed in case input buffer is momentarily unavailable.
        var eosTries = 0
        while (!feed(pcm, 0, true) && eosTries < 10) {
            eosTries++
            Thread.sleep(5)
        }
        drain(true)
    }

    private fun feed(pcm: ByteArray, length: Int, endOfStream: Boolean): Boolean {
        val inputId = codec.dequeueInputBuffer(10_000)
        if (inputId < 0) return false
        val inBuf = codec.getInputBuffer(inputId) ?: return false
        inBuf.clear()
        if (length > 0) inBuf.put(pcm, 0, length)
        val pts = System.nanoTime() / 1000 - startTimeUs
        val flags = if (endOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        codec.queueInputBuffer(inputId, 0, length, pts, flags)
        return true
    }

    private fun drain(endOfStream: Boolean) {
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
}

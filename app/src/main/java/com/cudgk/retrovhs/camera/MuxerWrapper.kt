package com.cudgk.retrovhs.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.nio.ByteBuffer

/**
 * Synchronizes a MediaMuxer between multiple track encoders.
 * Muxer.start() is deferred until all expected tracks have been added.
 */
class MuxerWrapper(
    private val context: Context,
    val outputUri: Uri,
    private val pfd: ParcelFileDescriptor,
    private val expectedTracks: Int,
) {
    private val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val lock = Any()
    private var added = 0
    private var started = false
    private var released = false

    val isStarted: Boolean
        get() = synchronized(lock) { started }

    fun addTrack(format: MediaFormat): Int = synchronized(lock) {
        check(!started) { "muxer already started" }
        val idx = muxer.addTrack(format)
        added++
        if (added == expectedTracks) {
            muxer.start()
            started = true
        }
        idx
    }

    fun writeSampleData(trackIndex: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (!started || released) return
            muxer.writeSampleData(trackIndex, buf, info)
        }
    }

    fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { pfd.close() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                runCatching { context.contentResolver.update(outputUri, values, null, null) }
            }
        }
    }

    companion object {
        fun createForVideo(
            context: Context,
            displayName: String,
            expectedTracks: Int,
        ): MuxerWrapper {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RetroVHS")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore.insert failed")
            val pfd = resolver.openFileDescriptor(uri, "w")
                ?: error("openFileDescriptor failed")
            return MuxerWrapper(context, uri, pfd, expectedTracks)
        }
    }
}

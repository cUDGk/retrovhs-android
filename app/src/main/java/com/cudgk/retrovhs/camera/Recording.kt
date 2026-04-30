package com.cudgk.retrovhs.camera

import android.content.Context
import android.view.Surface

/**
 * Top-level recording controller. Owns muxer + video encoder + (optional) audio encoder.
 * Caller must:
 *   - call start() once after construction
 *   - call drainVideo(false) on the GL thread after each rendered frame
 *   - call stop() (which signals video EOS via drainVideo(true) internally) and finally release()
 */
class Recording(
    context: Context,
    width: Int,
    height: Int,
    fps: Int,
    displayName: String,
    audioEnabled: Boolean,
) {
    private val muxerWrapper: MuxerWrapper
    val video: VideoEncoder
    private val audio: AudioEncoder?
    private var stopped = false

    init {
        val expected = if (audioEnabled) 2 else 1
        muxerWrapper = MuxerWrapper.createForVideo(context, displayName, expected)
        video = VideoEncoder(muxerWrapper, width, height, fps)
        audio = if (audioEnabled) AudioEncoder(muxerWrapper) else null
    }

    val inputSurface: Surface get() = video.inputSurface

    fun start() {
        audio?.start()
    }

    /** Called from GL thread after each frame. */
    fun drainVideo(endOfStream: Boolean) {
        video.drain(endOfStream)
    }

    /**
     * Stops audio thread (joins) and video EOS will be signalled by the renderer.
     * Caller MUST then call drainVideo(true) on the GL thread before release().
     */
    fun stopAudio() {
        audio?.stop()
    }

    fun release() {
        if (stopped) return
        stopped = true
        runCatching { audio?.stop() }
        runCatching { video.drain(true) } // best-effort EOS so encoder.stop() doesn't block
        runCatching { video.release() }
        runCatching { muxerWrapper.release() }
    }
}

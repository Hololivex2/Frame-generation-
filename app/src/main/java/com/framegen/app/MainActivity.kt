package com.framegen.app.engine

import android.content.res.AssetManager
import android.util.Log
import android.view.Surface

/**
 * FrameGenEngine — optional JNI wrapper for the native C++ engine.
 *
 * The Vulkan debug layer is loaded into the target game's process.  This
 * dashboard must therefore remain usable even if this app's optional JNI
 * library cannot be loaded on a particular device/ROM.
 */
class FrameGenEngine {

    companion object {
        private const val TAG = "FrameGenEngine"

        @Volatile
        private var nativeLoadAttempted = false

        @Volatile
        private var nativeLoaded = false

        /**
         * Do not load libframegen.so while the app/service is merely opening.
         * A missing or incompatible optional native dependency used to crash
         * the whole app immediately when FrameGenService was created.
         */
        private fun ensureNativeLoaded(): Boolean {
            if (nativeLoadAttempted) return nativeLoaded

            synchronized(FrameGenEngine::class.java) {
                if (!nativeLoadAttempted) {
                    nativeLoadAttempted = true
                    nativeLoaded = try {
                        System.loadLibrary("framegen")
                        true
                    } catch (error: Throwable) {
                        Log.e(TAG, "Optional native engine is unavailable; continuing with Vulkan-layer mode", error)
                        false
                    }
                }
            }

            return nativeLoaded
        }

        const val MODE_OFF = 0
        const val MODE_60FPS = 1
        const val MODE_90FPS = 2
        const val MODE_120FPS = 3
    }

    data class Stats(
        val captureMs: Float = 0f,
        val motionMs: Float = 0f,
        val interpolationMs: Float = 0f,
        val presentMs: Float = 0f,
        val totalMs: Float = 0f,
        val effectiveFps: Float = 0f,
        val gpuTemp: Float = 0f,
        val framesGenerated: Long = 0,
        val framesDropped: Long = 0
    )

    var isRunning = false
        private set

    /**
     * Initialize the optional in-app engine. Returns false instead of
     * terminating the dashboard when the device cannot load it.
     */
    fun init(
        surface: Surface,
        assetManager: AssetManager,
        mode: Int = MODE_60FPS,
        quality: Float = 0.5f,
        targetFps: Int = 120
    ): Boolean {
        if (!ensureNativeLoaded()) return false

        return try {
            nativeInit(surface, assetManager, mode, quality, targetFps)
        } catch (error: Throwable) {
            Log.e(TAG, "Native engine initialization failed", error)
            false
        }
    }

    fun start() {
        if (!ensureNativeLoaded()) {
            isRunning = false
            return
        }

        try {
            nativeStart()
            isRunning = true
        } catch (error: Throwable) {
            isRunning = false
            Log.e(TAG, "Native engine start failed", error)
        }
    }

    fun stop() {
        if (nativeLoaded) {
            try {
                nativeStop()
            } catch (error: Throwable) {
                Log.w(TAG, "Native engine stop failed", error)
            }
        }
        isRunning = false
    }

    fun destroy() {
        if (nativeLoaded) {
            try {
                nativeStop()
            } catch (error: Throwable) {
                Log.w(TAG, "Native engine stop during destroy failed", error)
            }
            try {
                nativeDestroy()
            } catch (error: Throwable) {
                Log.w(TAG, "Native engine destroy failed", error)
            }
        }
        isRunning = false
    }

    fun setMode(mode: Int) {
        if (!ensureNativeLoaded()) return
        try {
            nativeSetMode(mode)
        } catch (error: Throwable) {
            Log.e(TAG, "Native mode update failed", error)
        }
    }

    fun setQuality(quality: Float) {
        if (!ensureNativeLoaded()) return
        try {
            nativeSetQuality(quality.coerceIn(0f, 1f))
        } catch (error: Throwable) {
            Log.e(TAG, "Native quality update failed", error)
        }
    }

    fun getStats(): Stats {
        if (!ensureNativeLoaded()) return Stats()

        return try {
            val raw = nativeGetStats()
            if (raw == null || raw.size < 9) {
                Stats()
            } else {
                Stats(
                    captureMs = raw[0],
                    motionMs = raw[1],
                    interpolationMs = raw[2],
                    presentMs = raw[3],
                    totalMs = raw[4],
                    effectiveFps = raw[5],
                    gpuTemp = raw[6],
                    framesGenerated = raw[7].toLong(),
                    framesDropped = raw[8].toLong()
                )
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Native stats query failed", error)
            Stats()
        }
    }

    fun getGpuTemperature(): Float {
        if (!ensureNativeLoaded()) return 0f
        return try {
            nativeGetGpuTemp()
        } catch (error: Throwable) {
            Log.e(TAG, "Native temperature query failed", error)
            0f
        }
    }

    fun isThermalThrottled(): Boolean {
        if (!ensureNativeLoaded()) return false
        return try {
            nativeIsThermalThrottled()
        } catch (error: Throwable) {
            Log.e(TAG, "Native thermal query failed", error)
            false
        }
    }

    // ============================================================
    // Native methods
    // ============================================================
    private external fun nativeInit(
        surface: Surface,
        assetManager: AssetManager,
        mode: Int,
        quality: Float,
        targetFps: Int
    ): Boolean

    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeDestroy()
    private external fun nativeSetMode(mode: Int)
    private external fun nativeSetQuality(quality: Float)
    private external fun nativeGetStats(): FloatArray?
    private external fun nativeGetGpuTemp(): Float
    private external fun nativeIsThermalThrottled(): Boolean
}

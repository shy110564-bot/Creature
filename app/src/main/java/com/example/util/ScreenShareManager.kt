package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.service.JarvisVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenShareManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastCaptureTimeMs = 0L

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _latestScreenBitmap = MutableStateFlow<Bitmap?>(null)
    val latestScreenBitmap: StateFlow<Bitmap?> = _latestScreenBitmap.asStateFlow()

    fun createCaptureIntent(): Intent? {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        return mpm?.createScreenCaptureIntent()
    }

    fun startScreenShare(resultCode: Int, data: Intent, onFrameCaptured: (Bitmap) -> Unit) {
        stopScreenShare()

        // Ensure Foreground Service with mediaProjection type is running first
        runCatching {
            val serviceIntent = Intent(context, JarvisVoiceService::class.java).apply {
                putExtra(JarvisVoiceService.EXTRA_STATUS, "🔴 Live Screen Share & Voice Active 💕")
                putExtra(JarvisVoiceService.EXTRA_MEDIA_PROJECTION, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        mainHandler.postDelayed({
            runCatching {
                val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    ?: return@runCatching
                val projection = mpm.getMediaProjection(resultCode, data) ?: return@runCatching
                mediaProjection = projection

                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopScreenShare()
                    }
                }, mainHandler)

                val metrics = context.resources.displayMetrics
                val width = 540
                val height = 960
                val density = metrics.densityDpi

                val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                imageReader = reader

                reader.setOnImageAvailableListener({ ir ->
                    val now = System.currentTimeMillis()
                    val image = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    try {
                        if (now - lastCaptureTimeMs >= 1600L) {
                            lastCaptureTimeMs = now
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width

                            val rawBitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            rawBitmap.copyPixelsFromBuffer(buffer)
                            val cleanBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                            if (rawBitmap != cleanBitmap) {
                                rawBitmap.recycle()
                            }
                            _latestScreenBitmap.value = cleanBitmap
                            onFrameCaptured(cleanBitmap)
                        }
                    } catch (_: Throwable) {
                    } finally {
                        runCatching { image.close() }
                    }
                }, mainHandler)

                virtualDisplay = projection.createVirtualDisplay(
                    "JARVIS_Live_Screen",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    mainHandler
                )
                _isScreenSharing.value = true
            }.onFailure {
                _isScreenSharing.value = false
            }
        }, 250L)
    }

    fun stopScreenShare() {
        _isScreenSharing.value = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }
}

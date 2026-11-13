package com.somnaflow.app.bio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Photoplethysmography with no extra hardware: fingertip over the rear camera with the flash on.
 * Each frame is reduced to its mean brightness; the pulse wave is the tiny periodic change in that value.
 */
object CameraPpg {
    var running by mutableStateOf(false); private set
    var fingerOn by mutableStateOf(false); private set
    var quality by mutableFloatStateOf(0f); private set
    var wave by mutableStateOf(listOf<Float>()); private set
    var error by mutableStateOf(""); private set

    private var thread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var reader: ImageReader? = null
    private var proc = PpgProcessor { Bio.onRr(it) }

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (running) return
        error = ""
        try {
            val mgr = ctx.getSystemService(CameraManager::class.java)
            val ids = mgr.cameraIdList.filter { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            val id = ids.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: ids.firstOrNull()
            if (id == null) { error = "No rear camera found"; return }
            val sizes = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888)
            val size = sizes?.filter { it.width >= 160 }?.minByOrNull { it.width * it.height }
            if (size == null) { error = "Camera format not supported"; return }
            val t = HandlerThread("somnaflow-ppg").also { it.start() }; thread = t
            val h = Handler(t.looper)
            proc = PpgProcessor { Bio.onRr(it) }
            Bio.resetHeart(); Bio.hrSource = "Camera pulse sensor"
            val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2); reader = r
            var frames = 0
            r.setOnImageAvailableListener({ rd ->
                val img = try { rd.acquireLatestImage() } catch (_: Throwable) { null } ?: return@setOnImageAvailableListener
                try {
                    val y = img.planes[0]; val buf = y.buffer; val stride = y.rowStride
                    var sum = 0L; var n = 0
                    var row = img.height / 4
                    while (row < img.height * 3 / 4) {
                        var col = img.width / 4
                        while (col < img.width * 3 / 4) { sum += buf.get(row * stride + col).toInt() and 0xff; n++; col += 4 }
                        row += 4
                    }
                    val v = img.planes[2]; val vb = v.buffer
                    val red = vb.get((img.height / 4) * v.rowStride + (img.width / 4) * v.pixelStride).toInt() and 0xff
                    val mean = sum.toDouble() / n
                    proc.add(img.timestamp / 1e6, mean)
                    if (frames++ % 3 == 0) {
                        fingerOn = red > 150 && mean > 30
                        quality = if (fingerOn) proc.quality else 0f
                        wave = (wave + proc.wave).takeLast(90)
                    }
                } catch (_: Throwable) {} finally { img.close() }
            }, h)
            running = true
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    camera = cam
                    try {
                        val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(r.surface)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        @Suppress("DEPRECATION")
                        cam.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) { try { s.setRepeatingRequest(req.build(), null, h) } catch (_: Throwable) { fail("Camera stream failed") } }
                            override fun onConfigureFailed(s: CameraCaptureSession) = fail("Camera could not start")
                        }, h)
                    } catch (_: Throwable) { fail("Camera could not start") }
                }
                override fun onDisconnected(cam: CameraDevice) = stop()
                override fun onError(cam: CameraDevice, e: Int) = fail("Camera error $e")
            }, h)
        } catch (_: Throwable) { fail("Camera unavailable") }
    }

    private fun fail(msg: String) { error = msg; stop() }

    fun stop() {
        if (running) Bio.saveHeartSession()
        running = false; fingerOn = false
        try { camera?.close() } catch (_: Throwable) {}
        try { reader?.close() } catch (_: Throwable) {}
        try { thread?.quitSafely() } catch (_: Throwable) {}
        camera = null; reader = null; thread = null
        if (Bio.hrSource == "Camera pulse sensor") Bio.hrSource = ""
    }
}

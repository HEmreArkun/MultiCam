package com.multicam.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 410
        private const val VIDEO_WIDTH = 1920
        private const val VIDEO_HEIGHT = 1080
        private const val VIDEO_FPS = 30
        private const val VIDEO_BITRATE = 4_000_000
        private const val AUDIO_BITRATE = 128 * 1024
        private const val AUDIO_SAMPLE_RATE = 44_100
    }

    private lateinit var openGlView: OpenGlView
    private lateinit var tvStatus: TextView
    private lateinit var tvUrlPreview: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnSwitchCamera: Button
    private lateinit var etServer: EditText
    private lateinit var etPath: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var cbAudio: CheckBox

    private var rtmpCamera: RtmpCamera2? = null
    private var previewPrepared = false
    private var surfaceReady = false
    private var streaming = false
    private var isFrontCamera = false

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            showStatus("Sunucuya bağlanılıyor…")
        }

        override fun onConnectionSuccess() {
            showStatus("YAYINDA • Bağlantı kuruldu")
            runOnUiThread {
                btnStartStop.text = "YAYINI DURDUR"
                btnStartStop.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFDC2626.toInt())
                )
            }
        }

        override fun onConnectionFailed(reason: String) {
            showStatus("Bağlantı hatası: $reason")
            runOnUiThread {
                try { rtmpCamera?.stopStream() } catch (_: Exception) { }
                streaming = false
                btnStartStop.text = "YENİDEN DENE"
                btnStartStop.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF16A34A.toInt())
                )
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                setConnectionInputsEnabled(true)
                previewPrepared = false
                openGlView.postDelayed({
                    if (!streaming && surfaceReady && rtmpCamera != null) preparePreview()
                }, 400)
            }
        }

        override fun onDisconnect() {
            showStatus("Yayın bağlantısı kesildi")
        }

        override fun onAuthError() {
            showStatus("Kimlik doğrulama hatası: kullanıcı adı veya şifreyi kontrol et")
        }

        override fun onAuthSuccess() {
            showStatus("Kimlik doğrulama başarılı, yayın hazırlanıyor…")
        }

        override fun onNewBitrate(bitrate: Long) {
            if (streaming) {
                val kbps = bitrate / 1000
                showStatus("YAYINDA • Gönderim: ${kbps} kb/sn")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.statusBarColor = 0xFF0B1220.toInt()
        window.navigationBarColor = 0xFF0B1220.toInt()

        bindViews()
        etServer.addTextChangedListener(SimpleTextWatcher { refreshUrlPreview() })
        etPath.addTextChangedListener(SimpleTextWatcher { refreshUrlPreview() })

        btnSwitchCamera.setOnClickListener { switchCamera() }
        btnStartStop.setOnClickListener {
            if (streaming) stopStreaming() else startStreaming()
        }

        openGlView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady = true
                if (hasCameraPermission()) initializeCamera()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
            }
        })

        refreshUrlPreview()
        ensurePermissions()
    }

    private fun bindViews() {
        openGlView = findViewById(R.id.openGlView)
        tvStatus = findViewById(R.id.tvStatus)
        tvUrlPreview = findViewById(R.id.tvUrlPreview)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        etServer = findViewById(R.id.etServer)
        etPath = findViewById(R.id.etPath)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        cbAudio = findViewById(R.id.cbAudio)
    }

    private fun ensurePermissions() {
        val needed = mutableListOf<String>()
        if (!hasCameraPermission()) needed += Manifest.permission.CAMERA
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else if (surfaceReady) {
            initializeCamera()
        }
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        if (!hasCameraPermission()) {
            showStatus("Kamera izni olmadan uygulama çalışamaz.")
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_LONG).show()
            return
        }

        val audioGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        cbAudio.isChecked = audioGranted
        if (surfaceReady) initializeCamera()
    }

    private fun initializeCamera() {
        if (!surfaceReady || !hasCameraPermission() || rtmpCamera != null) return
        try {
            rtmpCamera = RtmpCamera2(openGlView, connectChecker)
            preparePreview()
        } catch (error: Exception) {
            showStatus("Kamera başlatılamadı: ${error.localizedMessage ?: "bilinmeyen hata"}")
        }
    }

    private fun preparePreview(): Boolean {
        val camera = rtmpCamera ?: return false
        if (!hasCameraPermission()) return false

        return try {
            val rotation = CameraHelper.getCameraOrientation(this)
            val videoReady = camera.prepareVideo(
                VIDEO_WIDTH,
                VIDEO_HEIGHT,
                VIDEO_FPS,
                VIDEO_BITRATE,
                2,
                rotation
            )

            if (!videoReady) {
                previewPrepared = false
                showStatus("Bu cihaz 1080p H.264 kodlamayı başlatamadı. Cihaz uyumluluğu kontrol edilmeli.")
                return false
            }

            val facing = if (isFrontCamera) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK
            camera.startPreview(facing, VIDEO_WIDTH, VIDEO_HEIGHT, rotation)
            previewPrepared = true
            showStatus("Kamera hazır • 1080p / 30 FPS")
            true
        } catch (error: Exception) {
            previewPrepared = false
            showStatus("Önizleme hatası: ${error.localizedMessage ?: "kamera açılamadı"}")
            false
        }
    }

    private fun startStreaming() {
        if (!hasCameraPermission()) {
            ensurePermissions()
            return
        }
        if (rtmpCamera == null) initializeCamera()
        val camera = rtmpCamera ?: run {
            showStatus("Kamera henüz hazır değil.")
            return
        }

        val host = etServer.text.toString().trim()
            .removePrefix("rtmp://")
            .removePrefix("rtmps://")
            .trimEnd('/')
        val path = etPath.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (host.isBlank() || host.contains('/') || host.contains(' ')) {
            etServer.error = "Bilgisayarın yerel IP adresini gir (örn. 192.168.1.20)"
            return
        }
        if (!path.matches(Regex("cam[1-4]"))) {
            etPath.error = "İlk sürümde cam1, cam2, cam3 veya cam4 kullan"
            return
        }
        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Yayın kullanıcı adı ve şifresi gerekli.", Toast.LENGTH_LONG).show()
            return
        }
        if (cbAudio.isChecked && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Mikrofon izni verilmedi. Mikrofon seçeneğini kapat veya izin ver.", Toast.LENGTH_LONG).show()
            return
        }

        if (!previewPrepared && !preparePreview()) return

        if (cbAudio.isChecked) {
            val audioReady = try {
                camera.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true, false, false)
            } catch (_: Exception) {
                false
            }
            if (!audioReady) {
                Toast.makeText(this, "Mikrofon kodlayıcısı açılamadı; ses olmadan yayın başlatılacak.", Toast.LENGTH_LONG).show()
            }
        }

        val encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8.name())
        val encodedPass = URLEncoder.encode(password, StandardCharsets.UTF_8.name())
        val streamUrl = "rtmp://$host:1935/$path?user=$encodedUser&pass=$encodedPass"

        try {
            camera.startStream(streamUrl)
            streaming = true
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            btnStartStop.text = "YAYIN DURDURULUYOR…"
            btnStartStop.isEnabled = true
            btnSwitchCamera.isEnabled = true
            setConnectionInputsEnabled(false)
            showStatus("Yayın başlatılıyor…")
        } catch (error: Exception) {
            streaming = false
            showStatus("Yayın başlatılamadı: ${error.localizedMessage ?: "hata"}")
        }
    }

    private fun stopStreaming() {
        try {
            rtmpCamera?.stopStream()
        } catch (_: Exception) {
            // Continue restoring the UI even if the stream was already disconnected.
        }
        streaming = false
        previewPrepared = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        btnStartStop.text = "YAYINI BAŞLAT"
        btnStartStop.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(0xFF16A34A.toInt())
        )
        setConnectionInputsEnabled(true)
        showStatus("Yayın durduruldu")
        if (surfaceReady && hasCameraPermission()) {
            openGlView.postDelayed({
                if (!streaming && rtmpCamera != null) preparePreview()
            }, 350)
        }
    }


    private fun setConnectionInputsEnabled(enabled: Boolean) {
        etServer.isEnabled = enabled
        etPath.isEnabled = enabled
        etUsername.isEnabled = enabled
        etPassword.isEnabled = enabled
        cbAudio.isEnabled = enabled
    }

    private fun switchCamera() {
        val camera = rtmpCamera ?: return
        try {
            camera.switchCamera()
            isFrontCamera = !isFrontCamera
            showStatus(if (isFrontCamera) "Ön kamera seçildi" else "Arka kamera seçildi")
        } catch (error: Exception) {
            showStatus("Kamera değiştirilemedi: ${error.localizedMessage ?: "desteklenmiyor"}")
        }
    }

    private fun refreshUrlPreview() {
        if (!::tvUrlPreview.isInitialized || !::etServer.isInitialized || !::etPath.isInitialized) return
        val host = etServer.text.toString().trim().ifBlank { "BILGISAYAR_IP" }
        val path = etPath.text.toString().trim().ifBlank { "cam1" }
        tvUrlPreview.text = "RTMP: rtmp://$host:1935/$path"
    }

    private fun showStatus(message: String) {
        runOnUiThread { tvStatus.text = message }
    }

    override fun onDestroy() {
        try {
            if (streaming) rtmpCamera?.stopStream()
            rtmpCamera?.stopPreview()
        } catch (_: Exception) {
            // Activity is being destroyed; resources are released by the library/OS.
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

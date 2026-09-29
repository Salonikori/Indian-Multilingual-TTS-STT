package com.itantra.app.audio

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioCaptureService : Service() {
    private var capture: AudioCapture? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "iTantra microphone", NotificationManager.IMPORTANCE_LOW))
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            capture?.stop(); capture = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("iTantra is listening")
            .setContentText("Speech is processed on this device.")
            .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build()
        if (Build.VERSION.SDK_INT >= 30)
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(ID, notification)
        if (capture == null) capture = AudioCapture().also { it.start() }
        return START_NOT_STICKY
    }
    override fun onDestroy() { capture?.stop(); capture = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_START = "com.itantra.app.audio.START_CAPTURE"
        const val ACTION_STOP = "com.itantra.app.audio.STOP_CAPTURE"
        private const val CHANNEL = "itantra_mic_capture"
        private const val ID = 4101
    }
}

package at.ferdi.earbuds

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Haelt das Widget nur dann aktuell, wenn der Bildschirm an ist (= Widget moeglicherweise sichtbar).
 * Bildschirm aus: keine Abfragen, kein Akkuverbrauch.
 */
class LiveService : Service() {

    companion object {
        private const val INTERVALL_MS = 30_000L
        private const val KANAL_ID = "live"

        fun aktiv(ctx: Context): Boolean =
            ctx.getSharedPreferences(EarbudWidget.PREFS, Context.MODE_PRIVATE).getBoolean("live", true)

        fun starten(ctx: Context) {
            if (!aktiv(ctx) || !EarbudReader.hatErlaubnis(ctx)) return
            try {
                ctx.startForegroundService(Intent(ctx, LiveService::class.java))
            } catch (e: Exception) {
                // Android erlaubt den Start gerade nicht (z. B. aus dem Hintergrund) - Widget laeuft trotzdem ohne Live-Modus
            }
        }

        fun stoppen(ctx: Context) {
            ctx.stopService(Intent(ctx, LiveService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var bildschirmAn = false

    private val schleife = object : Runnable {
        override fun run() {
            if (!bildschirmAn) return
            if (!EarbudWidget.gibtWidgets(this@LiveService)) {
                stopSelf()
                return
            }
            EarbudWidget.aktualisieren(this@LiveService, 0, 1) {}
            handler.postDelayed(this, INTERVALL_MS)
        }
    }

    private val empfaenger = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> los()
                Intent.ACTION_SCREEN_OFF -> {
                    bildschirmAn = false
                    handler.removeCallbacks(schleife)
                }
            }
        }
    }

    private fun los() {
        handler.removeCallbacks(schleife)
        bildschirmAn = true
        handler.post(schleife)   // sofort aktualisieren, dann alle 30 s
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAL_ID, "Live-Aktualisierung", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, KANAL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Ohrhörer-Akku")
            .setContentText("Aktualisiert, solange der Bildschirm an ist")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else
                startForeground(1, n)
        } catch (e: Exception) {
            stopSelf()
            return
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(empfaenger, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(empfaenger, f)
        if (getSystemService(PowerManager::class.java).isInteractive) los()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(schleife)
        try { unregisterReceiver(empfaenger) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package at.ferdi.earbuds

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.widget.RemoteViews

class EarbudWidget : AppWidgetProvider() {

    companion object {
        const val AKTION_AKTUALISIEREN = "at.ferdi.earbuds.REFRESH"
        private const val PREFS = "earbuds"

        fun anfordern(ctx: Context) {
            ctx.sendBroadcast(Intent(ctx, EarbudWidget::class.java).setAction(AKTION_AKTUALISIEREN))
        }

        private fun merken(ctx: Context, w: Array<Int?>?) {
            val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (w == null) {
                e.remove("l").remove("r").remove("c")
            } else {
                e.putInt("l", w[0] ?: -1).putInt("r", w[1] ?: -1).putInt("c", w[2] ?: -1)
            }
            e.apply()
        }

        private fun gemerkt(ctx: Context): Array<Int?> {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun holen(k: String): Int? = p.getInt(k, -1).let { if (it < 0) null else it }
            return arrayOf(holen("l"), holen("r"), holen("c"))
        }

        /** Zeichnet alle Widgets neu. */
        fun zeichnen(ctx: Context, werte: Array<Int?>) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, EarbudWidget::class.java))
            if (ids.isEmpty()) return
            val bild = PillRenderer.zeichne(werte)
            val tippen = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, EarbudWidget::class.java).setAction(AKTION_AKTUALISIEREN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget)
                v.setImageViewBitmap(R.id.bild, bild)
                v.setOnClickPendingIntent(R.id.bild, tippen)
                mgr.updateAppWidget(id, v)
            }
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Sofort mit dem letzten Stand zeichnen, dann im Hintergrund auffrischen
        zeichnen(ctx, gemerkt(ctx))
        arbeiten(ctx, goAsync(), 0)
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)   // APPWIDGET_UPDATE -> onUpdate
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val geraet: BluetoothDevice? =
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else
                        @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val name = try { geraet?.name } catch (e: SecurityException) { null }
                if (geraet != null && name != null && !name.contains(EarbudReader.NAME, ignoreCase = true)) return
                if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    merken(ctx, null)
                    zeichnen(ctx, arrayOfNulls(3))
                } else {
                    arbeiten(ctx, goAsync(), 2500)   // dem Kanal kurz Zeit geben
                }
            }
            AKTION_AKTUALISIEREN -> arbeiten(ctx, goAsync(), 0)
        }
    }

    /** Liest im Hintergrund (bis zu drei Versuche) und zeichnet das Ergebnis. */
    private fun arbeiten(ctx: Context, pending: BroadcastReceiver.PendingResult, wartenMs: Long) {
        Thread {
            try {
                if (wartenMs > 0) Thread.sleep(wartenMs)
                var ergebnis: EarbudReader.Ergebnis = EarbudReader.Ergebnis.Unklar
                for (versuch in 0 until 3) {
                    ergebnis = EarbudReader.lesen(ctx)
                    if (ergebnis !is EarbudReader.Ergebnis.Unklar) break
                    Thread.sleep(3000)
                }
                when (val e = ergebnis) {
                    is EarbudReader.Ergebnis.Werte -> {
                        val w = arrayOf(e.links, e.rechts, e.case)
                        merken(ctx, w)
                        zeichnen(ctx, w)
                    }
                    is EarbudReader.Ergebnis.NichtVerbunden -> {
                        merken(ctx, null)
                        zeichnen(ctx, arrayOfNulls(3))
                    }
                    else -> zeichnen(ctx, gemerkt(ctx))   // verbunden, aber gerade nicht lesbar: letzter Stand
                }
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

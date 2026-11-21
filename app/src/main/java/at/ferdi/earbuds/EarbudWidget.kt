package at.ferdi.earbuds

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews
import java.util.concurrent.atomic.AtomicBoolean

class EarbudWidget : AppWidgetProvider() {

    companion object {
        const val AKTION_AKTUALISIEREN = "at.ferdi.earbuds.REFRESH"
        const val PREFS = "earbuds"
        private val laeuft = AtomicBoolean(false)

        fun anfordern(ctx: Context) {
            ctx.sendBroadcast(Intent(ctx, EarbudWidget::class.java).setAction(AKTION_AKTUALISIEREN))
        }

        private fun merken(ctx: Context, w: Array<Int?>?, laden: BooleanArray?) {
            val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (w == null) {
                e.remove("l").remove("r").remove("c").remove("ll").remove("lr").remove("lc")
            } else {
                e.putInt("l", w[0] ?: -1).putInt("r", w[1] ?: -1).putInt("c", w[2] ?: -1)
                val la = laden ?: BooleanArray(3)
                e.putBoolean("ll", la[0]).putBoolean("lr", la[1]).putBoolean("lc", la[2])
            }
            e.apply()
        }

        private fun gemerkt(ctx: Context): Pair<Array<Int?>, BooleanArray> {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun holen(k: String): Int? = p.getInt(k, -1).let { if (it < 0) null else it }
            return Pair(
                arrayOf(holen("l"), holen("r"), holen("c")),
                booleanArrayOf(p.getBoolean("ll", false), p.getBoolean("lr", false), p.getBoolean("lc", false))
            )
        }

        /** Zeichnet alle Widgets mit dem letzten bekannten Stand neu (z. B. nach Aenderung der Einstellungen). */
        fun neuZeichnen(ctx: Context) {
            val (w, l) = gemerkt(ctx)
            zeichnen(ctx, w, l)
        }

        fun vorschau(ctx: Context) = PillRenderer.zeichne(gemerkt(ctx).first, gemerkt(ctx).second, PillRenderer.hintergrund(ctx))

        /** Exakte Pixelgroesse eines Widgets (damit das Bild nicht skaliert werden muss und scharf bleibt). */
        private fun groesse(ctx: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
            val o = mgr.getAppWidgetOptions(id)
            val hoch = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            var wDp = o.getInt(if (hoch) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            var hDp = o.getInt(if (hoch) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            if (wDp <= 0) wDp = 280
            if (hDp <= 0) hDp = 60
            val d = ctx.resources.displayMetrics.density
            var w = (wDp * d).toInt().coerceAtLeast(200)
            var h = (hDp * d).toInt().coerceAtLeast(48)
            val max = 1400   // Bildspeicher-Grenze von RemoteViews im Auge behalten
            if (w > max) { h = h * max / w; w = max }
            return Pair(w, h)
        }

        /** Zeichnet alle Widgets neu. glanz >= 0 = Lichtstreifen der Aktualisierungs-Animation. */
        fun zeichnen(ctx: Context, werte3: Array<Int?>, laden3: BooleanArray, glanz: Float = -1f, teil: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, EarbudWidget::class.java))
            if (ids.isEmpty()) return
            val hg = PillRenderer.hintergrund(ctx)
            var werte = werte3
            var laden = laden3
            if (Sync.aktiv(ctx)) {          // vierte Gruppe: Laptop-Akku
                val lp = Sync.laptop(ctx)
                werte = werte3 + lp?.first
                laden = laden3 + (lp?.second ?: false)
            }
            val tippen = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, EarbudWidget::class.java).setAction(AKTION_AKTUALISIEREN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in ids) {
                val (w, h) = groesse(ctx, mgr, id)
                val v = RemoteViews(ctx.packageName, R.layout.widget)
                v.setImageViewBitmap(R.id.bild, PillRenderer.zeichne(werte, laden, hg, w, h, glanz))
                if (teil || glanz >= 0f) {
                    mgr.partiallyUpdateAppWidget(id, v)
                } else {
                    v.setOnClickPendingIntent(R.id.bild, tippen)
                    mgr.updateAppWidget(id, v)
                }
            }
        }

        fun gibtWidgets(ctx: Context): Boolean =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, EarbudWidget::class.java)).isNotEmpty()

        /** Zaehlt die Zahlen weich von 0 (bzw. vom alten Stand) auf den neuen Stand - wie beim PC. */
        private fun zaehlen(ctx: Context, alt: Array<Int?>, neu: Array<Int?>, laden: BooleanArray) {
            val schritte = 14
            for (i in 1..schritte) {
                val t = i / schritte.toFloat()
                val e = 1f - (1f - t) * (1f - t) * (1f - t)
                val zw = Array<Int?>(3) { k ->
                    val a0 = alt[k]
                    val n0 = neu[k]
                    if (a0 != null && n0 != null) (a0 + (n0 - a0) * e).toInt() else n0
                }
                zeichnen(ctx, zw, laden, -1f, true)
                if (i < schritte) Thread.sleep(40)
            }
            zeichnen(ctx, neu, laden)   // Endbild inkl. Tipp-Aktion
        }

        private fun nullen(w: Array<Int?>): Array<Int?> = Array(3) { if (w[it] != null) 0 else null }

        /** Nur die Animation (wenn gerade schon gelesen wird und das Tippen sonst nichts bewirken wuerde). */
        private fun nurAnimation(ctx: Context) {
            Thread {
                try {
                    val (w, l) = gemerkt(ctx)
                    var t = 0f
                    val ende = System.currentTimeMillis() + 800
                    while (System.currentTimeMillis() < ende) {
                        zeichnen(ctx, w, l, t)
                        t += 0.07f
                        if (t > 1f) t = 0f
                        Thread.sleep(70)
                    }
                    zaehlen(ctx, nullen(w), w, l)
                } catch (e: Exception) {
                }
            }.start()
        }

        /**
         * Liest im Hintergrund (mehrere Versuche) und zeichnet das Ergebnis. Doppelte Aufrufe werden uebersprungen.
         * animiert = Lichtstreifen waehrend des Lesens, danach zaehlen die Zahlen auf den neuen Stand.
         */
        fun aktualisieren(ctx: Context, wartenMs: Long, versuche: Int, animiert: Boolean = false, fertig: () -> Unit) {
            if (!laeuft.compareAndSet(false, true)) {
                if (animiert) nurAnimation(ctx)
                fertig()
                return
            }
            Thread {
                val glanzAn = AtomicBoolean(animiert)
                var glanzThread: Thread? = null
                try {
                    val alt = gemerkt(ctx)
                    val beginn = System.currentTimeMillis()
                    if (animiert) {
                        glanzThread = Thread {
                            var t = 0f
                            try {
                                while (glanzAn.get()) {
                                    zeichnen(ctx, alt.first, alt.second, t)
                                    t += 0.06f
                                    if (t > 1f) t = 0f
                                    Thread.sleep(70)
                                }
                            } catch (e: Exception) {
                            }
                        }
                        glanzThread.start()
                    }
                    if (wartenMs > 0) Thread.sleep(wartenMs)
                    Sync.abgleich(ctx)   // Handy-Akku senden, Laptop-Akku holen (nur wenn Sync-Code gesetzt)
                    var ergebnis: EarbudReader.Ergebnis = EarbudReader.Ergebnis.Unklar
                    for (versuch in 0 until versuche) {
                        ergebnis = EarbudReader.lesen(ctx)
                        if (ergebnis !is EarbudReader.Ergebnis.Unklar) break
                        if (versuch < versuche - 1) Thread.sleep(3000)
                    }
                    if (animiert) {
                        val rest = 900 - (System.currentTimeMillis() - beginn)
                        if (rest > 0) Thread.sleep(rest)    // mindestens kurz sichtbar
                    }
                    glanzAn.set(false)
                    glanzThread?.join(800)
                    when (val e = ergebnis) {
                        is EarbudReader.Ergebnis.Werte -> {
                            val w = arrayOf(e.links, e.rechts, e.case)
                            merken(ctx, w, e.laden)
                            if (animiert) zaehlen(ctx, nullen(w), w, e.laden) else zeichnen(ctx, w, e.laden)
                        }
                        is EarbudReader.Ergebnis.NichtVerbunden -> {
                            merken(ctx, null, null)
                            zeichnen(ctx, arrayOfNulls(3), BooleanArray(3))
                        }
                        else -> if (animiert) zaehlen(ctx, nullen(alt.first), alt.first, alt.second) else neuZeichnen(ctx)   // nicht lesbar: letzter Stand
                    }
                } catch (e: Exception) {
                } finally {
                    glanzAn.set(false)
                    laeuft.set(false)
                    fertig()
                }
            }.start()
        }
    }

    override fun onEnabled(ctx: Context) {
        LiveService.starten(ctx)
    }

    override fun onDisabled(ctx: Context) {
        LiveService.stoppen(ctx)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // Sofort mit dem letzten Stand zeichnen, dann im Hintergrund auffrischen
        neuZeichnen(ctx)
        val p = goAsync()
        aktualisieren(ctx, 0, 3, false) { p.finish() }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, neu: Bundle) {
        neuZeichnen(ctx)   // Groesse geaendert -> in der neuen Groesse scharf zeichnen
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)   // APPWIDGET_UPDATE -> onUpdate
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> LiveService.starten(ctx)
            BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val geraet: BluetoothDevice? =
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else
                        @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val name = try { geraet?.name } catch (e: SecurityException) { null }
                if (geraet != null && name != null && !name.contains(EarbudReader.NAME, ignoreCase = true)) return
                if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    merken(ctx, null, null)
                    zeichnen(ctx, arrayOfNulls(3), BooleanArray(3))
                } else {
                    val p = goAsync()
                    aktualisieren(ctx, 2500, 3, false) { p.finish() }   // dem Kanal kurz Zeit geben
                }
            }
            AKTION_AKTUALISIEREN -> {
                val p = goAsync()
                aktualisieren(ctx, 0, 2, true) { p.finish() }   // Tippen: mit Animation
            }
        }
    }
}

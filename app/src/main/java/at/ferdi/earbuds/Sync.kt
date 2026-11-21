package at.ferdi.earbuds

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tauscht den Akkustand mit dem PC aus ueber ntfy.sh (kostenlos, ohne Konto). Beide Seiten nutzen denselben Sync-Code.
 * Handy sendet {"d":"handy","p":Prozent,"l":ladend}, der PC {"d":"yoga",...}. Nur wenige, kleine Anfragen.
 */
object Sync {
    private const val PC_ALTER_MAX_S = 45 * 60

    fun code(ctx: Context): String =
        ctx.getSharedPreferences(EarbudWidget.PREFS, Context.MODE_PRIVATE).getString("sync_code", "")!!.trim()

    fun aktiv(ctx: Context) = code(ctx).length >= 8

    /** Gespeicherter Laptop-Stand: Prozent, laedt; null = unbekannt/veraltet. */
    fun laptop(ctx: Context): Pair<Int, Boolean>? {
        val sp = ctx.getSharedPreferences(EarbudWidget.PREFS, Context.MODE_PRIVATE)
        val p = sp.getInt("lp", -1)
        val t = sp.getLong("lt", 0L)
        if (p < 0 || System.currentTimeMillis() / 1000 - t > PC_ALTER_MAX_S) return null
        return Pair(p, sp.getBoolean("ll", false))
    }

    private fun handyAkku(ctx: Context): Pair<Int, Boolean>? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l < 0 || s <= 0) return null
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val laedt = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL &&
            i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return Pair(l * 100 / s, laedt)
    }

    /** Sendet den Handy-Akku (bei Aenderung oder alle 8 min) und holt den Laptop-Akku. Im Hintergrund-Thread aufrufen. */
    fun abgleich(ctx: Context) {
        if (!aktiv(ctx)) return
        val sp = ctx.getSharedPreferences(EarbudWidget.PREFS, Context.MODE_PRIVATE)
        val c = code(ctx)
        try {
            val h = handyAkku(ctx)
            if (h != null) {
                val jetzt = System.currentTimeMillis()
                val geaendert = sp.getInt("sp", -1) != h.first || sp.getBoolean("sl", false) != h.second
                if (geaendert || jetzt - sp.getLong("st", 0L) > 8 * 60_000L) {
                    val body = JSONObject().put("d", "handy").put("p", h.first).put("l", h.second).toString()
                    val v = URL("https://ntfy.sh/$c").openConnection() as HttpURLConnection
                    v.connectTimeout = 4000
                    v.readTimeout = 4000
                    v.requestMethod = "POST"
                    v.doOutput = true
                    v.outputStream.use { it.write(body.toByteArray()) }
                    v.inputStream.close()
                    v.disconnect()
                    sp.edit().putInt("sp", h.first).putBoolean("sl", h.second).putLong("st", jetzt).apply()
                }
            }
        } catch (e: Exception) {
        }
        try {
            val v = URL("https://ntfy.sh/$c/json?poll=1&since=12h").openConnection() as HttpURLConnection
            v.connectTimeout = 4000
            v.readTimeout = 4000
            val text = v.inputStream.bufferedReader().use { it.readText() }
            v.disconnect()
            var p = -1
            var l = false
            var t = 0L
            for (zeile in text.lines()) {
                if (zeile.isBlank()) continue
                try {
                    val m = JSONObject(zeile)
                    if (m.optString("event") != "message") continue
                    val d = JSONObject(m.optString("message"))
                    if (d.optString("d") == "yoga" && d.has("p")) {
                        p = d.getInt("p"); l = d.optBoolean("l"); t = m.optLong("time")
                    }
                } catch (e: Exception) {
                }
            }
            sp.edit().putInt("lp", p).putBoolean("ll", l).putLong("lt", t).apply()
        } catch (e: Exception) {
        }
    }
}

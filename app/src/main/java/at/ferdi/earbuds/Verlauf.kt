package at.ferdi.earbuds

import android.content.Context
import java.io.File

/** Speichert den Akkuverlauf (7 Tage) und schaetzt daraus die Restdauer. */
object Verlauf {
    class Probe(val t: Long, val p: Int, val l: Boolean)
    class Schaetzung(val modus: String, val stunden: Double?, val steigung: Double?)

    private val letzte = HashMap<String, Probe>()
    private var geladen = false
    private const val TAGE = 7

    private fun datei(ctx: Context) = File(ctx.filesDir, "verlauf.csv")

    private fun zeilen(ctx: Context): List<Pair<String, Probe>> {
        val f = datei(ctx)
        if (!f.exists()) return emptyList()
        val grenze = System.currentTimeMillis() / 1000 - TAGE * 86400L
        val aus = ArrayList<Pair<String, Probe>>()
        f.forEachLine { z ->
            val t = z.split(",")
            if (t.size == 4) {
                val ts = t[0].toLongOrNull()
                val p = t[2].toIntOrNull()
                if (ts != null && p != null && ts >= grenze) aus.add(Pair(t[1], Probe(ts, p, t[3] == "1")))
            }
        }
        return aus
    }

    private fun init(ctx: Context) {
        if (geladen) return
        for ((n, pr) in zeilen(ctx)) {
            val alt = letzte[n]
            if (alt == null || pr.t >= alt.t) letzte[n] = pr
        }
        geladen = true
    }

    /** Haelt einen Messwert fest: bei Aenderung oder spaetestens alle 10 Minuten. zeit = Zeitpunkt der Messung (Sekunden). */
    @Synchronized
    fun add(ctx: Context, name: String, p: Int?, laedt: Boolean, zeit: Long = System.currentTimeMillis() / 1000) {
        if (p == null) return
        try {
            init(ctx)
            val alt = letzte[name]
            if (alt != null) {
                if (zeit <= alt.t) return
                if (alt.p == p && alt.l == laedt && zeit - alt.t < 600) return
            }
            val f = datei(ctx)
            f.appendText("$zeit,$name,$p,${if (laedt) 1 else 0}\n")
            letzte[name] = Probe(zeit, p, laedt)
            if (f.length() > 300_000) {   // alte Eintraege aussortieren
                val z = zeilen(ctx)
                f.writeText(z.joinToString("") { "${it.second.t},${it.first},${it.second.p},${if (it.second.l) 1 else 0}\n" })
            }
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun holen(ctx: Context, name: String, stunden: Int): List<Probe> {
        val grenze = System.currentTimeMillis() / 1000 - stunden * 3600L
        return zeilen(ctx).filter { it.first == name && it.second.t >= grenze }.map { it.second }.sortedBy { it.t }
    }

    /** Wie am PC: letztes zusammenhaengendes Stueck, lineare Regression -> Prozent pro Stunde -> Restdauer. */
    fun schaetze(proben: List<Probe>): Schaetzung {
        val keine = Schaetzung("keine", null, null)
        if (proben.size < 3) return keine
        val letzteP = proben.last()
        var laden = letzteP.l
        if (!laden && proben.size >= 2) {
            val vor = proben[proben.size - 2]
            if (letzteP.p > vor.p && vor.t > letzteP.t - 1800) laden = true
        }
        val seg = ArrayList<Probe>()
        seg.add(letzteP)
        for (i in proben.size - 2 downTo 0) {
            val q = proben[i]
            if (seg[0].t - q.t > 3 * 3600 || letzteP.t - q.t > 6 * 3600) break
            if (laden) {
                if (q.p > seg[0].p + 1) break
            } else {
                if (q.l || q.p < seg[0].p - 1) break
            }
            seg.add(0, q)
        }
        if (seg.size < 3 || seg.last().t - seg.first().t < 600) return keine
        val n = seg.size
        val mt = seg.sumOf { it.t.toDouble() } / n
        val mp = seg.sumOf { it.p.toDouble() } / n
        val v = seg.sumOf { (it.t - mt) * (it.t - mt) }
        if (v <= 0.0) return keine
        val steigung = seg.sumOf { (it.t - mt) * (it.p - mp) } / v * 3600.0
        if (laden) {
            if (steigung < 0.3) return keine
            return Schaetzung("laden", maxOf(0.0, (100 - letzteP.p) / steigung), steigung)
        }
        if (steigung > -0.1) return keine
        return Schaetzung("entladen", maxOf(0.0, letzteP.p / -steigung), steigung)
    }

    fun dauerText(h: Double?): String {
        if (h == null) return ""
        if (h > 100) return "mehr als 100 Stunden"
        val min = Math.round(h * 60).toInt()
        return when {
            min < 60 -> "$min min"
            min < 48 * 60 -> "${min / 60} h ${"%02d".format(min % 60)} min"
            else -> "%.1f Tage".format(h / 24)
        }
    }
}

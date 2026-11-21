package at.ferdi.earbuds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface

/** Zeichnet die Pille: linker Earbud, rechter Earbud, Case - je Symbol + Prozent, Blitz beim Laden. */
object PillRenderer {
    private const val BREITE = 170f   // Einheiten, wie bei der PC-Anzeige
    private const val HOEHE = 36f
    private const val SCALE = 8f      // Pixel pro Einheit (scharf, auch auf dem S24 mit hoher Dichte)

    private val GRAU = Color.rgb(140, 140, 140)

    const val STANDARD_FARBE = 0x202020
    const val STANDARD_DECKKRAFT = 100   // Prozent (100 = nicht durchsichtig)

    // Farbverlauf: bis 10 % rot, bis 50 % orange, ab 90 % gruen, dazwischen weich
    private val STUFEN = arrayOf(
        intArrayOf(10, 255, 69, 58),
        intArrayOf(50, 255, 159, 10),
        intArrayOf(90, 46, 204, 113)
    )

    fun farbe(p: Int?): Int {
        if (p == null) return GRAU
        if (p <= STUFEN.first()[0]) return Color.rgb(STUFEN.first()[1], STUFEN.first()[2], STUFEN.first()[3])
        if (p >= STUFEN.last()[0]) return Color.rgb(STUFEN.last()[1], STUFEN.last()[2], STUFEN.last()[3])
        for (i in 0 until STUFEN.size - 1) {
            val a = STUFEN[i]
            val b = STUFEN[i + 1]
            if (p >= a[0] && p <= b[0]) {
                val t = (p - a[0]).toFloat() / (b[0] - a[0])
                return Color.rgb(
                    (a[1] + (b[1] - a[1]) * t).toInt(),
                    (a[2] + (b[2] - a[2]) * t).toInt(),
                    (a[3] + (b[3] - a[3]) * t).toInt()
                )
            }
        }
        return GRAU
    }

    /** Hintergrundfarbe und Deckkraft aus den Einstellungen (ARGB). */
    fun hintergrund(ctx: Context): Int {
        val sp = ctx.getSharedPreferences(EarbudWidget.PREFS, Context.MODE_PRIVATE)
        val rgb = sp.getInt("bg_farbe", STANDARD_FARBE) and 0xFFFFFF
        val deck = sp.getInt("bg_deck", STANDARD_DECKKRAFT).coerceIn(0, 100)
        return Color.argb((deck * 255 / 100), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    /**
     * werte = links, rechts, case; null = unbekannt oder nicht verbunden (grau mit Strich).
     * laden = je Geraet, ob es gerade geladen wird (Blitz).
     */
    fun zeichne(werte: Array<Int?>, laden: BooleanArray, hg: Int): Bitmap {
        val w = (BREITE * SCALE).toInt()
        val h = (HOEHE * SCALE).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = hg
        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, p)

        // Schriftfarbe je nach Helligkeit des Hintergrunds; bei viel Durchsicht bleibt es hell
        val lum = (0.299 * Color.red(hg) + 0.587 * Color.green(hg) + 0.114 * Color.blue(hg)) / 255.0
        val hell = Color.alpha(hg) > 140 && lum > 0.6
        val textFarbe = if (hell) Color.rgb(20, 20, 20) else Color.WHITE
        val blitzFarbe = Color.rgb(20, 20, 20)

        val gw = 44f
        val abstand = 6f
        val start = (BREITE - (3 * gw + 2 * abstand)) / 2f
        val u = SCALE * 0.78f
        val py = h / 2f

        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = textFarbe
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textSize = 13.5f * SCALE
        val fm = text.fontMetrics
        val basis = py - (fm.ascent + fm.descent) / 2f

        for (i in 0 until 3) {
            val wert = werte.getOrNull(i)
            val ladend = wert != null && laden.getOrNull(i) == true
            val gx = start + i * (gw + abstand)
            val px = (gx + 5f) * SCALE
            p.style = Paint.Style.FILL
            p.color = farbe(wert)
            when (i) {
                0 -> ohr(c, p, px, py, u, -1)
                1 -> ohr(c, p, px, py, u, 1)
                else -> etui(c, p, px, py, u, hg)
            }
            if (ladend) blitz(c, px, py, u, blitzFarbe)
            val beschriftung = if (wert == null) "-" else "$wert%"
            c.drawText(beschriftung, (gx + 13f) * SCALE, basis, text)
        }
        return bmp
    }

    private fun ohr(c: Canvas, p: Paint, px: Float, py: Float, u: Float, aussen: Int) {
        c.drawOval(RectF(px - 5 * u, py - 9 * u, px + 5 * u, py + 2 * u), p)
        val sx = px + aussen * 3.4f * u
        c.drawRoundRect(RectF(sx - 1.9f * u, py - 2 * u, sx + 1.9f * u, py + 9 * u), 1.9f * u, 1.9f * u, p)
    }

    private fun etui(c: Canvas, p: Paint, px: Float, py: Float, u: Float, hg: Int) {
        c.drawRoundRect(RectF(px - 6.5f * u, py - 6 * u, px + 6.5f * u, py + 7 * u), 3.5f * u, 3.5f * u, p)
        // Strich mit exakt dem Hintergrund (inkl. Durchsicht) ueberschreiben
        val strich = Paint(Paint.ANTI_ALIAS_FLAG)
        strich.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        strich.color = hg
        strich.strokeWidth = 1.3f * u
        c.drawLine(px - 6.5f * u, py - 1.5f * u, px + 6.5f * u, py - 1.5f * u, strich)
    }

    /** Gelber Blitz mit dunklem Rand unten rechts am Symbol. */
    private fun blitz(c: Canvas, px: Float, py: Float, u: Float, rand: Int) {
        val xs = floatArrayOf(2.0f, -4.6f, -0.4f, -2.0f, 4.8f, 0.6f)
        val ys = floatArrayOf(-10f, 1.4f, 1.4f, 10f, -1.8f, -1.8f)
        val k = 0.72f
        val ox = px + 4.2f * u
        val oy = py + 2.4f * u
        fun pfad(fx: Float, fy: Float): Path {
            val b = Path()
            for (i in xs.indices) {
                val x = ox + xs[i] * fx * k * u
                val y = oy + ys[i] * fy * k * u
                if (i == 0) b.moveTo(x, y) else b.lineTo(x, y)
            }
            b.close()
            return b
        }
        val pf = Paint(Paint.ANTI_ALIAS_FLAG)
        pf.style = Paint.Style.FILL
        pf.color = rand
        c.drawPath(pfad(1.3f, 1.2f), pf)
        pf.color = Color.rgb(255, 224, 40)
        c.drawPath(pfad(1f, 1f), pf)
    }
}

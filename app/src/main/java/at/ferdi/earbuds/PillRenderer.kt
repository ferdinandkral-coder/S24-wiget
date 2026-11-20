package at.ferdi.earbuds

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/** Zeichnet die Pille: linker Earbud, rechter Earbud, Case - je Symbol + Prozent, in der Farbe des Stands. */
object PillRenderer {
    private const val BREITE = 170f   // Einheiten, wie bei der PC-Anzeige
    private const val HOEHE = 36f
    private const val SCALE = 4f      // Pixel pro Einheit

    private val HINTERGRUND = Color.rgb(32, 32, 32)
    private val GRAU = Color.rgb(140, 140, 140)

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

    /** werte = links, rechts, case; null = unbekannt oder nicht verbunden (grau mit Strich). */
    fun zeichne(werte: Array<Int?>): Bitmap {
        val w = (BREITE * SCALE).toInt()
        val h = (HOEHE * SCALE).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = HINTERGRUND
        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2f, h / 2f, p)

        val gw = 44f
        val abstand = 6f
        val start = (BREITE - (3 * gw + 2 * abstand)) / 2f
        val u = SCALE * 0.78f
        val py = h / 2f

        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = Color.WHITE
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textSize = 13.5f * SCALE
        val fm = text.fontMetrics
        val basis = py - (fm.ascent + fm.descent) / 2f

        for (i in 0 until 3) {
            val wert = werte.getOrNull(i)
            val gx = start + i * (gw + abstand)
            val px = (gx + 5f) * SCALE
            p.style = Paint.Style.FILL
            p.color = farbe(wert)
            when (i) {
                0 -> ohr(c, p, px, py, u, -1)
                1 -> ohr(c, p, px, py, u, 1)
                else -> etui(c, p, px, py, u)
            }
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

    private fun etui(c: Canvas, p: Paint, px: Float, py: Float, u: Float) {
        c.drawRoundRect(RectF(px - 6.5f * u, py - 6 * u, px + 6.5f * u, py + 7 * u), 3.5f * u, 3.5f * u, p)
        val strich = Paint(Paint.ANTI_ALIAS_FLAG)
        strich.color = HINTERGRUND
        strich.strokeWidth = 1.3f * u
        c.drawLine(px - 6.5f * u, py - 1.5f * u, px + 6.5f * u, py - 1.5f * u, strich)
    }
}

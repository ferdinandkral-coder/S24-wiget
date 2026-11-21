package at.ferdi.earbuds

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path as GPath
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface

/** Zeichnet die Pille: linker Earbud, rechter Earbud, Case - je Symbol + Prozent, Blitz beim Laden. */
object PillRenderer {
    private const val BREITE3 = 170f  // Einheiten, wie bei der PC-Anzeige (3 Gruppen)
    private const val BREITE4 = 220f  // mit Laptop (4 Gruppen)
    private const val HOEHE = 36f

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

    /** Vorschau in fester Groesse. */
    fun zeichne(werte: Array<Int?>, laden: BooleanArray, hg: Int): Bitmap =
        zeichne(werte, laden, hg, 1360, 288, -1f)

    /**
     * werte = links, rechts, case; null = unbekannt oder nicht verbunden (grau mit Strich).
     * laden = je Geraet, ob es gerade geladen wird (Blitz).
     * bw/bh = exakte Bildgroesse in Pixeln (Widget-Groesse), damit nichts skaliert werden muss -> scharf.
     * glanz = Position (0..1) eines Lichtstreifens waehrend des Aktualisierens, -1 = aus.
     */
    fun zeichne(werte: Array<Int?>, laden: BooleanArray, hg: Int, bw: Int, bh: Int, glanz: Float): Bitmap {
        val n = if (werte.size >= 4) 4 else 3
        val BREITE = if (n == 4) BREITE4 else BREITE3
        val sc = minOf(bw / BREITE, bh / HOEHE)          // Pixel pro Einheit
        val w = BREITE * sc
        val h = HOEHE * sc
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate((bw - w) / 2f, (bh - h) / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.isDither = true

        p.color = hg
        c.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, p)

        // Schriftfarbe je nach Helligkeit des Hintergrunds; bei viel Durchsicht bleibt es hell
        val lum = (0.299 * Color.red(hg) + 0.587 * Color.green(hg) + 0.114 * Color.blue(hg)) / 255.0
        val hell = Color.alpha(hg) > 140 && lum > 0.6
        val textFarbe = if (hell) Color.rgb(20, 20, 20) else Color.WHITE
        val blitzRand = Color.rgb(20, 20, 20)

        val gw = 44f
        val abstand = 6f
        val start = (BREITE - (n * gw + (n - 1) * abstand)) / 2f
        val u = sc * 0.78f
        val py = h / 2f

        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = textFarbe
        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.textSize = 13.5f * sc
        val fm = text.fontMetrics
        val basis = py - (fm.ascent + fm.descent) / 2f

        for (i in 0 until n) {
            val wert = werte.getOrNull(i)
            val ladend = wert != null && laden.getOrNull(i) == true
            val gx = start + i * (gw + abstand)
            val px = (gx + 5f) * sc
            p.style = Paint.Style.FILL
            p.color = farbe(wert)
            when (i) {
                0 -> ohr(c, p, px, py, u, -1)
                1 -> ohr(c, p, px, py, u, 1)
                2 -> etui(c, p, px, py, u, hg)
                else -> laptop(c, px, py, u, p.color)
            }
            if (ladend) blitz(c, px, py, u, blitzRand)
            val beschriftung = if (wert == null) "-" else "$wert%"
            c.drawText(beschriftung, (gx + 13f) * sc, basis, text)
        }

        if (glanz >= 0f) {
            val x0 = (glanz * (BREITE + 70f) - 50f) * sc
            val band = Paint(Paint.ANTI_ALIAS_FLAG)
            band.shader = LinearGradient(
                x0, 0f, x0 + 50f * sc, 0f,
                intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(90, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null, Shader.TileMode.CLAMP
            )
            val rund = GPath()
            rund.addRoundRect(RectF(0f, 0f, w, h), h / 2f, h / 2f, GPath.Direction.CW)
            c.save()
            c.clipPath(rund)
            c.drawRect(0f, 0f, w, h, band)
            c.restore()
        }
        return bmp
    }

    private fun ohr(c: Canvas, p: Paint, px: Float, py: Float, u: Float, aussen: Int) {
        c.drawOval(RectF(px - 5 * u, py - 9 * u, px + 5 * u, py + 2 * u), p)
        val sx = px + aussen * 3.4f * u
        c.drawRoundRect(RectF(sx - 1.9f * u, py - 2 * u, sx + 1.9f * u, py + 9 * u), 1.9f * u, 1.9f * u, p)
    }

    private fun laptop(c: Canvas, px: Float, py: Float, u: Float, farbe: Int) {
        val l = Paint(Paint.ANTI_ALIAS_FLAG)
        l.color = farbe
        l.style = Paint.Style.STROKE
        l.strokeWidth = 1.7f * u
        c.drawRoundRect(RectF(px - 6.5f * u, py - 6.5f * u, px + 6.5f * u, py + 3.8f * u), 1.4f * u, 1.4f * u, l)
        l.style = Paint.Style.FILL
        c.drawRoundRect(RectF(px - 9f * u, py + 5.2f * u, px + 9f * u, py + 7.2f * u), u, u, l)
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

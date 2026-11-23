package at.ferdi.earbuds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Liniendiagramm: Akkuverlauf mit Flaeche darunter, gestrichelte Prognose bis 0 % bzw. 100 %. */
class DiagrammView(ctx: Context) : View(ctx) {
    var proben: List<Verlauf.Probe> = emptyList()
    var stunden = 24
    var schaetzung: Verlauf.Schaetzung = Verlauf.Schaetzung("keine", null, null)

    private val d = resources.displayMetrics.density
    private val gitter = Paint().apply { color = Color.rgb(44, 44, 48); strokeWidth = d }
    private val jetztLinie = Paint().apply {
        color = Color.rgb(80, 80, 86); strokeWidth = d; pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 120, 128); textSize = 11 * d }
    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f * d; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val punkt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prognose = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.4f * d; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(7 * d, 6 * d), 0f)
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (230 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat()
        val H = height.toFloat()
        val l = 34 * d
        val r = W - 6 * d
        val o = 8 * d
        val u = H - 24 * d
        val jetzt = System.currentTimeMillis() / 1000.0
        val start = jetzt - stunden * 3600.0
        val rest = schaetzung.stunden
        val vor = if (schaetzung.modus != "keine" && rest != null) minOf(rest, stunden * 0.5) else 0.0
        val ende = jetzt + vor * 3600
        fun x(t: Double) = (l + (t - start) / (ende - start) * (r - l)).toFloat()
        fun y(p: Double) = (u - p / 100.0 * (u - o)).toFloat()

        text.textAlign = Paint.Align.RIGHT
        for (p in intArrayOf(0, 50, 100)) {
            c.drawLine(l, y(p.toDouble()), r, y(p.toDouble()), gitter)
            c.drawText("$p", l - 8 * d, y(p.toDouble()) + 4 * d, text)
        }
        val fmt = SimpleDateFormat(if (stunden <= 24) "HH:mm" else "EE", Locale.GERMANY)
        for (i in 0..3) {
            val t = start + (ende - start) * i / 3
            text.textAlign = when (i) { 0 -> Paint.Align.LEFT; 3 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            c.drawText(fmt.format(Date((t * 1000).toLong())), x(t), H - 4 * d, text)
        }
        c.drawLine(x(jetzt), o, x(jetzt), u, jetztLinie)

        val sichtbar = proben.filter { it.t >= start }
        val farbe = PillRenderer.farbe(sichtbar.lastOrNull()?.p)
        if (sichtbar.isEmpty()) {
            text.textAlign = Paint.Align.CENTER
            text.textSize = 13 * d
            c.drawText("Noch keine Messwerte", (l + r) / 2, (o + u) / 2, text)
            text.textSize = 11 * d
            return
        }
        val pf = Path()
        sichtbar.forEachIndexed { i, q ->
            val px = x(q.t.toDouble()); val py = y(q.p.toDouble())
            if (i == 0) pf.moveTo(px, py) else pf.lineTo(px, py)
        }
        val fl = Path(pf)
        fl.lineTo(x(sichtbar.last().t.toDouble()), u)
        fl.lineTo(x(sichtbar.first().t.toDouble()), u)
        fl.close()
        flaeche.shader = LinearGradient(0f, o, 0f, u,
            Color.argb(110, Color.red(farbe), Color.green(farbe), Color.blue(farbe)),
            Color.argb(0, Color.red(farbe), Color.green(farbe), Color.blue(farbe)), Shader.TileMode.CLAMP)
        c.drawPath(fl, flaeche)
        linie.color = farbe
        c.drawPath(pf, linie)

        val letzte = sichtbar.last()
        if (vor > 0.0 && schaetzung.steigung != null) {
            val pk = Path()
            for (i in 0..40) {
                val h = vor * i / 40
                val px = x(letzte.t + h * 3600)
                val py = y(Verlauf.prognose(schaetzung.modus, letzte.p.toDouble(), schaetzung.steigung!!, h))
                if (i == 0) pk.moveTo(px, py) else pk.lineTo(px, py)
            }
            prognose.color = Color.argb(200, Color.red(farbe), Color.green(farbe), Color.blue(farbe))
            c.drawPath(pk, prognose)
        }
        punkt.color = Color.rgb(28, 28, 31)
        c.drawCircle(x(letzte.t.toDouble()), y(letzte.p.toDouble()), 6.5f * d, punkt)
        punkt.color = farbe
        c.drawCircle(x(letzte.t.toDouble()), y(letzte.p.toDouble()), 4.5f * d, punkt)
    }
}

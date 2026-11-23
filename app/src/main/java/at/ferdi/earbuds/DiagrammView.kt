package at.ferdi.earbuds

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Liniendiagramm des Akkustands mit gestrichelter Prognose bis 0 % (bzw. 100 % beim Laden). */
class DiagrammView(ctx: Context) : View(ctx) {
    var proben: List<Verlauf.Probe> = emptyList()
    var stunden = 24
    var schaetzung: Verlauf.Schaetzung = Verlauf.Schaetzung("keine", null, null)

    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 28, 28) }
    private val gitter = Paint().apply { color = Color.rgb(46, 46, 46); strokeWidth = d }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(125, 131, 138); textSize = 11 * d }
    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(61, 220, 132); strokeWidth = 2.4f * d; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val prognose = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 77); strokeWidth = 2.2f * d; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8 * d, 5 * d), 0f)
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (260 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat()
        val H = height.toFloat()
        c.drawRoundRect(0f, 0f, W, H, 12 * d, 12 * d, bg)
        val l = 40 * d
        val r = W - 14 * d
        val o = 16 * d
        val u = H - 28 * d
        val jetzt = System.currentTimeMillis() / 1000.0
        val start = jetzt - stunden * 3600.0
        val rest = schaetzung.stunden
        val vorschau = if (schaetzung.modus != "keine" && rest != null) minOf(rest, stunden * 0.6) else 0.0
        val ende = jetzt + vorschau * 3600
        fun x(t: Double) = (l + (t - start) / (ende - start) * (r - l)).toFloat()
        fun y(p: Double) = (u - p / 100.0 * (u - o)).toFloat()

        for (p in intArrayOf(0, 25, 50, 75, 100)) {
            c.drawLine(l, y(p.toDouble()), r, y(p.toDouble()), gitter)
            text.textAlign = Paint.Align.RIGHT
            c.drawText("$p%", l - 6 * d, y(p.toDouble()) + 4 * d, text)
        }
        val fmt = SimpleDateFormat(if (stunden <= 24) "HH:mm" else "dd.MM.", Locale.GERMANY)
        text.textAlign = Paint.Align.CENTER
        for (i in 0..3) {
            val t = start + (ende - start) * i / 3
            c.drawText(fmt.format(Date((t * 1000).toLong())), x(t), H - 8 * d, text)
        }
        gitter.color = Color.rgb(58, 58, 58)
        c.drawLine(x(jetzt), o, x(jetzt), u, gitter)
        gitter.color = Color.rgb(46, 46, 46)

        if (proben.size >= 2) {
            val pf = Path()
            proben.forEachIndexed { i, q ->
                if (i == 0) pf.moveTo(x(q.t.toDouble()), y(q.p.toDouble())) else pf.lineTo(x(q.t.toDouble()), y(q.p.toDouble()))
            }
            c.drawPath(pf, linie)
        } else if (proben.size == 1) {
            c.drawCircle(x(proben[0].t.toDouble()), y(proben[0].p.toDouble()), 4 * d, Paint(linie).apply { style = Paint.Style.FILL })
        } else {
            text.textAlign = Paint.Align.CENTER
            c.drawText("Noch keine Messwerte – der Verlauf füllt sich mit der Zeit.", W / 2, H / 2, text)
        }
        if (vorschau > 0.0 && proben.isNotEmpty()) {
            val letzte = proben.last()
            val zielP = if (rest != null && rest > stunden * 0.6)
                (letzte.p + (schaetzung.steigung ?: 0.0) * vorschau).coerceIn(0.0, 100.0)
            else if (schaetzung.modus == "laden") 100.0 else 0.0
            c.drawLine(x(letzte.t.toDouble()), y(letzte.p.toDouble()), x(ende), y(zielP), prognose)
        }
    }
}

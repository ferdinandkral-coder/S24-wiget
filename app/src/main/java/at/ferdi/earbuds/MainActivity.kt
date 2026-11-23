package at.ferdi.earbuds

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    // Farben
    private val HG = Color.rgb(14, 14, 16)
    private val KARTE = Color.rgb(28, 28, 31)
    private val FLAECHE = Color.rgb(40, 40, 44)
    private val TEXT = Color.WHITE
    private val GRAU = Color.rgb(150, 150, 158)
    private val AKZENT = Color.rgb(61, 220, 132)

    private val sp by lazy { getSharedPreferences(EarbudWidget.PREFS, MODE_PRIVATE) }

    private lateinit var kacheln: LinearLayout
    private lateinit var zeitraum: LinearLayout
    private lateinit var restGross: TextView
    private lateinit var restKlein: TextView
    private lateinit var diagramm: DiagrammView
    private lateinit var status: TextView
    private lateinit var syncStatus: TextView
    private lateinit var erlaubnis: TextView
    private lateinit var vorschau: ImageView
    private lateinit var deck: SeekBar
    private lateinit var deckWert: TextView
    private lateinit var farben: LinearLayout

    private var gewaehlt = "handy"
    private var stunden = 24

    private val geraete = listOf(
        "ohr_l" to "Links", "ohr_r" to "Rechts", "case" to "Case", "laptop" to "Laptop", "handy" to "Handy"
    )
    private val langeNamen = mapOf(
        "ohr_l" to "Linker Ohrhörer", "ohr_r" to "Rechter Ohrhörer", "case" to "Ladecase",
        "laptop" to "Laptop", "handy" to "Handy"
    )
    private val palette = intArrayOf(
        0x202020, 0x000000, 0x1E2A3A, 0x2B1E3A, 0x1E3A2B, 0x3A1E1E, 0x5A5A5A, 0xF2F2F2
    )

    // ---------- kleine Bausteine ----------
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun rund(farbe: Int, radiusDp: Int, rand: Int = 0, randFarbe: Int = 0) = GradientDrawable().apply {
        setColor(farbe)
        cornerRadius = dp(radiusDp).toFloat()
        if (rand > 0) setStroke(dp(rand), randFarbe)
    }

    private fun text(t: String, groesse: Float, farbe: Int = TEXT, fett: Boolean = false) = TextView(this).apply {
        text = t
        textSize = groesse
        setTextColor(farbe)
        if (fett) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun karte(titel: String?, inhalt: LinearLayout.() -> Unit): LinearLayout {
        val k = LinearLayout(this)
        k.orientation = LinearLayout.VERTICAL
        k.background = rund(KARTE, 24)
        k.setPadding(dp(18), dp(16), dp(18), dp(18))
        if (titel != null) {
            val t = text(titel.uppercase(), 12f, GRAU, true)
            t.letterSpacing = 0.08f
            t.setPadding(0, 0, 0, dp(12))
            k.addView(t)
        }
        k.inhalt()
        return k
    }

    private fun knopf(t: String, haupt: Boolean = false, bei: () -> Unit) = text(t, 15f, if (haupt) Color.BLACK else TEXT, true).apply {
        gravity = Gravity.CENTER
        background = rund(if (haupt) AKZENT else FLAECHE, 16)
        setPadding(dp(16), dp(13), dp(16), dp(13))
        isClickable = true
        setOnClickListener { bei() }
    }

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, oben: Int = 0) =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(oben) }

    // ---------- Aufbau ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = HG
        window.navigationBarColor = HG

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(28), dp(16), dp(32))

        // Kopf
        box.addView(text("Akku", 32f, TEXT, true))
        status = text("", 14f, GRAU)
        status.setPadding(dp(2), dp(4), 0, dp(16))
        box.addView(status)

        erlaubnis = knopf("Bluetooth-Erlaubnis erteilen", true) {
            if (Build.VERSION.SDK_INT >= 33)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS), 1)
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
        box.addView(erlaubnis, lp(oben = 0).apply { bottomMargin = dp(14) })

        // Geraete-Kacheln (antippen = Verlauf anzeigen)
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        kacheln = LinearLayout(this)
        kacheln.orientation = LinearLayout.HORIZONTAL
        scroll.addView(kacheln)
        box.addView(scroll, lp())

        // Verlauf
        box.addView(karte("Verlauf") {
            zeitraum = LinearLayout(this@MainActivity)
            zeitraum.orientation = LinearLayout.HORIZONTAL
            zeitraum.background = rund(FLAECHE, 14)
            zeitraum.setPadding(dp(3), dp(3), dp(3), dp(3))
            for ((t, h) in listOf("6 h" to 6, "24 h" to 24, "7 Tage" to 168)) {
                val s = text(t, 14f, TEXT, true)
                s.gravity = Gravity.CENTER
                s.setPadding(0, dp(8), 0, dp(8))
                s.tag = h
                s.setOnClickListener { stunden = h; aktualisieren() }
                zeitraum.addView(s, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            addView(zeitraum, lp())
            restGross = text("", 26f, TEXT, true)
            restGross.setPadding(0, dp(16), 0, 0)
            addView(restGross)
            restKlein = text("", 13f, GRAU)
            restKlein.setPadding(0, dp(2), 0, dp(10))
            addView(restKlein)
            diagramm = DiagrammView(this@MainActivity)
            addView(diagramm, lp())
        }, lp(oben = 14))

        // Widget-Aussehen
        box.addView(karte("Widget") {
            vorschau = ImageView(this@MainActivity)
            vorschau.adjustViewBounds = true
            vorschau.background = rund(Color.rgb(70, 92, 120), 16)
            vorschau.setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(vorschau, lp())

            addView(text("Hintergrund", 14f, GRAU).apply { setPadding(0, dp(16), 0, dp(8)) })
            farben = LinearLayout(this@MainActivity)
            farben.orientation = LinearLayout.HORIZONTAL
            for (f in palette) {
                val v = View(this@MainActivity)
                v.tag = f
                v.setOnClickListener { sp.edit().putInt("bg_farbe", f).apply(); speichern() }
                farben.addView(v, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })
            }
            addView(farben)

            val zeile = LinearLayout(this@MainActivity)
            zeile.orientation = LinearLayout.HORIZONTAL
            zeile.gravity = Gravity.CENTER_VERTICAL
            zeile.setPadding(0, dp(16), 0, 0)
            zeile.addView(text("Deckkraft", 14f, GRAU), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            deckWert = text("", 14f, TEXT, true)
            zeile.addView(deckWert)
            addView(zeile)
            deck = SeekBar(this@MainActivity)
            deck.max = 100
            deck.progress = sp.getInt("bg_deck", PillRenderer.STANDARD_DECKKRAFT)
            deck.progressTintList = ColorStateList.valueOf(AKZENT)
            deck.thumbTintList = ColorStateList.valueOf(AKZENT)
            deck.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (u) { sp.edit().putInt("bg_deck", p).apply(); speichern() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            addView(deck, lp(oben = 4))
            addView(knopf("Zurücksetzen") {
                sp.edit().putInt("bg_farbe", PillRenderer.STANDARD_FARBE).putInt("bg_deck", PillRenderer.STANDARD_DECKKRAFT).apply()
                deck.progress = PillRenderer.STANDARD_DECKKRAFT
                speichern()
            }, lp(oben = 12))
        }, lp(oben = 14))

        // Einstellungen
        box.addView(karte("Einstellungen") {
            val live = Switch(this@MainActivity)
            live.text = "Live-Aktualisierung"
            live.textSize = 15f
            live.setTextColor(TEXT)
            live.isChecked = sp.getBoolean("live", true)
            live.thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(AKZENT, Color.rgb(180, 180, 180))
            )
            live.setOnCheckedChangeListener { _, an ->
                sp.edit().putBoolean("live", an).apply()
                if (an) LiveService.starten(this@MainActivity) else LiveService.stoppen(this@MainActivity)
            }
            addView(live, lp())
            addView(text("Alle 30 s, nur bei eingeschaltetem Bildschirm", 12f, GRAU).apply { setPadding(0, 0, 0, dp(16)) })

            addView(text("Sync-Code (wie im PC-Programm)", 14f, GRAU).apply { setPadding(0, 0, 0, dp(8)) })
            val code = EditText(this@MainActivity)
            code.setSingleLine(true)
            code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            code.setTextColor(TEXT)
            code.setHintTextColor(GRAU)
            code.hint = "z. B. yoga-akku-…"
            code.textSize = 15f
            code.background = rund(FLAECHE, 14)
            code.setPadding(dp(14), dp(12), dp(14), dp(12))
            code.setText(sp.getString("sync_code", ""))
            code.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) {
                    sp.edit().putString("sync_code", (e?.toString() ?: "").trim()).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            addView(code, lp())
            syncStatus = text("", 12f, GRAU)
            syncStatus.setPadding(0, dp(8), 0, 0)
            addView(syncStatus)

            val reihe = LinearLayout(this@MainActivity)
            reihe.orientation = LinearLayout.HORIZONTAL
            val a = knopf("Sync testen") { syncJetzt() }
            val b = knopf("Widget aktualisieren") {
                EarbudWidget.anfordern(this@MainActivity)
                status.text = "Aktualisiere …"
                kacheln.postDelayed({ aktualisieren() }, 4000)
            }
            reihe.addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            reihe.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(reihe, lp(oben = 14))
            addView(text("Widget: Startbildschirm lange drücken → Widgets → „Ohrhörer-Akku“. Antippen aktualisiert.\n" +
                "Ist die JBL-App offen, kann sie den Kanal belegen.", 12f, GRAU).apply { setPadding(0, dp(14), 0, 0) })
        }, lp(oben = 14))

        val sv = ScrollView(this)
        sv.setBackgroundColor(HG)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(box)
        setContentView(sv)
    }

    // ---------- Daten ----------
    private fun handyJetzt(): Pair<Int, Boolean>? {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l < 0 || s <= 0) return null
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return Pair(l * 100 / s, st == BatteryManager.BATTERY_STATUS_CHARGING)
    }

    private fun aktuell(): Map<String, Pair<Int, Boolean>?> {
        fun w(k: String, lk: String): Pair<Int, Boolean>? =
            sp.getInt(k, -1).let { if (it < 0) null else Pair(it, sp.getBoolean(lk, false)) }
        val h = handyJetzt()
        if (h != null) Verlauf.add(this, "handy", h.first, h.second)
        return mapOf(
            "ohr_l" to w("l", "ll"), "ohr_r" to w("r", "lr"), "case" to w("c", "lc"),
            "laptop" to Sync.laptop(this), "handy" to h
        )
    }

    private fun kachelnBauen(werte: Map<String, Pair<Int, Boolean>?>) {
        kacheln.removeAllViews()
        for ((key, name) in geraete) {
            if (key == "laptop" && !Sync.aktiv(this)) continue
            val w = werte[key]
            val k = LinearLayout(this)
            k.orientation = LinearLayout.VERTICAL
            val aktiv = key == gewaehlt
            k.background = rund(if (aktiv) Color.rgb(36, 46, 40) else KARTE, 20, if (aktiv) 2 else 0, AKZENT)
            k.setPadding(dp(14), dp(12), dp(14), dp(12))
            k.addView(text(name, 13f, GRAU))
            val zahl = text(if (w == null) "–" else "${w.first}%", 24f, if (w == null) GRAU else PillRenderer.farbe(w.first), true)
            k.addView(zahl)
            k.addView(text(if (w?.second == true) "⚡ lädt" else " ", 12f, Color.rgb(255, 214, 10)))
            k.setOnClickListener { gewaehlt = key; aktualisieren() }
            kacheln.addView(k, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) })
        }
    }

    private fun aktualisieren() {
        // Zeitraum-Schalter
        for (i in 0 until zeitraum.childCount) {
            val v = zeitraum.getChildAt(i) as TextView
            val an = v.tag == stunden
            v.background = if (an) rund(Color.rgb(70, 70, 76), 11) else null
            v.setTextColor(if (an) TEXT else GRAU)
        }
        Thread {
            val werte = aktuell()
            val proben = Verlauf.holen(this, gewaehlt, stunden)
            val sz = Verlauf.schaetze(Verlauf.holen(this, gewaehlt, 8))
            runOnUiThread {
                kachelnBauen(werte)
                val name = langeNamen[gewaehlt] ?: gewaehlt
                when (sz.modus) {
                    "entladen" -> {
                        restGross.text = "Noch ca. ${Verlauf.dauerText(sz.stunden)}"
                        restKlein.text = "$name · ${"%.1f".format(-(sz.steigung ?: 0.0))} % pro Stunde"
                    }
                    "laden" -> {
                        restGross.text = "Voll in ca. ${Verlauf.dauerText(sz.stunden)}"
                        restKlein.text = "$name · lädt mit ${"%.1f".format(sz.steigung ?: 0.0)} % pro Stunde"
                    }
                    else -> {
                        restGross.text = name
                        restKlein.text = "Für die Restdauer braucht es noch ein paar Messwerte (mind. 10 Minuten)."
                    }
                }
                diagramm.proben = proben
                diagramm.stunden = stunden
                diagramm.schaetzung = sz
                diagramm.invalidate()
                zeigeStatus()
            }
        }.start()
    }

    private fun syncJetzt() {
        syncStatus.text = "Teste …"
        Thread {
            Sync.abgleich(this)
            runOnUiThread { zeigeStatus(); EarbudWidget.neuZeichnen(this); aktualisieren() }
        }.start()
    }

    private fun speichern() {
        EarbudWidget.neuZeichnen(this)
        vorschauZeigen()
    }

    private fun vorschauZeigen() {
        val aktuelleFarbe = sp.getInt("bg_farbe", PillRenderer.STANDARD_FARBE) and 0xFFFFFF
        for (i in 0 until farben.childCount) {
            val v = farben.getChildAt(i)
            val f = v.tag as Int
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(Color.red(f), Color.green(f), Color.blue(f)))
                setStroke(dp(if (f == aktuelleFarbe) 3 else 1), if (f == aktuelleFarbe) AKZENT else Color.rgb(80, 80, 86))
            }
        }
        deckWert.text = "${deck.progress} %"
        vorschau.setImageBitmap(EarbudWidget.vorschau(this))
    }

    private fun zeigeStatus() {
        val ok = EarbudReader.hatErlaubnis(this)
        erlaubnis.visibility = if (ok) View.GONE else View.VISIBLE
        status.text = if (ok) "${EarbudReader.NAME} · Widget aktiv" else "Bluetooth-Erlaubnis fehlt noch"
        syncStatus.text = if (Sync.aktiv(this)) Sync.status(this) else "Kein Sync-Code – Laptop-Akku aus."
    }

    override fun onResume() {
        super.onResume()
        vorschauZeigen()
        aktualisieren()
        LiveService.starten(this)
        if (Sync.aktiv(this)) Thread {
            Sync.abgleich(this)
            runOnUiThread { EarbudWidget.neuZeichnen(this); aktualisieren() }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EarbudWidget.anfordern(this)
        LiveService.starten(this)
        zeigeStatus()
    }
}

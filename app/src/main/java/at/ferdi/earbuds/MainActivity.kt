package at.ferdi.earbuds

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var syncStatus: TextView
    private lateinit var diagramm: DiagrammView
    private lateinit var diagrammText: TextView
    private var gewaehlt = "handy"
    private var stunden = 24
    private lateinit var vorschau: ImageView
    private lateinit var wertDeck: TextView
    private lateinit var rot: SeekBar
    private lateinit var gruen: SeekBar
    private lateinit var blau: SeekBar
    private lateinit var deck: SeekBar

    private val sp by lazy { getSharedPreferences(EarbudWidget.PREFS, MODE_PRIVATE) }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun beschriftung(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 15f
        v.setPadding(0, dp(14), 0, dp(2))
        return v
    }

    private fun regler(max: Int, wert: Int, bei: () -> Unit): SeekBar {
        val s = SeekBar(this)
        s.max = max
        s.progress = wert
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, vomNutzer: Boolean) { if (vomNutzer) bei() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return s
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, pad)

        val titel = TextView(this)
        titel.text = "Ohrhörer-Akku"
        titel.textSize = 24f
        box.addView(titel)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, pad, 0, pad)
        box.addView(status)

        val erlaubnis = Button(this)
        erlaubnis.text = "Bluetooth-Erlaubnis erteilen"
        erlaubnis.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS), 1)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            }
        }
        box.addView(erlaubnis)

        val jetzt = Button(this)
        jetzt.text = "Widget jetzt aktualisieren"
        jetzt.setOnClickListener {
            EarbudWidget.anfordern(this)
            status.text = "Aktualisiere ..."
        }
        box.addView(jetzt)

        val live = CheckBox(this)
        live.text = "Live-Aktualisierung (alle 30 s, nur bei eingeschaltetem Bildschirm)"
        live.isChecked = sp.getBoolean("live", true)
        live.setOnCheckedChangeListener { _, an ->
            sp.edit().putBoolean("live", an).apply()
            if (an) LiveService.starten(this) else LiveService.stoppen(this)
        }
        box.addView(live)

        // --- Verlauf und Restdauer ---
        box.addView(beschriftung("Verlauf und voraussichtliche Restdauer"))
        val geraete = arrayOf(
            Pair("Links", "ohr_l"), Pair("Rechts", "ohr_r"), Pair("Case", "case"), Pair("Laptop", "laptop"), Pair("Handy", "handy")
        )
        val reihe1 = LinearLayout(this)
        reihe1.orientation = LinearLayout.HORIZONTAL
        for ((titel2, key) in geraete) {
            val b = Button(this)
            b.text = titel2
            b.isAllCaps = false
            b.setOnClickListener { gewaehlt = key; diagrammAktualisieren() }
            reihe1.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(reihe1)
        val reihe2 = LinearLayout(this)
        reihe2.orientation = LinearLayout.HORIZONTAL
        for ((titel2, h) in arrayOf(Pair("6 h", 6), Pair("24 h", 24), Pair("7 Tage", 168))) {
            val b = Button(this)
            b.text = titel2
            b.isAllCaps = false
            b.setOnClickListener { stunden = h; diagrammAktualisieren() }
            reihe2.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(reihe2)
        diagrammText = TextView(this)
        diagrammText.textSize = 15f
        diagrammText.setPadding(0, dp(8), 0, dp(4))
        box.addView(diagrammText)
        diagramm = DiagrammView(this)
        box.addView(diagramm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- Sync mit dem PC ---
        box.addView(beschriftung("Sync-Code (derselbe wie im PC-Programm; leer = Laptop-Akku aus)"))
        val code = EditText(this)
        code.setSingleLine(true)
        code.setText(sp.getString("sync_code", ""))
        code.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
                sp.edit().putString("sync_code", (e?.toString() ?: "").trim()).apply()
                EarbudWidget.anfordern(this@MainActivity)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        box.addView(code)
        syncStatus = TextView(this)
        syncStatus.textSize = 13f
        box.addView(syncStatus)
        val test = Button(this)
        test.text = "Sync jetzt testen"
        test.setOnClickListener {
            syncStatus.text = "Teste ..."
            Thread {
                val t = Sync.abgleich(this)
                runOnUiThread {
                    syncStatus.text = "Code gespeichert: ${if (Sync.aktiv(this)) "ja (${Sync.code(this).length} Zeichen)" else "nein"}\n$t"
                    EarbudWidget.neuZeichnen(this)
                }
            }.start()
        }
        box.addView(test)

        // --- Aussehen ---
        box.addView(beschriftung("Vorschau"))
        vorschau = ImageView(this)
        vorschau.adjustViewBounds = true
        vorschau.setBackgroundColor(Color.rgb(90, 110, 140))
        vorschau.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.addView(vorschau)

        val farbe = sp.getInt("bg_farbe", PillRenderer.STANDARD_FARBE)
        val aendern = { speichern() }
        box.addView(beschriftung("Hintergrundfarbe: Rot"))
        rot = regler(255, Color.red(farbe), aendern); box.addView(rot)
        box.addView(beschriftung("Grün"))
        gruen = regler(255, Color.green(farbe), aendern); box.addView(gruen)
        box.addView(beschriftung("Blau"))
        blau = regler(255, Color.blue(farbe), aendern); box.addView(blau)

        wertDeck = beschriftung("")
        box.addView(wertDeck)
        deck = regler(100, sp.getInt("bg_deck", PillRenderer.STANDARD_DECKKRAFT), aendern)
        box.addView(deck)

        val standard = Button(this)
        standard.text = "Auf Standard zurücksetzen"
        standard.setOnClickListener {
            rot.progress = Color.red(PillRenderer.STANDARD_FARBE)
            gruen.progress = Color.green(PillRenderer.STANDARD_FARBE)
            blau.progress = Color.blue(PillRenderer.STANDARD_FARBE)
            deck.progress = PillRenderer.STANDARD_DECKKRAFT
            speichern()
        }
        box.addView(standard)

        val scroll = ScrollView(this)
        scroll.addView(box)
        setContentView(scroll)
        aktualisiereVorschau()
    }

    private fun diagrammAktualisieren() {
        val namen = mapOf("ohr_l" to "Linker Ohrhörer", "ohr_r" to "Rechter Ohrhörer", "case" to "Ladecase", "laptop" to "Laptop", "handy" to "Handy")
        Thread {
            val proben = Verlauf.holen(this, gewaehlt, stunden)
            val sz = Verlauf.schaetze(Verlauf.holen(this, gewaehlt, 8))
            val jetzt = proben.lastOrNull()?.p
            val zeile = (namen[gewaehlt] ?: gewaehlt) + (if (jetzt != null) "  $jetzt %" else "") + "\n" + when (sz.modus) {
                "entladen" -> "Noch ca. ${Verlauf.dauerText(sz.stunden)}  (${"%.1f".format(-(sz.steigung ?: 0.0))} % pro Stunde)"
                "laden" -> "Lädt: voll in ca. ${Verlauf.dauerText(sz.stunden)}"
                else -> "Restdauer: noch zu wenig Messwerte (braucht ein paar Messungen über mindestens 10 Minuten)"
            }
            runOnUiThread {
                diagramm.proben = proben
                diagramm.stunden = stunden
                diagramm.schaetzung = sz
                diagramm.invalidate()
                diagrammText.text = zeile
            }
        }.start()
    }

    private fun speichern() {
        sp.edit()
            .putInt("bg_farbe", Color.rgb(rot.progress, gruen.progress, blau.progress))
            .putInt("bg_deck", deck.progress)
            .apply()
        EarbudWidget.neuZeichnen(this)
        aktualisiereVorschau()
    }

    private fun aktualisiereVorschau() {
        wertDeck.text = "Deckkraft des Hintergrunds: ${deck.progress} %  (0 = ganz durchsichtig)"
        vorschau.setImageBitmap(EarbudWidget.vorschau(this))
    }

    override fun onResume() {
        super.onResume()
        diagrammAktualisieren()
        zeigeStatus()
        aktualisiereVorschau()
        LiveService.starten(this)
        if (Sync.aktiv(this)) Thread {
            val t = Sync.abgleich(this)
            runOnUiThread { zeigeStatus(); EarbudWidget.neuZeichnen(this) }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EarbudWidget.anfordern(this)
        LiveService.starten(this)
        zeigeStatus()
    }

    private fun zeigeStatus() {
        syncStatus.text = "Code gespeichert: ${if (Sync.aktiv(this)) "ja (${Sync.code(this).length} Zeichen)" else "nein"}\nLetzter Abgleich:\n${Sync.status(this)}"
        val ok = EarbudReader.hatErlaubnis(this)
        status.text = (if (ok) "Bluetooth-Erlaubnis: erteilt.\n\n" else "Bluetooth-Erlaubnis: fehlt noch.\n\n") +
            "So bekommst du das Widget: Startbildschirm lange drücken → Widgets → „Ohrhörer-Akku“ " +
            "auf den Startbildschirm ziehen. Ein Tipp aufs Widget aktualisiert es.\n\n" +
            "Gesucht wird das gekoppelte Gerät „${EarbudReader.NAME}“. Ist die JBL-App gleichzeitig offen, " +
            "kann sie den Kanal belegen – dann die JBL-App schließen."
    }
}

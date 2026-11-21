package at.ferdi.earbuds

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
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
        zeigeStatus()
        aktualisiereVorschau()
        LiveService.starten(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EarbudWidget.anfordern(this)
        LiveService.starten(this)
        zeigeStatus()
    }

    private fun zeigeStatus() {
        val ok = EarbudReader.hatErlaubnis(this)
        status.text = (if (ok) "Bluetooth-Erlaubnis: erteilt.\n\n" else "Bluetooth-Erlaubnis: fehlt noch.\n\n") +
            "So bekommst du das Widget: Startbildschirm lange drücken → Widgets → „Ohrhörer-Akku“ " +
            "auf den Startbildschirm ziehen. Ein Tipp aufs Widget aktualisiert es.\n\n" +
            "Gesucht wird das gekoppelte Gerät „${EarbudReader.NAME}“. Ist die JBL-App gleichzeitig offen, " +
            "kann sie den Kanal belegen – dann die JBL-App schließen."
    }
}

package at.ferdi.earbuds

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, pad)

        val titel = TextView(this)
        titel.text = "Ohrhörer-Akku"
        titel.textSize = 24f
        box.addView(titel)

        status = TextView(this)
        status.textSize = 16f
        status.setPadding(0, pad, 0, pad)
        box.addView(status)

        val erlaubnis = Button(this)
        erlaubnis.text = "Bluetooth-Erlaubnis erteilen"
        erlaubnis.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

        setContentView(box)
    }

    override fun onResume() {
        super.onResume()
        zeigeStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EarbudWidget.anfordern(this)
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

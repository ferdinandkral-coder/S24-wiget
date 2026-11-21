package at.ferdi.earbuds

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Liest den Akkustand der JBL ueber den Google-Fast-Pair-Nachrichtenkanal (RFCOMM, UUID df21fe2c-...).
 * Nachricht: [Gruppe][Code][Laenge, 2 Byte][Daten]. Akku: Gruppe 0x03, Code 0x03, Daten = links, rechts, Case.
 * Jedes Byte: Bit 7 = laedt, Bit 0-6 = Prozent, 0x7F = unbekannt.
 */
object EarbudReader {
    const val NAME = "JBL Tune 255NC"
    private val KANAL: UUID = UUID.fromString("df21fe2c-2515-4fdb-8886-f12c4d67927c")

    sealed class Ergebnis {
        /** Kopfhoerer ist nicht verbunden (oder Bluetooth aus). */
        object NichtVerbunden : Ergebnis()
        /** Verbunden, aber der Wert konnte gerade nicht gelesen werden. */
        object Unklar : Ergebnis()
        class Werte(val links: Int?, val rechts: Int?, val case: Int?, val laden: BooleanArray) : Ergebnis()
    }

    fun hatErlaubnis(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lesen(ctx: Context): Ergebnis {
        if (!hatErlaubnis(ctx)) return Ergebnis.Unklar
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return Ergebnis.NichtVerbunden
        if (!adapter.isEnabled) return Ergebnis.NichtVerbunden
        val geraet = adapter.bondedDevices.firstOrNull { (it.name ?: "").contains(NAME, ignoreCase = true) }
            ?: return Ergebnis.NichtVerbunden
        if (!verbunden(ctx, adapter, geraet)) return Ergebnis.NichtVerbunden
        return abfragen(geraet) ?: Ergebnis.Unklar
    }

    /** Ist das Geraet gerade ueber Audio (A2DP) oder Headset verbunden? */
    @SuppressLint("MissingPermission")
    private fun verbunden(ctx: Context, adapter: BluetoothAdapter, geraet: BluetoothDevice): Boolean {
        for (profil in intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            val latch = CountDownLatch(1)
            var ja = false
            val ok = adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    try {
                        ja = proxy.connectedDevices.any { it.address == geraet.address }
                    } finally {
                        adapter.closeProfileProxy(p, proxy)
                        latch.countDown()
                    }
                }

                override fun onServiceDisconnected(p: Int) {}
            }, profil)
            if (ok) latch.await(3, TimeUnit.SECONDS)
            if (ja) return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun abfragen(geraet: BluetoothDevice): Ergebnis.Werte? {
        var socket: BluetoothSocket? = null
        val fertig = CountDownLatch(1)
        // Wachhund: schliesst den Socket nach 9 s, falls Verbinden oder Lesen haengt
        Thread {
            if (!fertig.await(9, TimeUnit.SECONDS)) {
                try { socket?.close() } catch (e: Exception) {}
            }
        }.start()
        try {
            val s = geraet.createRfcommSocketToServiceRecord(KANAL)
            socket = s
            s.connect()
            val ein = s.inputStream
            val puffer = ArrayList<Int>()
            val ende = System.currentTimeMillis() + 5000
            val block = ByteArray(256)
            while (System.currentTimeMillis() < ende) {
                if (ein.available() <= 0) {
                    Thread.sleep(100)
                    continue
                }
                val n = ein.read(block)
                if (n <= 0) break
                for (i in 0 until n) puffer.add(block[i].toInt() and 0xFF)
                while (puffer.size >= 4) {
                    val laenge = (puffer[2] shl 8) or puffer[3]
                    if (puffer.size < 4 + laenge) break
                    val gruppe = puffer[0]
                    val code = puffer[1]
                    val daten = puffer.subList(4, 4 + laenge).toList()
                    repeat(4 + laenge) { puffer.removeAt(0) }
                    if (gruppe == 0x03 && code == 0x03 && daten.size >= 3) {
                        return Ergebnis.Werte(
                            akku(daten[0]), akku(daten[1]), akku(daten[2]),
                            booleanArrayOf((daten[0] and 0x80) != 0, (daten[1] and 0x80) != 0, (daten[2] and 0x80) != 0)
                        )
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            fertig.countDown()
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    private fun akku(b: Int): Int? = if ((b and 0x7F) == 0x7F) null else minOf(100, b and 0x7F)
}

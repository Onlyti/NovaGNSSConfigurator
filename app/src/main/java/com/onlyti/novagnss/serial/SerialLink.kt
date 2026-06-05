package com.onlyti.novagnss.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.atomic.AtomicLong

/**
 * USB-host serial link to a GNSS receiver (NovAtel OEM7 / u-blox / any CDC/FTDI/CP210x/CH340).
 * Bidirectional: write ASCII commands, read back the response stream. Shared with rtk-router.
 *
 * USB permission is requested on demand via PendingIntent + BroadcastReceiver.
 */
class SerialLink(
    private val context: Context,
    private val baud: Int,
    private val portIndex: Int,
    private val onConnected: (deviceName: String, portCount: Int, port: Int) -> Unit,
    private val onDisconnected: (reason: String) -> Unit,
    private val onData: (ByteArray) -> Unit,
) {
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    @Volatile private var receiverRegistered = false
    val rxBytes = AtomicLong(0)
    val txBytes = AtomicLong(0)

    private val usbManager get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            unregister()
            if (granted) openFirstDriver() else onDisconnected("USB permission denied")
        }
    }

    fun connect() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            onDisconnected("no USB serial device found")
            return
        }
        val device = drivers[0].device
        if (usbManager.hasPermission(device)) {
            openFirstDriver()
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags,
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(permissionReceiver, filter)
            }
            receiverRegistered = true
            usbManager.requestPermission(device, pi)
        }
    }

    private fun openFirstDriver() {
        try {
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
                ?: run { onDisconnected("device detached"); return }
            val connection = usbManager.openDevice(driver.device)
                ?: run { onDisconnected("openDevice failed"); return }
            val portCount = driver.ports.size
            // portIndex < 0 => auto-detect the responding CDC port (NovAtel exposes several).
            val idx = if (portIndex < 0) probePorts(driver, connection).coerceIn(0, portCount - 1)
                      else portIndex.coerceIn(0, portCount - 1)
            val p = driver.ports[idx]
            p.open(connection)
            p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p

            val io = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    rxBytes.addAndGet(data.size.toLong())
                    onData(data)
                }
                override fun onRunError(e: Exception) {
                    onDisconnected("serial read error: ${e.message}")
                }
            })
            ioManager = io
            io.start()

            val name = driver.device.productName ?: driver.javaClass.simpleName
            Log.d(TAG, "serial open: driver=${driver.javaClass.simpleName} device=$name port=$idx/$portCount baud=$baud")
            onConnected(name, portCount, idx)
        } catch (t: Throwable) {
            onDisconnected("open failed: ${t.message}")
        }
    }

    /**
     * Probe each CDC port with a NovAtel command and return the index that responds.
     * Score: 2 = returns a VERSION reply, 1 = any data, 0 = silent. Picks the best.
     */
    private fun probePorts(driver: com.hoho.android.usbserial.driver.UsbSerialDriver, connection: android.hardware.usb.UsbDeviceConnection): Int {
        var best = 0
        var bestScore = -1
        for (i in driver.ports.indices) {
            val p = driver.ports[i]
            var score = 0
            try {
                p.open(connection)
                p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                p.write("\r\nLOG VERSIONA ONCE\r\n".toByteArray(Charsets.US_ASCII), 500)
                val buf = ByteArray(2048)
                val sb = StringBuilder()
                val deadline = System.currentTimeMillis() + PROBE_MS
                while (System.currentTimeMillis() < deadline) {
                    val n = try { p.read(buf, 250) } catch (_: Throwable) { 0 }
                    if (n > 0) sb.append(String(buf, 0, n, Charsets.US_ASCII))
                    if (sb.contains("VERSION")) break
                }
                score = if (sb.contains("VERSION")) 2 else if (sb.isNotEmpty()) 1 else 0
                Log.d(TAG, "probe port $i: score=$score (${sb.length}B)")
            } catch (_: Throwable) {
            } finally {
                try { p.close() } catch (_: Throwable) {}
            }
            if (score > bestScore) { bestScore = score; best = i }
            if (bestScore >= 2) break
        }
        Log.d(TAG, "auto-detect -> port $best")
        return best
    }

    /** Write raw bytes to the receiver. */
    fun write(data: ByteArray) {
        val p = port ?: return
        try {
            p.write(data, WRITE_TIMEOUT_MS)
            txBytes.addAndGet(data.size.toLong())
        } catch (_: Throwable) {
        }
    }

    private fun unregister() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(permissionReceiver) } catch (_: Throwable) {}
            receiverRegistered = false
        }
    }

    fun close() {
        try { ioManager?.stop() } catch (_: Throwable) {}
        try { port?.close() } catch (_: Throwable) {}
        unregister()
        ioManager = null
        port = null
    }

    companion object {
        private const val TAG = "novatel"
        private const val ACTION_USB_PERMISSION = "com.onlyti.novagnss.USB_PERMISSION"
        private const val WRITE_TIMEOUT_MS = 2000
        private const val PROBE_MS = 1300L   // per-port auto-detect listen window
    }
}

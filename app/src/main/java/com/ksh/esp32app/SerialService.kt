package com.ksh.esp32app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.ArrayDeque

/**
 * A bound and foreground [Service] that manages the USB serial connection.
 * This service handles the low-level details of opening, closing, reading from, and writing to the serial port.
 * It runs as a foreground service to ensure the connection remains active even when the app is not in the foreground.
 */
class SerialService : Service(), SerialInputOutputManager.Listener {

    /**
     * Binder class for clients to get a reference to the [SerialService].
     */
    inner class SerialBinder : Binder() {
        fun getService(): SerialService = this@SerialService
    }

    private val binder = SerialBinder()
    private val mainLooper: Handler = Handler(Looper.getMainLooper())
    private var listener: SerialListener? = null
    private var port: UsbSerialPort? = null
    private var serialIoManager: SerialInputOutputManager? = null
    private var connected = false
    private var isInitializing = false

    /**
     * Called when a client binds to the service.
     * @return The [IBinder] that clients can use to communicate with the service.
     */
    override fun onBind(intent: Intent): IBinder {
        FileLogger.log("SerialService", "onBind")
        return binder
    }

    /**
     * Establishes a connection to the specified serial port.
     *
     * @param listener The listener that will receive callbacks for serial events.
     * @param port The [UsbSerialPort] to connect to.
     * @param baudRate The baud rate for the connection.
     * @param dataBits The number of data bits.
     * @param stopBits The number of stop bits.
     * @param parity The parity scheme.
     * @param quietPeriod The quiet period in seconds to wait before processing data.
     */
    fun connect(listener: SerialListener, port: UsbSerialPort, baudRate: Int, dataBits: Int, stopBits: Int, parity: Int, quietPeriod: Int) {
        this.listener = listener
        this.port = port
        try {
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            val usbDeviceConnection = usbManager.openDevice(port.device) ?: throw IOException("Failed to open device")
            port.open(usbDeviceConnection)
            port.setParameters(baudRate, dataBits, stopBits, parity)
            // Disable DTR/RTS to prevent reset
            port.dtr = false
            port.rts = false

            // Set a flag to ignore incoming data during the initial flood.
            isInitializing = true
            connected = false

            // Start the I/O manager immediately. onNewData will begin firing but will drop the data.
            serialIoManager = SerialInputOutputManager(port, this)
            serialIoManager?.start()
            
            // Promote the service to a foreground service.
            createNotification()

            // After a "quiet period", we'll consider the connection stable and start processing data.
            // This is necessary because the device floods the connection with status messages for
            // about 20-30 seconds on startup. This delay prevents an ANR (Application Not Responding).
            val quietPeriodMillis = quietPeriod * 1000L
            Handler(Looper.getMainLooper()).postDelayed({
                if (this.port?.isOpen != true) {
                    isInitializing = false
                    return@postDelayed // Port was closed during the quiet period
                }
                FileLogger.log("SerialService", "Quiet period finished. Accepting data.")
                isInitializing = false
                connected = true
                mainLooper.post { this.listener?.onSerialConnect() }
            }, quietPeriodMillis) // Use configurable quiet period.

        } catch (e: Exception) {
            // If an error occurs, notify the listener.
            isInitializing = false
            FileLogger.log("SerialService", "Error during connect", e)
            mainLooper.post { this.listener?.onSerialConnectError(e) }
        }
    }

    /**
     * Disconnects from the serial port and stops the service.
     */
    fun disconnect() {
        isInitializing = false
        connected = false
        serialIoManager?.stop()
        serialIoManager = null
        try {
            port?.close()
        } catch (ignored: IOException) {
        }
        port = null
        // Stop the foreground service and remove the notification.
        stopForeground(true)
    }

    /**
     * Writes data to the serial port asynchronously.
     *
     * @param data The byte array to write.
     */
    fun write(data: ByteArray) {
        if (connected) {
            try {
                serialIoManager?.writeAsync(data)
            } catch (e: IOException) {
                onRunError(e)
            }
        }
    }

    /**
     * Callback from [SerialInputOutputManager] when new data is received from the serial port.
     *
     * @param data The new data that was received.
     */
    override fun onNewData(data: ByteArray) {
        if (isInitializing || !connected) {
            // While initializing, drop all incoming data to avoid flooding the main thread.
            return
        }
        // Post the data to the main looper to be processed by the listener (ViewModel).
        mainLooper.post { listener?.onSerialRead(ArrayDeque(listOf(data))) }
    }

    /**
     * Callback from [SerialInputOutputManager] when an I/O error occurs.
     *
     * @param e The exception that occurred.
     */
    override fun onRunError(e: Exception) {
        if (connected) {
            FileLogger.log("SerialService", "onRunError", e)
            // Post the error to the main looper to be handled by the listener.
            mainLooper.post { listener?.onSerialIoError(e) }
        }
    }

    /**
     * Creates and displays a notification to indicate that the service is running in the foreground.
     * This is required for services that need to run for an extended period.
     */
    private fun createNotification() {
        val channelId = "serial_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Serial Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("USB Serial Connected")
            .setContentText("Connected to device")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }
}
package com.ksh.esp32app

import java.util.ArrayDeque

/**
 * An interface for receiving events from the [SerialService].
 * The [MainViewModel] implements this interface to handle serial communication events.
 */
interface SerialListener {
    /**
     * Called when the connection process begins.
     */
    fun onSerialInitialize()

    /**
     * Called when a serial connection is successfully established and the quiet period is over.
     */
    fun onSerialConnect()

    /**
     * Called when an error occurs while attempting to connect.
     * @param e The exception that occurred.
     */
    fun onSerialConnectError(e: Exception)

    /**
     * Called when new data is received from the serial port.
     * The data is provided as a queue of byte arrays.
     * @param datas A queue containing the received data chunks.
     */
    fun onSerialRead(datas: ArrayDeque<ByteArray>)

    /**
     * Called when an I/O error occurs during serial communication (e.g., the device is disconnected).
     * @param e The exception that occurred.
     */
    fun onSerialIoError(e: Exception)
}

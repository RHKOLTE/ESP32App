package com.ksh.esp32app

import androidx.compose.runtime.Immutable

/**
 * Defines the type of a line displayed in the terminal.
 * This is used to differentiate between data received, data sent, and status messages,
 * often for styling purposes (e.g., applying different colors).
 */
@Immutable
enum class LineType {
    /** Data received from the connected device. */
    INCOMING,
    /** Data sent from the app to the device (local echo). */
    OUTGOING,
    /** Status messages, such as "Connected" or "Disconnected". */
    STATUS
}

/**
 * Represents a single line of text in the terminal display.
 * This data class is immutable, which is beneficial for performance in Jetpack Compose.
 *
 * @property text The actual text content of the line.
 * @property type The [LineType] of the line, indicating its origin or purpose.
 */
@Immutable
data class TerminalLine(val text: String, val type: LineType)

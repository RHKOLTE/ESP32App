package com.ksh.esp32app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Represents a macro command that can be sent to the connected device.
 * This data class is immutable and serializable, allowing it to be easily stored and passed around.
 *
 * @property name The user-defined name of the macro, displayed on the macro button.
 * @property value The actual string/command to be sent. This can be plain text or a hexadecimal string.
 * @property isHex If true, the [value] is treated as a hexadecimal string and converted to bytes before sending.
 * @property repeat If true, the macro command is sent repeatedly with a specified delay.
 * @property repeatDelay The delay in milliseconds between repetitions if [repeat] is true.
 */
@Immutable
@Serializable
data class Macro(
    val name: String = "",
    val value: String = "",
    val isHex: Boolean = false,
    val repeat: Boolean = false,
    val repeatDelay: Int = 100
)

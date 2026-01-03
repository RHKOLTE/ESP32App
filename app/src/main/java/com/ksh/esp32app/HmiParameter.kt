package com.ksh.esp32app

import kotlinx.serialization.Serializable

/**
 * Represents the static definition (metadata) of a single Human-Machine Interface (HMI) parameter.
 *
 * @param key The unique command key for the parameter (e.g., "WO", "PN").
 * @param displayName The human-readable name for the UI (e.g., "Work Order").
 * @param isProtected True if viewing or editing this parameter requires a password.
 * @param validationRule A string defining the validation rule (e.g., "1-999", "URL"). Null if no validation is needed.
 * @param editable If true, the user can edit this parameter. Defaults to false.
 * @param value The current value of the parameter, fetched from the device. (This is a dynamic part, not metadata)
 */
@Serializable
data class HmiParameter(
    val key: String,
    val displayName: String,
    val isProtected: Boolean = false,
    val validationRule: String? = null,
    val editable: Boolean = false, // Default to false unless specified in JSON
    var value: String = "" // This holds the dynamic value fetched from the device.
)

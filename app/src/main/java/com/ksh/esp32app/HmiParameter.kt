package com.ksh.esp32app

import kotlinx.serialization.Serializable

/**
 * Represents a single Human-Machine Interface (HMI) parameter, combining static metadata and a dynamic value.
 *
 * @param key The unique command key for the parameter (e.g., "WO", "PN"). Used for GET_DT and SET_DT.
 * @param displayName The human-readable name for the UI (e.g., "Work Order").
 * @param isProtected True if viewing or editing this parameter requires a password. If false, it's considered not protected.
 * @param validationRule A string defining the validation rule (e.g., "1-999", "URL"). Null or empty if no validation is needed.
 * @param value The current value of the parameter, fetched from the device.
 */
@Serializable
data class HmiParameter(
    val key: String,
    val displayName: String,
    val isProtected: Boolean = false,
    val validationRule: String? = null,
    var value: String = "" // This holds the dynamic value
)

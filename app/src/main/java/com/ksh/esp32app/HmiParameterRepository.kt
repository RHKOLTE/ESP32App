package com.ksh.esp32app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * A repository that holds the definitions for all HMI parameters.
 * This provides a single source of truth for parameter metadata like display names, keys, and validation rules.
 * It loads parameters from a custom JSON file if it exists, otherwise falls back to a default JSON in assets.
 */
class HmiParameterRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
    private val customParametersFile = File(context.filesDir, "hmi_parameters.json")

    private val _parametersFlow = MutableStateFlow<List<HmiParameter>>(emptyList())
    val parametersFlow: StateFlow<List<HmiParameter>> = _parametersFlow.asStateFlow()

    init {
        loadParameters()
    }

    private fun loadParameters() {
        val jsonString = if (customParametersFile.exists()) {
            FileLogger.log("HmiRepo", "Loading custom HMI parameters file.")
            customParametersFile.readText()
        } else {
            try {
                FileLogger.log("HmiRepo", "Loading default HMI parameters from assets.")
                context.assets.open("hmi_parameters.json").bufferedReader().use { it.readText() }
            } catch (ioException: IOException) {
                FileLogger.log("HmiRepo", "Failed to load default HMI parameters.", ioException)
                "[]" // Return an empty list on failure
            }
        }
        _parametersFlow.value = json.decodeFromString(ListSerializer(HmiParameter.serializer()), jsonString)
    }

    /**
     * Overwrites the custom parameters file with new content and reloads the parameters.
     * @throws IllegalArgumentException if the jsonString is not a valid parameter list.
     */
    fun saveParameters(jsonString: String) {
        // First, validate if the string is a valid List<HmiParameter>
        try {
            json.decodeFromString<List<HmiParameter>>(jsonString)
            customParametersFile.writeText(jsonString)
            loadParameters() // Reload to update the flow
            FileLogger.log("HmiRepo", "Successfully saved and reloaded custom HMI parameters.")
        } catch (e: Exception) {
            FileLogger.log("HmiRepo", "Failed to save custom HMI parameters.", e)
            throw IllegalArgumentException("Invalid HMI parameter JSON format.")
        }
    }

    /**
     * Gets the content of the currently active parameter JSON file for download/sharing.
     */
    fun getCurrentParametersJson(): String {
        return json.encodeToString(ListSerializer(HmiParameter.serializer()), parametersFlow.value)
    }
}

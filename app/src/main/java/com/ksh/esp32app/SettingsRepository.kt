package com.ksh.esp32app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * A Kotlin extension property to provide a singleton instance of [DataStore] for the application.
 * This creates a file named "settings" in the app's private storage to persist the settings.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * A repository class for managing the application's settings.
 * This class uses Jetpack DataStore to persist the [UiState] object as a JSON string.
 *
 * @param context The application context, used to get the [DataStore] instance.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        // A key to store the settings JSON string in the DataStore.
        val SETTINGS_KEY = stringPreferencesKey("settings")
    }

    /**
     * A flow that emits the [UiState] whenever the settings are updated in the DataStore.
     * It reads the JSON string from DataStore, decodes it into a [UiState] object, and emits it.
     * If the settings don't exist or there's a decoding error, it emits a default [UiState].
     */
    val settingsFlow = dataStore.data.map { preferences ->
        preferences[SETTINGS_KEY]?.let {
            try {
                // Decode the JSON string back into a UiState object.
                Json.decodeFromString<UiState>(it)
            } catch (e: Exception) {
                // If decoding fails, log the error and return a default state.
                FileLogger.log("SettingsRepository", "Error decoding settings", e)
                UiState()
            }
        } ?: UiState() // If the key doesn't exist, return a default state.
    }

    /**
     * Saves the provided [UiState] to the DataStore.
     * It serializes the [UiState] object into a JSON string and saves it.
     *
     * @param uiState The [UiState] object to save.
     */
    suspend fun saveSettings(uiState: UiState) {
        dataStore.edit {
            // Serialize the UiState to a JSON string.
            val json = Json.encodeToString(UiState.serializer(), uiState)
            // Save the JSON string to DataStore with the defined key.
            it[SETTINGS_KEY] = json
        }
    }
}
package com.ksh.esp32app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * A factory for creating instances of [MainViewModel].
 * This factory is necessary because [MainViewModel] has a constructor that takes an [Application]
 * and a [SettingsRepository] as dependencies, and the default ViewModel factory doesn't know how to provide them.
 *
 * @param application The application instance, which is required by [MainViewModel] and [SettingsRepository].
 */
class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    /**
     * Creates a new instance of the given [ViewModel] class.
     *
     * @param modelClass The class of the ViewModel to create.
     * @return A newly created ViewModel.
     * @throws IllegalArgumentException if the provided `modelClass` is not [MainViewModel].
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Check if the requested ViewModel is of type MainViewModel.
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // If it is, create a new instance of MainViewModel, 
            // providing it with the Application context and a new SettingsRepository instance.
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, SettingsRepository(application)) as T
        }
        // If the requested ViewModel is of any other type, throw an exception.
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

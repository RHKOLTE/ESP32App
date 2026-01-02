package com.ksh.esp32app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val settingsRepository = SettingsRepository(application)
            val hmiParameterRepository = HmiParameterRepository(application)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, settingsRepository, hmiParameterRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

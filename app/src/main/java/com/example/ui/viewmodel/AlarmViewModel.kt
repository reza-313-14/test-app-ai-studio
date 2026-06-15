package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AlarmConfig
import com.example.data.database.AlarmLog
import com.example.data.repository.AlarmRepository
import com.example.service.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(private val repository: AlarmRepository) : ViewModel() {

    val configState: StateFlow<AlarmConfig?> = repository.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val logsState: StateFlow<List<AlarmLog>> = repository.logsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateInterval(minutes: Int) {
        viewModelScope.launch {
            val currentConfig = repository.getConfig()
            repository.saveConfig(currentConfig.copy(intervalMinutes = minutes))
        }
    }

    fun toggleAlarm(context: Context) {
        viewModelScope.launch {
            val currentConfig = repository.getConfig()
            val newState = !currentConfig.isEnabled
            
            if (newState) {
                val triggerTime = System.currentTimeMillis() + (currentConfig.intervalMinutes * 60 * 1000L)
                repository.saveConfig(
                    currentConfig.copy(
                        isEnabled = true,
                        nextTriggeredTime = triggerTime
                    )
                )
                AlarmScheduler.scheduleNextAlarm(context, triggerTime)
            } else {
                repository.saveConfig(
                    currentConfig.copy(
                        isEnabled = false,
                        nextTriggeredTime = 0L
                    )
                )
                AlarmScheduler.cancelAlarm(context)
            }
        }
    }

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch {
            val currentConfig = repository.getConfig()
            repository.saveConfig(currentConfig.copy(soundEnabled = enabled))
        }
    }

    fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch {
            val currentConfig = repository.getConfig()
            repository.saveConfig(currentConfig.copy(vibrationEnabled = enabled))
        }
    }

    fun toggleDarkMode(useSystem: Boolean, isDark: Boolean) {
        viewModelScope.launch {
            val currentConfig = repository.getConfig()
            repository.saveConfig(
                currentConfig.copy(
                    useSystemTheme = useSystem,
                    isDarkMode = isDark
                )
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    class Factory(private val repository: AlarmRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
                return AlarmViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

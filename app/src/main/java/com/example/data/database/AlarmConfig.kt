package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_config")
data class AlarmConfig(
    @PrimaryKey val id: Int = 1,
    val intervalMinutes: Int = 30,
    val isEnabled: Boolean = false,
    val lastTriggeredTime: Long = 0L,
    val nextTriggeredTime: Long = 0L,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val isDarkMode: Boolean = false,
    val useSystemTheme: Boolean = true
)

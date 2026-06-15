package com.example.data.repository

import com.example.data.database.AlarmConfig
import com.example.data.database.AlarmDao
import com.example.data.database.AlarmLog
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {

    val configFlow: Flow<AlarmConfig?> = alarmDao.getConfigFlow()
    val logsFlow: Flow<List<AlarmLog>> = alarmDao.getLogsFlow()

    suspend fun getConfig(): AlarmConfig {
        return alarmDao.getConfig() ?: AlarmConfig().also {
            alarmDao.saveConfig(it)
        }
    }

    suspend fun saveConfig(config: AlarmConfig) {
        alarmDao.saveConfig(config)
    }

    suspend fun insertLog(log: AlarmLog) {
        alarmDao.insertLog(log)
    }

    suspend fun clearLogs() {
        alarmDao.clearLogs()
    }
}

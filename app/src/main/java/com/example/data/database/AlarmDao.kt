package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<AlarmConfig?>

    @Query("SELECT * FROM alarm_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): AlarmConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: AlarmConfig)

    @Query("SELECT * FROM alarm_logs ORDER BY timestamp DESC LIMIT 50")
    fun getLogsFlow(): Flow<List<AlarmLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AlarmLog)

    @Query("DELETE FROM alarm_logs")
    suspend fun clearLogs()
}

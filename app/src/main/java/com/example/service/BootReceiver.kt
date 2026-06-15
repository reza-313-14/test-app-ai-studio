package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val repository = AlarmRepository(db.alarmDao)
                    val config = repository.getConfig()

                    if (config.isEnabled) {
                        val currentTimeMs = System.currentTimeMillis()
                        val triggerTimeMs = if (config.nextTriggeredTime > currentTimeMs) {
                            config.nextTriggeredTime
                        } else {
                            currentTimeMs + (config.intervalMinutes * 60 * 1000L)
                        }

                        // Update db with scheduled values
                        repository.saveConfig(
                            config.copy(
                                nextTriggeredTime = triggerTimeMs
                            )
                        )

                        AlarmScheduler.scheduleNextAlarm(context, triggerTimeMs)
                        Log.d("BootReceiver", "Rescheduled alarm for reboot at $triggerTimeMs")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error during boot reschedule", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

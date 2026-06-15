package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.database.AppDatabase
import com.example.data.database.AlarmLog
import com.example.data.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val repository = AlarmRepository(db.alarmDao)
                val config = repository.getConfig()

                if (config.isEnabled) {
                    // 1. Show notification or custom actions
                    showNotification(context, config.intervalMinutes, config.soundEnabled, config.vibrationEnabled)

                    // 2. Insert Log to database
                    repository.insertLog(
                        AlarmLog(
                            intervalMinutes = config.intervalMinutes,
                            isSuccessful = true
                        )
                    )

                    // 3. Compute and schedule next execution
                    val nextTimeMs = System.currentTimeMillis() + (config.intervalMinutes * 60 * 1000L)
                    
                    // Update database
                    repository.saveConfig(
                        config.copy(
                            lastTriggeredTime = System.currentTimeMillis(),
                            nextTriggeredTime = nextTimeMs
                        )
                    )

                    // Schedule next repeating alarm self-contained
                    AlarmScheduler.scheduleNextAlarm(context, nextTimeMs)
                } else {
                    AlarmScheduler.cancelAlarm(context)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error receiving alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        intervalMinutes: Int,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "interval_alarm_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Interval Alarm Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when your custom intervals complete"
                enableVibration(vibrationEnabled)
                if (!vibrationEnabled) {
                    vibrationPattern = longArrayOf(0)
                }
                if (!soundEnabled) {
                    setSound(null, null)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification
        val titleText = "Interval Completed!"
        val contentText = if (intervalMinutes == 1) {
            "Your 1 minute interval has elapsed."
        } else {
            "Your $intervalMinutes minutes interval has elapsed."
        }

        val designAccentColor = 0xFF00BFA5.toInt() // Neon Teal

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setColor(designAccentColor)

        // Custom Sound/Vibration fallbacks for API < 26
        if (soundEnabled) {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(defaultSoundUri)
        }
        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 400, 200, 400))
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}

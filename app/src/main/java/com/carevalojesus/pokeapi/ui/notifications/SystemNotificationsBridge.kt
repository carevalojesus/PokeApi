package com.carevalojesus.pokeapi.ui.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carevalojesus.pokeapi.MainActivity
import com.carevalojesus.pokeapi.R
import com.carevalojesus.pokeapi.data.firebase.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

object SystemNotificationsBridge {

    private const val CHANNEL_ID = "pokeapi_general_notifications"
    private const val PREFS_NAME = "system_notifications_bridge"
    private const val PREF_KEY_SHOWN = "shown_ids"
    private const val MAX_STORED_IDS = 300

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null

    fun start(context: Context, repository: FirebaseRepository) {
        createChannelIfNeeded(context)
        listenJob?.cancel()
        listenJob = scope.launch {
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val shownIds = sharedPrefs.getStringSet(PREF_KEY_SHOWN, emptySet())
                ?.toMutableSet()
                ?: mutableSetOf()
            var initialized = false

            repository.observeNotificationsForCurrentUser().collectLatest { notifications ->
                if (!initialized) {
                    shownIds += notifications.map { it.id }
                    saveShownIds(sharedPrefs, shownIds)
                    initialized = true
                    return@collectLatest
                }

                notifications
                    .filter { !it.read && !shownIds.contains(it.id) }
                    .sortedBy { it.createdAt?.seconds ?: 0L }
                    .forEach { item ->
                        showSystemNotification(context, item.id, item.title, item.message)
                        shownIds += item.id
                    }

                trimSet(shownIds)
                saveShownIds(sharedPrefs, shownIds)
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
    }

    private fun showSystemNotification(
        context: Context,
        notificationId: String,
        title: String,
        message: String
    ) {
        if (!canPostNotifications(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId.hashCode(), notification)
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Notificaciones PokeApi",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos importantes para entrenadores y administradores"
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun saveShownIds(sharedPrefs: android.content.SharedPreferences, ids: Set<String>) {
        sharedPrefs.edit().putStringSet(PREF_KEY_SHOWN, ids).apply()
    }

    private fun trimSet(ids: MutableSet<String>) {
        if (ids.size <= MAX_STORED_IDS) return
        val toRemove = ids.size - MAX_STORED_IDS
        repeat(toRemove) { _ ->
            val key = ids.firstOrNull() ?: UUID.randomUUID().toString()
            ids.remove(key)
        }
    }
}


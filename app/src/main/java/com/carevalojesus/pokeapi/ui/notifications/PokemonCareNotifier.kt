package com.carevalojesus.pokeapi.ui.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carevalojesus.pokeapi.R
import com.carevalojesus.pokeapi.data.firebase.PokemonCareState
import com.carevalojesus.pokeapi.util.PokemonNames

object PokemonCareNotifier {
    private const val CHANNEL_ID = "pokeapi_pokemon_care"
    private const val PREFS_NAME = "pokemon_care_notifier"
    private const val COOLDOWN_MS = 90 * 60 * 1000L

    fun notifyStateIfNeeded(context: Context, state: PokemonCareState) {
        val event = when {
            state.sleeping && state.wantsToWakeUp -> "wake"
            state.hunger <= 25 -> "hunger"
            state.energy <= 20 && !state.sleeping -> "sleep"
            else -> null
        } ?: return

        val key = "${state.pokemonId}_$event"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < COOLDOWN_MS) return
        if (!canPostNotifications(context)) return

        createChannelIfNeeded(context)
        val name = PokemonNames.getName(state.pokemonId)
        val message = when (event) {
            "hunger" -> "$name tiene mucha hambre."
            "sleep" -> "$name tiene sueño."
            else -> "$name quiere despertar."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Cuidado Pokémon")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        prefs.edit().putLong(key, now).apply()
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Cuidado Pokémon",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

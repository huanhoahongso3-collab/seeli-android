package com.tunnaduong.seeli.core

import android.content.Context
import kotlinx.serialization.Serializable
import java.time.Instant

/** What the app shares with its widgets: only words, dates and counts — never the photos. */
@Serializable
data class WidgetSnapshot(
    val loggedIn: Boolean = false,
    /** `revealAt` of every photo that hasn't been reunited yet. */
    val pendingReveals: List<Date> = emptyList(),
    /** Notes the user left, newest first. */
    val words: List<Word> = emptyList(),
) {
    @Serializable
    data class Word(val note: String, val date: Date, val place: String? = null)

    fun dueCount(at: Instant) = pendingReveals.count { it <= at }
    fun sleepingCount(at: Instant) = pendingReveals.count { it > at }

    companion object {
        private const val KEY = "seeli.widget.snapshot"
        private fun prefs(c: Context) = c.getSharedPreferences("seeli.widget", Context.MODE_PRIVATE)

        fun save(c: Context, s: WidgetSnapshot) =
            prefs(c).edit().putString(KEY, ApiClient.json.encodeToString(serializer(), s)).apply()

        fun load(c: Context): WidgetSnapshot = prefs(c).getString(KEY, null)
            ?.let { runCatching { ApiClient.json.decodeFromString(serializer(), it) }.getOrNull() } ?: WidgetSnapshot()
    }
}

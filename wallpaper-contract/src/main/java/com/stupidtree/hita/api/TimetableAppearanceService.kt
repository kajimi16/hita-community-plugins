package com.stupidtree.hita.api

import androidx.lifecycle.LiveData
import java.io.InputStream

/** Native timetable appearance. Settings survive removal of the settings UI plugin. */
data class TimetableAppearance(
    val cardOpacity: Int = 95,
    val blurRadius: Int = 0,
    val scrimOpacity: Int = 20,
    val hasWallpaper: Boolean = false,
    val revision: Long = 0,
) {
    fun normalized() = copy(cardOpacity = cardOpacity.coerceIn(0, 100),
        blurRadius = blurRadius.coerceIn(0, 30), scrimOpacity = scrimOpacity.coerceIn(0, 80))
}

interface TimetableAppearanceService {
    fun observe(): LiveData<TimetableAppearance>
    fun update(settings: TimetableAppearance)
    /** Blocking image validation/copy: call off the UI thread. Caller owns the input stream. */
    fun importWallpaper(input: InputStream)
    /** Blocking: caller closes the returned stream. */
    fun openWallpaper(): InputStream?
    fun clearWallpaper()
    fun reset()
    companion object { val KEY = ServiceKey<TimetableAppearanceService>("hita.timetable.appearance") }
}

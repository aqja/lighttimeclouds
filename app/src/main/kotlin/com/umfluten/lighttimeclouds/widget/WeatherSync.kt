package com.umfluten.lighttimeclouds.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.WeatherRepository
import java.io.IOException
import java.time.Duration

/**
 * Keeps the forecast cache of every place shown by a weather-enabled widget up to date: a
 * WorkManager job every [PERIODIC_INTERVAL] while at least one such widget exists (paused while the
 * battery is low), plus an immediate run after the settings change. Nothing is scheduled (and no
 * network access happens) while no widget shows weather.
 */
object WeatherSync {
    private const val PERIODIC_WORK = "weather_periodic"
    private const val IMMEDIATE_WORK = "weather_now"

    /**
     * Forecasts hardly change from hour to hour, and each download keeps the mobile radio awake for
     * a few seconds, so every two hours is enough (the ring starts to fade after four).
     */
    private val PERIODIC_INTERVAL: Duration = Duration.ofHours(2)

    /** A fetch younger than this is reused; a bit below the interval, so a slightly early run still refreshes. */
    internal val PERIODIC_MAX_AGE: Duration = Duration.ofMinutes(105)
    private val IMMEDIATE_MAX_AGE: Duration = Duration.ofMinutes(15)

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** The background job also waits while the battery is low; the widget keeps the last forecast meanwhile. */
    private val periodicConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Starts or stops the periodic job to match the current widgets; with [refreshNow] also fetches
     * right away (e.g. after weather was switched on or a place moved).
     */
    suspend fun update(context: Context, refreshNow: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (placesNeedingWeather(context).isEmpty()) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            WeatherRepository(context).deleteAllExcept(emptySet())
            return
        }
        val periodic = PeriodicWorkRequestBuilder<WeatherSyncWorker>(PERIODIC_INTERVAL)
            .setConstraints(periodicConstraints)
            .setInputData(workDataOf(WeatherSyncWorker.KEY_MAX_AGE_MINUTES to PERIODIC_MAX_AGE.toMinutes()))
            .build()
        // UPDATE (not KEEP) so an already scheduled job takes over a changed interval or constraints.
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        if (refreshNow) {
            val immediate = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setConstraints(networkConstraint)
                .setInputData(workDataOf(WeatherSyncWorker.KEY_MAX_AGE_MINUTES to IMMEDIATE_MAX_AGE.toMinutes()))
                .build()
            workManager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, immediate)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /** Distinct places of all placed widgets that show the weather ring. */
    internal suspend fun placesNeedingWeather(context: Context): List<Place> =
        WidgetIds.activeWidgetIds(context)
            .map { ClockWidgetProvider.effectiveSettings(context, it) }
            .filter { it.showWeather }
            .map { it.place }
            .distinctBy { it.id }
}

/** Refreshes the forecast of each place in [WeatherSync.placesNeedingWeather] and redraws the widgets if anything changed. */
class WeatherSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val places = WeatherSync.placesNeedingWeather(applicationContext)
        val repository = WeatherRepository(applicationContext)
        repository.deleteAllExcept(places.map { it.id }.toSet())
        // Covers widgets removed while the job was scheduled (onDeleted may still see them as placed).
        if (places.isEmpty()) {
            WeatherSync.cancel(applicationContext)
            return Result.success()
        }
        val maxAge = Duration.ofMinutes(inputData.getLong(KEY_MAX_AGE_MINUTES, WeatherSync.PERIODIC_MAX_AGE.toMinutes()))

        var changed = false
        var failed = false
        for (place in places) {
            try {
                changed = repository.refresh(place, maxAge) || changed
            } catch (e: IOException) {
                Log.w(TAG, "Forecast download for place ${place.id} failed", e)
                failed = true
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unusable forecast for place ${place.id}", e)
                failed = true
            } catch (e: SecurityException) {
                // Network access withheld by the user; retrying will not help until it is granted.
                Log.w(TAG, "No network permission", e)
                return Result.success()
            }
        }
        if (changed) ClockWidgetProvider.updateAll(applicationContext)
        // Widgets keep showing the last forecast (fading as it ages), so a few retries are enough.
        return if (failed && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_MAX_AGE_MINUTES = "max_age_minutes"
        private const val TAG = "WeatherSync"
        private const val MAX_RETRIES = 3
    }
}

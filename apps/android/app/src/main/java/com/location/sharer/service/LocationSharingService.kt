package com.location.sharer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.location.sharer.BuildConfig
import com.location.sharer.MainActivity
import com.location.sharer.data.SharingStatusStore
import com.location.sharer.permissions.AppPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.resume

class LocationSharingService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var locationManager: LocationManager
  private var uploadJob: Job? = null
  private var stopRequested = false

  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannel()
    locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        stopRequested = false
        SharingStatusStore.markServiceStarting(this)
        Log.i(TAG, "Foreground location sharing started.")
        startForeground(NOTIFICATION_ID, buildNotification("Preparing first upload"))
        startUploadLoop()
      }

      ACTION_STOP -> stopSharing("Sharing stopped by the user.")
    }

    return START_STICKY
  }

  override fun onDestroy() {
    if (!stopRequested && uploadJob != null) {
      SharingStatusStore.markFailure(
        context = this,
        sharingActive = false,
        statusLabel = "Sharing paused",
        note = "Foreground service stopped. Open the app and tap Start Sharing again."
      )
      Log.w(TAG, "Foreground location sharing stopped unexpectedly.")
    }

    uploadJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startUploadLoop() {
    if (uploadJob?.isActive == true) {
      return
    }

    uploadJob = serviceScope.launch {
      while (isActive) {
        val notificationText = try {
          collectAndUploadOnce()
        } catch (error: Exception) {
          val safeMessage = error.message ?: error::class.java.simpleName
          Log.e(TAG, "Upload loop failed.", error)

          SharingStatusStore.markFailure(
            context = this@LocationSharingService,
            sharingActive = true,
            statusLabel = "Upload failed",
            note = "Service is still running, but the last upload failed: $safeMessage"
          )

          "Upload failed"
        }

        refreshNotification(notificationText)
        delay(BuildConfig.LOCATION_UPLOAD_INTERVAL_MS)
      }
    }
  }

  private suspend fun collectAndUploadOnce(): String {
    if (!AppPermissions.hasLocationPermission(this)) {
      SharingStatusStore.markFailure(
        context = this,
        sharingActive = false,
        statusLabel = "Permission required",
        note = "Grant location permission and start sharing again."
      )
      stopSharing(
        note = "Location permission is missing.",
        persistStoppedState = false
      )

      return "Permission required"
    }

    val location = currentLocation()

    if (location == null) {
      SharingStatusStore.markFailure(
        context = this,
        sharingActive = true,
        statusLabel = "Waiting for GPS fix",
        note = "Foreground service is active, but Android has not returned a recent location yet. Keep location enabled and try again near a window or outdoors."
      )
      Log.w(TAG, "Android did not return a recent location.")

      return "Waiting for GPS"
    }

    val capturedAt = Instant.ofEpochMilli(
      location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
    ).toString()
    val batteryInfo = readBatteryInfo()

    uploadLocation(
      latitude = location.latitude,
      longitude = location.longitude,
      accuracyMeters = location.accuracy.toDouble(),
      capturedAt = capturedAt,
      batteryPercent = batteryInfo.percent,
      isCharging = batteryInfo.isCharging,
      speedMps = location.speed.takeIf { location.hasSpeed() }?.toDouble()
    )

    SharingStatusStore.markUploadSuccess(
      context = this,
      capturedAt = capturedAt,
      latitude = location.latitude,
      longitude = location.longitude,
      accuracyMeters = location.accuracy.toDouble(),
      batteryPercent = batteryInfo.percent,
      note = "Real location uploaded through the web API. Next upload will run automatically."
    )
    Log.i(
      TAG,
      "Uploaded location lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m."
    )

    return "Last sent ${capturedAt.replace("T", " ").removeSuffix("Z")} UTC"
  }

  @SuppressLint("MissingPermission")
  private suspend fun currentLocation(): Location? {
    val providers = mutableListOf<String>()
    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      providers.add(LocationManager.GPS_PROVIDER)
    }
    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
      providers.add(LocationManager.NETWORK_PROVIDER)
    }
    Log.i(TAG, "Available location providers: $providers")

    // 1. Try getLastKnownLocation first (instant, no callback needed)
    var bestLocation: Location? = null
    for (provider in providers) {
      val loc = runCatching {
        locationManager.getLastKnownLocation(provider)
      }.onFailure { e ->
        Log.w(TAG, "getLastKnownLocation($provider) failed.", e)
      }.getOrNull() ?: continue

      Log.i(TAG, "lastKnownLocation from $provider: lat=${loc.latitude}, lng=${loc.longitude}, age=${System.currentTimeMillis() - loc.time}ms")

      if (bestLocation == null || loc.time > bestLocation.time) {
        bestLocation = loc
      }
    }

    // 2. If we have a recent location, use it immediately
    if (bestLocation != null && isRecentEnough(bestLocation)) {
      Log.i(TAG, "Using recent lastKnownLocation")
      return bestLocation
    }

    // 3. Request a fresh location update with timeout
    val freshLocation = requestFreshLocation(providers)
    if (freshLocation != null) {
      return freshLocation
    }

    // 4. Fall back to stale location if available (better than nothing)
    if (bestLocation != null) {
      Log.i(TAG, "Using stale lastKnownLocation (age=${System.currentTimeMillis() - bestLocation.time}ms)")
      return bestLocation
    }

    return null
  }

  @SuppressLint("MissingPermission")
  private suspend fun requestFreshLocation(providers: List<String>): Location? {
    if (providers.isEmpty()) return null

    return suspendCancellableCoroutine { continuation ->
      val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
      val listeners = mutableListOf<LocationListener>()

      val callback = { location: Location ->
        if (resumed.compareAndSet(false, true)) {
          Log.i(TAG, "Got fresh location: lat=${location.latitude}, lng=${location.longitude}")
          // Remove all listeners on main thread
          val listenersToRemove = listeners.toList()
          android.os.Handler(Looper.getMainLooper()).post {
            for (l in listenersToRemove) {
              runCatching { locationManager.removeUpdates(l) }
            }
          }
          continuation.resume(location)
        }
      }

      for (provider in providers) {
        val listener = object : LocationListener {
          override fun onLocationChanged(location: Location) { callback(location) }
          @Deprecated("Deprecated in API")
          override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
          override fun onProviderEnabled(p: String) {}
          override fun onProviderDisabled(p: String) {}
        }
        listeners.add(listener)

        try {
          locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
          Log.i(TAG, "Requested location updates from $provider")
        } catch (e: Exception) {
          Log.w(TAG, "requestLocationUpdates($provider) failed.", e)
        }
      }

      // If no listeners were registered, return null immediately
      if (listeners.isEmpty()) {
        if (resumed.compareAndSet(false, true)) {
          continuation.resume(null)
        }
        return@suspendCancellableCoroutine
      }

      continuation.invokeOnCancellation {
        resumed.set(true)
        android.os.Handler(Looper.getMainLooper()).post {
          for (l in listeners) {
            runCatching { locationManager.removeUpdates(l) }
          }
        }
      }

      // Timeout
      android.os.Handler(Looper.getMainLooper()).postDelayed({
        if (resumed.compareAndSet(false, true)) {
          Log.w(TAG, "Fresh location request timed out after ${CURRENT_LOCATION_TIMEOUT_MS}ms")
          for (l in listeners) {
            runCatching { locationManager.removeUpdates(l) }
          }
          continuation.resume(null)
        }
      }, CURRENT_LOCATION_TIMEOUT_MS)
    }
  }

  private fun isRecentEnough(location: Location): Boolean {
    val capturedAtMillis = location.time

    if (capturedAtMillis <= 0L) {
      return true
    }

    return System.currentTimeMillis() - capturedAtMillis <= MAX_LOCATION_AGE_MS
  }

  private fun uploadLocation(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
    capturedAt: String,
    batteryPercent: Int?,
    isCharging: Boolean?,
    speedMps: Double?
  ) {
    val endpoint = "${BuildConfig.LOCATION_API_BASE_URL.trimEnd('/')}/api/location"
    Log.i(TAG, "Posting location update to $endpoint for ${BuildConfig.LOCATION_DEVICE_ID}.")
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = 15_000
      readTimeout = 15_000
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("X-Device-Token", BuildConfig.LOCATION_DEVICE_TOKEN)
    }

    try {
      val body = JSONObject().apply {
        put("deviceId", BuildConfig.LOCATION_DEVICE_ID)
        put("lat", latitude)
        put("lng", longitude)
        put("accuracyMeters", accuracyMeters)
        put("capturedAt", capturedAt)
        put("batteryPercent", batteryPercent ?: JSONObject.NULL)
        put("isCharging", isCharging ?: JSONObject.NULL)
        put("speedMps", speedMps ?: JSONObject.NULL)
      }

      connection.outputStream.use { stream ->
        stream.write(body.toString().toByteArray(Charsets.UTF_8))
      }

      val responseCode = connection.responseCode

      if (responseCode !in 200..299) {
        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
          ?: "HTTP $responseCode"
        Log.e(TAG, "Location upload returned HTTP $responseCode: $errorBody")
        throw IllegalStateException(errorBody)
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun refreshNotification(contentText: String) {
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIFICATION_ID, buildNotification(contentText))
  }

  private fun buildNotification(contentText: String): Notification {
    val openAppIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Location sharing active")
      .setContentText(contentText)
      .setSmallIcon(android.R.drawable.ic_menu_mylocation)
      .setContentIntent(openAppIntent)
      .setOngoing(true)
      .build()
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val manager = getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Location sharing",
      NotificationManager.IMPORTANCE_LOW
    )

    manager.createNotificationChannel(channel)
  }

  private fun stopSharing(note: String, persistStoppedState: Boolean = true) {
    stopRequested = true
    uploadJob?.cancel()
    uploadJob = null

    if (persistStoppedState) {
      SharingStatusStore.markServiceStopped(this, note)
    }

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun readBatteryInfo(): BatteryInfo {
    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) {
      (level * 100) / scale
    } else {
      null
    }
    val isCharging = when (status) {
      BatteryManager.BATTERY_STATUS_CHARGING,
      BatteryManager.BATTERY_STATUS_FULL -> true

      BatteryManager.BATTERY_STATUS_DISCHARGING,
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false

      else -> null
    }

    return BatteryInfo(percent = percent, isCharging = isCharging)
  }

  data class BatteryInfo(
    val percent: Int?,
    val isCharging: Boolean?
  )

  companion object {
    private const val TAG = "LocationSharingService"
    private const val CHANNEL_ID = "location-sharing"
    private const val NOTIFICATION_ID = 1001
    private const val ACTION_START = "com.location.sharer.action.START"
    private const val ACTION_STOP = "com.location.sharer.action.STOP"
    private const val CURRENT_LOCATION_TIMEOUT_MS = 15_000L
    private const val MAX_LOCATION_AGE_MS = 2 * 60 * 1000L

    fun start(context: Context) {
      val intent = Intent(context, LocationSharingService::class.java).apply {
        action = ACTION_START
      }

      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, LocationSharingService::class.java).apply {
        action = ACTION_STOP
      }

      context.startService(intent)
    }
  }
}

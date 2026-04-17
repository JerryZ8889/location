package com.location.sharer

import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.location.sharer.data.SharingStatusStore
import com.location.sharer.model.SharingUiState
import com.location.sharer.permissions.AppPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val appContext = application.applicationContext
  private val preferences = SharingStatusStore.preferences(appContext)
  private val _uiState = MutableStateFlow(SharingUiState())
  val uiState: StateFlow<SharingUiState> = _uiState.asStateFlow()

  private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
    refreshFromDevice()
  }

  init {
    preferences.registerOnSharedPreferenceChangeListener(listener)
    refreshFromDevice()
  }

  override fun onCleared() {
    preferences.unregisterOnSharedPreferenceChangeListener(listener)
    super.onCleared()
  }

  fun refreshFromDevice() {
    val persisted = SharingStatusStore.read(appContext)

    _uiState.value = SharingUiState(
      deviceLabel = buildDeviceLabel(),
      deviceId = BuildConfig.LOCATION_DEVICE_ID,
      pairedViewerName = "Temporary device-token upload mode",
      shareStatusLabel = persisted.shareStatusLabel,
      sharingActive = persisted.sharingActive,
      lastUploadedAt = persisted.lastUploadedAt,
      lastKnownLatitude = persisted.lastKnownLatitude,
      lastKnownLongitude = persisted.lastKnownLongitude,
      lastAccuracyMeters = persisted.lastAccuracyMeters,
      batteryPercent = persisted.batteryPercent,
      permissionSummary = permissionSummary(),
      backgroundPermissionSummary = backgroundPermissionSummary(),
      showOpenSettingsButton = !AppPermissions.hasBackgroundLocationPermission(appContext),
      apiBaseUrl = BuildConfig.LOCATION_API_BASE_URL,
      note = persisted.note
    )
  }

  fun onPermissionRequestStarted() {
    _uiState.value = _uiState.value.copy(
      note = "Waiting for Android runtime permission result."
    )
  }

  fun onPermissionRequestDenied() {
    refreshFromDevice()
    _uiState.value = _uiState.value.copy(
      shareStatusLabel = "Permission required",
      note = "Grant location permission before the foreground service can upload real data."
    )
  }

  fun onBackgroundPermissionSettingsRequired() {
    refreshFromDevice()
    _uiState.value = _uiState.value.copy(
      shareStatusLabel = "Need background location",
      note = "Open system settings and change location permission to Allow all the time, then return to the app."
    )
  }

  fun onBackgroundPermissionDenied() {
    refreshFromDevice()
    _uiState.value = _uiState.value.copy(
      shareStatusLabel = "Background location not granted",
      note = "This build expects background location access for continuous sharing. Open settings and switch to Allow all the time."
    )
  }

  private fun buildDeviceLabel(): String {
    val manufacturer = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()

    return listOf(manufacturer, model)
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .ifBlank { "Android Sharer Device" }
  }

  private fun permissionSummary(): String {
    val hasLocation = AppPermissions.hasLocationPermission(appContext)
    val hasNotifications = AppPermissions.hasNotificationPermission(appContext)

    return when {
      hasLocation && hasNotifications -> "Location and notification permission granted."
      hasLocation -> "Location granted. Notification permission is still missing or not required."
      else -> "Location permission is required before sharing can start."
    }
  }

  private fun backgroundPermissionSummary(): String =
    when {
      AppPermissions.hasBackgroundLocationPermission(appContext) ->
        "Background location is enabled."
      AppPermissions.backgroundPermissionRequiresSettingsRedirect() ->
        "Open Android app settings and change location access to Allow all the time."
      else ->
        "Background location still needs to be granted."
    }
}

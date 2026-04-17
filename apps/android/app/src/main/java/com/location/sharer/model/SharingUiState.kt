package com.location.sharer.model

data class SharingUiState(
  val deviceLabel: String = "Android Sharer Device",
  val deviceId: String = "demo-tokyo-android",
  val pairedViewerName: String = "Temporary internal upload-token mode",
  val shareStatusLabel: String = "Ready to request permission and start sharing",
  val sharingActive: Boolean = false,
  val lastUploadedAt: String? = null,
  val lastKnownLatitude: Double? = null,
  val lastKnownLongitude: Double? = null,
  val lastAccuracyMeters: Double? = null,
  val batteryPercent: Int? = null,
  val permissionSummary: String = "Location and notification permission have not been checked yet.",
  val backgroundPermissionSummary: String = "Background location permission has not been checked yet.",
  val showOpenSettingsButton: Boolean = false,
  val apiBaseUrl: String = "http://10.0.2.2:3000",
  val note: String = "Grant permissions and start sharing to upload real location data."
)

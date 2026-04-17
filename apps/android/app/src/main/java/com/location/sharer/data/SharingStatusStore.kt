package com.location.sharer.data

import android.content.Context
import androidx.core.content.edit

data class PersistedSharingStatus(
  val sharingActive: Boolean,
  val shareStatusLabel: String,
  val lastUploadedAt: String?,
  val lastKnownLatitude: Double?,
  val lastKnownLongitude: Double?,
  val lastAccuracyMeters: Double?,
  val batteryPercent: Int?,
  val note: String
)

object SharingStatusStore {
  private const val PREFS_NAME = "sharing_status"
  private const val KEY_SHARING_ACTIVE = "sharing_active"
  private const val KEY_SHARE_STATUS_LABEL = "share_status_label"
  private const val KEY_LAST_UPLOADED_AT = "last_uploaded_at"
  private const val KEY_LAST_LATITUDE = "last_latitude"
  private const val KEY_LAST_LONGITUDE = "last_longitude"
  private const val KEY_LAST_ACCURACY_METERS = "last_accuracy_meters"
  private const val KEY_BATTERY_PERCENT = "battery_percent"
  private const val KEY_NOTE = "note"
  private const val DEFAULT_STATUS = "Ready to request permission and start sharing"
  private const val DEFAULT_NOTE =
    "Configure the API base URL, grant permissions, and start the foreground service."

  fun preferences(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun read(context: Context): PersistedSharingStatus {
    val prefs = preferences(context)

    return PersistedSharingStatus(
      sharingActive = prefs.getBoolean(KEY_SHARING_ACTIVE, false),
      shareStatusLabel = prefs.getString(KEY_SHARE_STATUS_LABEL, DEFAULT_STATUS) ?: DEFAULT_STATUS,
      lastUploadedAt = prefs.getString(KEY_LAST_UPLOADED_AT, null),
      lastKnownLatitude = prefs.takeIf { it.contains(KEY_LAST_LATITUDE) }
        ?.getFloat(KEY_LAST_LATITUDE, 0f)
        ?.toDouble(),
      lastKnownLongitude = prefs.takeIf { it.contains(KEY_LAST_LONGITUDE) }
        ?.getFloat(KEY_LAST_LONGITUDE, 0f)
        ?.toDouble(),
      lastAccuracyMeters = prefs.takeIf { it.contains(KEY_LAST_ACCURACY_METERS) }
        ?.getFloat(KEY_LAST_ACCURACY_METERS, 0f)
        ?.toDouble(),
      batteryPercent = prefs.takeIf { it.contains(KEY_BATTERY_PERCENT) }
        ?.getInt(KEY_BATTERY_PERCENT, 0),
      note = prefs.getString(KEY_NOTE, DEFAULT_NOTE) ?: DEFAULT_NOTE
    )
  }

  fun markServiceStarting(context: Context) {
    preferences(context).edit {
      putBoolean(KEY_SHARING_ACTIVE, true)
      putString(KEY_SHARE_STATUS_LABEL, "Foreground sharing active")
      putString(KEY_NOTE, "Collecting the current location and uploading it to the web API.")
    }
  }

  fun markServiceStopped(context: Context, note: String) {
    preferences(context).edit {
      putBoolean(KEY_SHARING_ACTIVE, false)
      putString(KEY_SHARE_STATUS_LABEL, "Sharing paused")
      putString(KEY_NOTE, note)
    }
  }

  fun markUploadSuccess(
    context: Context,
    capturedAt: String,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
    batteryPercent: Int?,
    note: String
  ) {
    preferences(context).edit {
      putBoolean(KEY_SHARING_ACTIVE, true)
      putString(KEY_SHARE_STATUS_LABEL, "Last upload succeeded")
      putString(KEY_LAST_UPLOADED_AT, capturedAt)
      putFloat(KEY_LAST_LATITUDE, latitude.toFloat())
      putFloat(KEY_LAST_LONGITUDE, longitude.toFloat())
      putFloat(KEY_LAST_ACCURACY_METERS, accuracyMeters.toFloat())
      if (batteryPercent != null) {
        putInt(KEY_BATTERY_PERCENT, batteryPercent)
      } else {
        remove(KEY_BATTERY_PERCENT)
      }
      putString(KEY_NOTE, note)
    }
  }

  fun markFailure(
    context: Context,
    sharingActive: Boolean,
    statusLabel: String,
    note: String
  ) {
    preferences(context).edit {
      putBoolean(KEY_SHARING_ACTIVE, sharingActive)
      putString(KEY_SHARE_STATUS_LABEL, statusLabel)
      putString(KEY_NOTE, note)
    }
  }
}

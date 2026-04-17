package com.location.sharer.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AppPermissions {
  fun missingForegroundRuntimePermissions(context: Context): List<String> {
    val missing = mutableListOf<String>()

    if (!hasLocationPermission(context)) {
      missing += Manifest.permission.ACCESS_FINE_LOCATION
      missing += Manifest.permission.ACCESS_COARSE_LOCATION
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !hasNotificationPermission(context)
    ) {
      missing += Manifest.permission.POST_NOTIFICATIONS
    }

    return missing.distinct()
  }

  fun hasBackgroundLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
      ) == PackageManager.PERMISSION_GRANTED

  fun backgroundPermissionRequiresSettingsRedirect(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

  fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED

  fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
}

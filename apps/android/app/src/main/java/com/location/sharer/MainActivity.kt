package com.location.sharer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.location.sharer.permissions.AppPermissions
import com.location.sharer.service.LocationSharingService
import com.location.sharer.ui.LocationSharerApp

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()
  private var pendingStartAfterPermissionFlow = false
  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
      viewModel.refreshFromDevice()

      if (AppPermissions.hasLocationPermission(this)) {
        continueStartSharing()
      } else {
        pendingStartAfterPermissionFlow = false
        viewModel.onPermissionRequestDenied()
      }
    }
  private val backgroundPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.refreshFromDevice()

      if (granted || AppPermissions.hasBackgroundLocationPermission(this)) {
        startSharingService()
      } else {
        pendingStartAfterPermissionFlow = false
        viewModel.onBackgroundPermissionDenied()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    viewModel.refreshFromDevice()

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      LocationSharerApp(
        state = uiState,
        onStartSharing = {
          pendingStartAfterPermissionFlow = true
          val missingPermissions = AppPermissions.missingForegroundRuntimePermissions(this)

          if (missingPermissions.isEmpty()) {
            continueStartSharing()
          } else {
            viewModel.onPermissionRequestStarted()
            permissionLauncher.launch(missingPermissions.toTypedArray())
          }
        },
        onStopSharing = {
          pendingStartAfterPermissionFlow = false
          LocationSharingService.stop(this)
          viewModel.refreshFromDevice()
        },
        onOpenAppSettings = {
          openAppSettingsForBackgroundLocation()
        }
      )
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshFromDevice()

    if (
      pendingStartAfterPermissionFlow &&
      AppPermissions.hasLocationPermission(this) &&
      AppPermissions.hasBackgroundLocationPermission(this)
    ) {
      startSharingService()
    }
  }

  private fun continueStartSharing() {
    if (AppPermissions.hasBackgroundLocationPermission(this)) {
      startSharingService()
      return
    }

    if (AppPermissions.backgroundPermissionRequiresSettingsRedirect()) {
      viewModel.onBackgroundPermissionSettingsRequired()
      openAppSettingsForBackgroundLocation()
      return
    }

    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
  }

  private fun startSharingService() {
    pendingStartAfterPermissionFlow = false
    LocationSharingService.start(this)
    viewModel.refreshFromDevice()
  }

  private fun openAppSettingsForBackgroundLocation() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", packageName, null)
    )
    startActivity(intent)
  }
}

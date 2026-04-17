package com.location.sharer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.location.sharer.model.SharingUiState

@Composable
fun SharingHomeScreen(
  state: SharingUiState,
  onStartSharing: () -> Unit,
  onStopSharing: () -> Unit,
  onOpenAppSettings: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Location Sharer",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold
    )

    Text(
      text = "Internal MVP uploader. This build uses a device token and talks to the web API directly through the backend route.",
      style = MaterialTheme.typography.bodyMedium
    )

    InfoCard(title = "Device", value = state.deviceLabel)
    InfoCard(title = "Device ID", value = state.deviceId)
    InfoCard(title = "Viewer", value = state.pairedViewerName)
    InfoCard(title = "Status", value = state.shareStatusLabel)
    InfoCard(title = "Permissions", value = state.permissionSummary)
    InfoCard(title = "Background Access", value = state.backgroundPermissionSummary)
    InfoCard(title = "API Base URL", value = state.apiBaseUrl)
    InfoCard(title = "Last Upload", value = state.lastUploadedAt ?: "--")
    InfoCard(
      title = "Latest Coordinates",
      value = if (state.lastKnownLatitude != null && state.lastKnownLongitude != null) {
        "${state.lastKnownLatitude}, ${state.lastKnownLongitude}"
      } else {
        "--"
      }
    )
    InfoCard(
      title = "Accuracy",
      value = state.lastAccuracyMeters?.let { "${"%.1f".format(it)} m" } ?: "--"
    )
    InfoCard(
      title = "Battery",
      value = state.batteryPercent?.let { "$it%" } ?: "--"
    )
    InfoCard(title = "Implementation Note", value = state.note)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Button(
        onClick = onStartSharing,
        enabled = !state.sharingActive,
        modifier = Modifier.weight(1f)
      ) {
        Text("Start Sharing")
      }

      OutlinedButton(
        onClick = onStopSharing,
        enabled = state.sharingActive,
        modifier = Modifier.weight(1f)
      ) {
        Text("Stop Sharing")
      }
    }

    if (state.showOpenSettingsButton) {
      OutlinedButton(
        onClick = onOpenAppSettings,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Open App Settings")
      }
    }

    Spacer(modifier = Modifier.height(12.dp))
  }
}

@Composable
private fun InfoCard(title: String, value: String) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

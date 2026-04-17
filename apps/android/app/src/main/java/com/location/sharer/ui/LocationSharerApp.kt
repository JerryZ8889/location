package com.location.sharer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.location.sharer.model.SharingUiState

@Composable
fun LocationSharerApp(
  state: SharingUiState,
  onStartSharing: () -> Unit,
  onStopSharing: () -> Unit,
  onOpenAppSettings: () -> Unit
) {
  MaterialTheme {
    Surface {
      SharingHomeScreen(
        state = state,
        onStartSharing = onStartSharing,
        onStopSharing = onStopSharing,
        onOpenAppSettings = onOpenAppSettings
      )
    }
  }
}

# Android Sharer App

This is the Android app that the consented sharer installs.

Current skeleton includes:

- Gradle project files
- Compose-based main screen
- foreground service that requests the current location and uploads it on an interval
- manifest permissions, notification channel setup, and internal MVP upload-token flow

Immediate next steps:

1. Add real sign-in and pairing flow.
2. Replace the temporary device-token bootstrap with proper device registration.
3. Add local persistence and retry behavior.
4. Harden the service lifecycle and stale-state UX.
5. Add automated tests around permissions, upload formatting, and failure handling.

## Internal MVP Configuration

The current Android build reads optional overrides from `apps/android/local.properties`.

Example keys are in `apps/android/local.properties.example`:

- `location.apiBaseUrl`
- `location.deviceId`
- `location.deviceToken`
- `location.uploadIntervalMs`

Defaults are set for emulator-style local testing and the seeded demo device:

- `location.apiBaseUrl=http://10.0.2.2:3000`
- `location.deviceId=demo-tokyo-android`
- `location.deviceToken=demo-token-tokyo`

For a physical phone on the same Wi-Fi, set `location.apiBaseUrl` to your computer's LAN URL, for example `http://192.168.x.x:3000`.

## Background Location Permission

This build now declares `ACCESS_BACKGROUND_LOCATION`.

On Android 11 and newer, the system usually does not grant "Allow all the time" from the first runtime dialog. The app will redirect you to system settings, and you should manually change location access to:

- `Allow all the time`

If the app screen shows a missing background-permission warning, use the in-app `Open App Settings` button and then return to the app.

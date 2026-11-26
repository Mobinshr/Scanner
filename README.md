# Scanner

A native Android QR code scanner built with Kotlin, Jetpack Compose, Material 3, CameraX and Google ML Kit.

## App details

- **Application ID:** `com.mobin.scanner`
- **Minimum Android:** Android 10 (API 29)
- **Target SDK:** 35
- **UI:** Jetpack Compose + Material 3
- **Camera:** CameraX
- **QR recognition:** Google ML Kit Barcode Scanning

## Features

- Live QR scanning using the rear camera
- Scan QR codes from images selected through the system photo picker
- Flashlight toggle
- Result preview with copy and share actions
- Open detected web links
- Local scan history (up to 100 recent results)
- System light and dark theme support
- Persian right-to-left interface

## Build

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21 and Gradle 8.9.

Run:

```bash
gradle :app:assembleDebug
```

## GitHub Actions signing

The workflow expects the repository Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64`. It must contain the same stable debug keystore used by the other Mobinshr Android apps. The workflow verifies the signing certificate before publishing the APK.

The latest APK is published at the stable `latest` release.

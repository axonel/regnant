# Regnant Android Application

## 1. Overview

Regnant Android is a native Kotlin mobile client built with Jetpack Compose. It serves as the personal mobile control plane for the user's Omarchy Linux computer, communicating entirely peer-to-peer over local Wi-Fi with zero external cloud relays.

## 2. Technical Architecture

- **Language**: Kotlin 2.0.21
- **Target OS**: Android 16 (API Level 36, Baklava)
- **Minimum OS**: Android 10 (API Level 29)
- **UI Toolkit**: Jetpack Compose (Material 3 with custom Regnant design tokens)
- **Cryptographic Storage**: Android Keystore (`EncryptedSharedPreferences` backed by AES-256-GCM MasterKeys)
- **Local Service Discovery**: `android.net.nsd.NsdManager` for DNS-SD (`_regnant._tcp`)
- **Networking**: OkHttp 4.12.0 with custom `X509TrustManager` pinning the host's TLS certificate SHA-256 fingerprint
- **Voice Recognition**: Push-to-talk using Android's native `SpeechRecognizer` (checking `SpeechRecognizer.isOnDeviceRecognitionAvailable` and requesting offline models via `EXTRA_PREFER_OFFLINE`)
- **Background Persistence**: `RegnantForegroundService` declared with `connectedDevice` foreground service type

## 3. Visual Design & Theme

Adheres strictly to the Regnant design system:
- **Main Background**: `#0C0F14`
- **Primary Text (Candle)**: `#EFE9D7`
- **Primary Action (Coral)**: `#7D2826`
- **Secondary Accent (Leaf)**: `#898861`
- **Containers / Subtle Surfaces (Juniper)**: `#343723`

The application icon is an original geometric silhouette representing a sovereign crown and directional command vector.

## 4. Key Screens

1. **Home Screen**:
   - Connection status pill (Connected / Reconnecting / Offline).
   - Quick Action chips: `Terminal`, `Brave`, `Files`, `Calculator`.
   - Command text field with send action.
   - Push-to-talk microphone button.
   - Live activity feed showing recent command execution statuses with timestamps.
2. **Devices Screen**:
   - Details of the paired workstation (Host name, Host ID, IP address, Port, TLS fingerprint, Last seen).
   - "Forget / Revoke Host" action.
   - Manual host IP & Port fallback inputs for router client-isolation or USB tethering.
   - Discovered mDNS hosts list.
3. **Activity Screen**:
   - Complete historical log of submitted commands with status badges (`COMPLETED`, `FAILED`, `REJECTED`, `RUNNING`) and result messages.
4. **Settings Screen**:
   - Automatic reconnection toggle.
   - Persistent foreground service toggle.
   - Notification preferences toggle.
   - About Regnant architecture summary.

## 5. Build and Installation Instructions

### Prerequisites
- JDK 21 (Temurin OpenJDK 21)
- Android SDK with platform `android-36` and build-tools `36.0.0` or `34.0.0`

### Build Debug APK
```bash
cd android
./gradlew assembleDebug
```
The compiled APK is placed at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Install onto Device via ADB
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.regnant.android/.MainActivity
```

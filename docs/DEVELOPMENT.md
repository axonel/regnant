# Development Guide

## Toolchain

- Node.js 26 with npm for the Linux host service.
- JDK 21 and Android SDK API 36 for the Android application.
- A Linux desktop session for platform-specific integration work.

## Host

```bash
cd linux/host
npm ci
npm test
```

The package scripts currently expose a single test command using Node's built-in test runner.

## Android

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

The Android test task may report `NO-SOURCE` because no test source files are currently present. The debug APK build is the meaningful current Android build check.

## Browser extension

The browser companion is under `linux/browser-extension`. It uses Manifest V3. Installation and runtime verification must be performed in a compatible Chromium-based browser; do not treat a manifest parse check as end-to-end verification.

## Pull requests

Use focused commits, run the relevant tests, update documentation and describe any manual checks. Never include local runtime state, credentials or generated build output.

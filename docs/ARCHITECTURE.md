# Architecture

Regnant is a local-first control plane with an Android client and a Linux host service. The Android app initiates authenticated requests; the host validates capabilities and dispatches supported operations into the logged-in desktop session.

## Components

- **Android client** (`android/`): Kotlin, Jetpack Compose, OkHttp TLS/WebSocket client, Android NSD discovery and encrypted credential storage.
- **Linux host** (`linux/host/`): Node.js HTTPS/WSS API, host-side capability checks, SQLite persistence and operation-specific managers.
- **Linux desktop companion** (`linux/desktop/`): Electron interface for host-side pairing and service controls.
- **Browser companion** (`linux/browser-extension/`): Chromium Manifest V3 extension intended to communicate with the host-side browser bridge.
- **Shared protocol** (`shared/protocol.js`): Shared protocol constants and command definitions.

## Request lifecycle

1. Android discovers or selects a host.
2. Initial pairing uses a short-lived code presented by the host.
3. The client stores host identity and pairing credentials locally.
4. Requests use the pinned host certificate and bearer-token authentication.
5. The host validates the paired device and requested capability.
6. A dedicated manager performs the operation and returns a result or emits an event.

## Platform integration

Several managers depend on the active user session: Hyprland IPC, Wayland utilities, PipeWire/MPRIS, Quickshell and Omarchy. These are not portable Linux APIs. Unsupported environments must be treated as unsupported rather than silently emulated.

## Diagrams

- [System architecture](diagrams/system-architecture.mmd)
- [Pairing and authenticated connection](diagrams/pairing-and-authentication.mmd)
- [Command execution](diagrams/command-execution-flow.mmd)
- [Host lifecycle](diagrams/host-service-lifecycle.mmd)
- [Security boundaries](diagrams/security-boundaries.mmd)

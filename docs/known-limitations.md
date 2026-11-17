# Regnant — Environmental Context & Known Limitations

## 1. Network Topology & Router Client Isolation

### Context
In shared corporate, university, or cellular carrier Wi-Fi networks (such as RFC 6598 CGNAT subnets `100.64.0.0/10`), wireless access points often enforce "Client Isolation" or "AP Isolation". Under this configuration, peer-to-peer ICMP and TCP/UDP packets between Wi-Fi clients connected to the same SSID are dropped by the router hardware.

### Implemented Alternatives & Fallbacks
1. **Manual IP Fallback**: Regnant allows the user to manually enter an alternate host address and port in the Devices screen.
2. **USB Development Tunneling**: When connected via USB with ADB debugging enabled, running `adb reverse tcp:8765 tcp:8765` forwards `127.0.0.1:8765` on Android directly to the host service on the laptop, ensuring 100% reliable local development and troubleshooting regardless of router policies.
3. **Home & Unrestricted Networks**: When connected to standard home Wi-Fi routers where client isolation is disabled, DNS-SD / mDNS service discovery and direct station-to-station TLS connections operate seamlessly out of the box.

## 2. IPv6 Link-Local Scope Identifiers

### Context
Avahi and mDNS stacks advertise both IPv4 and IPv6 link-local addresses (`fe80::...%interface`). In Java/Kotlin URL parsers (including OkHttp's `HttpUrl`), interface scope specifiers (`%wlan0`) are treated as invalid characters within HTTP/WS URLs.

### Mitigation
Regnant's `NsdDiscovery` prioritizes IPv4 network interfaces (`java.net.Inet4Address`) and strips interface scope suffixes while wrapping raw IPv6 addresses into standard RFC 3986 bracketed notation (`[fe80::...]`).

## 3. Android OEM Background Restrictions

### Context
Modern Android versions (especially Android 14, 15, and 16) and aggressive vendor battery managers (e.g. Vivo FuntouchOS, Xiaomi MIUI, Samsung OneUI) terminate background network sockets if an application remains idle in the background without a persistent foreground service.

### Design Decision
Regnant does not falsely promise an unbreakable background socket without user consent. Instead:
1. It provides a configurable **Persistent Foreground Service** toggle in Settings using the official Android `connectedDevice` foreground service type, displaying an ongoing status notification with a quick "Disconnect" action.
2. It handles application pause/resume gracefully: whenever the app is brought back to the foreground, `RegnantClient` automatically initiates instant reconnection to the paired workstation.

## 4. Laptop Suspension & Lock Screen

### Context
When the Linux laptop enters system suspend / S3 sleep, all user session processes and network interfaces are halted by the kernel.

### Design Decision
Commands are never silently queued with false success promises. If the host daemon is unreachable because the computer is asleep or powered off, the Android client immediately marks the host `OFFLINE` and alerts the user.

## 5. Speech Recognition Availability

### Context
Android's `SpeechRecognizer.isOnDeviceRecognitionAvailable()` depends on whether the user's installed Google Speech Services or OEM speech engine has downloaded an offline language model pack.

### Mitigation
Regnant checks `SpeechRecognizer.isRecognitionAvailable(context)` before initializing voice input, flags `RecognizerIntent.EXTRA_PREFER_OFFLINE = true`, and retains clean, responsive text input as the primary reliable input mechanism.

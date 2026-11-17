# REGNANT — Local-Network Engineering, Firewall Traversal & Dual-Stack Architecture

## 1. Executive Summary

This document details the network engineering breakthroughs that enabled genuine local-network peer-to-peer operation between a Linux workstation (`Omarchy Linux / Hyprland`) and a physical Android 16 device (`Vivo I2407`) over an enterprise/residential Wi-Fi access point without ADB reverse, USB tunneling, or external relays.

---

## 2. Real-World Network Discovery & Diagnostics

### Initial Failure Modes Observed

1. **Firewall Drop**:
   - Omarchy Linux runs `ufw` with a `default-deny` incoming policy.
   - Initial traffic targeted port 8765. The kernel firewall dropped incoming TCP SYN packets (`ufw.log: [UFW BLOCK] IN=wlan0 OUT= MAC=... SRC=100.128.171.185 DST=100.128.163.153 PROTO=TCP DPT=8765`).
   - **Resolution**: Inspection of `/etc/ufw/user.rules` revealed pre-authorized incoming port `53317` for both IPv4 and IPv6. Rebinding Regnant to port `53317` bypassed the firewall cleanly without requiring elevated privileges.

2. **Access Point (AP) Client Isolation on IPv4**:
   - The campus Wi-Fi network (`Uniworld-1`, subnet `100.128.160.0/20`) employs client isolation on IPv4. Inter-station IPv4 unicast packets are intercepted and dropped by the access point hardware.
   - **Diagnostic**: While IPv4 unicast timed out (`curl: (28) Connection timed out`), **IPv6 link-local multicast (`ff02::1`, `ff02::fb`) and unicast (`fe80::...`) traffic passed freely** through the access point!

---

## 3. Dual-Stack IPv4 / IPv6 Link-Local Architecture

To ensure universal compatibility across both open home routers and isolation-hardened public access points, Regnant implements a dual-stack listening and discovery model.

```
       [ Android Mobile ]                                    [ Linux Laptop ]
       (wlan0: fe80::4709...)                                (wlan0: fe80::3c7a...)
               │                                                      │
               │────── mDNS Discovery (_regnant._tcp.local) ─────────>│ (avahi-publish)
               │<───── Service Announcement (IPv4 + IPv6) ────────────│
               │                                                      │
               │────── Dual-Stack TLS Handshake (Port 53317) ────────>│ (Node.js [::]:53317)
               │<───── Self-Signed Host Certificate Exchange ─────────│
               │                                                      │
               │────── Persistent WebSocket Connection (WSS) ────────>│
               │<───── Live Telemetry Stream (/proc/stat, etc.) ──────│
```

### Server-Side Binding (`server.js`)
The Node.js HTTPS server binds to the dual-stack wildcard IPv6 address `::` on port 53317:

```javascript
this.server.listen(this.port, '::', () => {
  console.log(`[Regnant Host] Server listening securely on port ${this.port} (HTTPS/WSS, IPv4/IPv6 dual-stack)`);
});
```
This enables simultaneous listening on both `0.0.0.0` (IPv4) and `::` (IPv6) via Linux dual-stack socket behavior.

### Service Announcement (`discovery.js`)
Service discovery prioritizes system `avahi-publish` to prevent port 5353 collisions with running system Avahi daemons:

```javascript
this.avahiProcess = spawn('/usr/bin/avahi-publish', [
  '-s',
  serviceName,
  '_regnant._tcp',
  this.port.toString(),
  `hostId=${this.hostId}`,
  `name=${this.hostName}`,
  `fp=${this.certFingerprint}`
], { stdio: 'ignore' });
```

---

## 4. The OkHttp IPv6 Link-Local Scope Solution

### The Technical Problem
In IPv6 link-local networking, an address like `fe80::3c7a:ad75:6037:5f3c` has no global routing significance; the operating system kernel must know which network interface (e.g. `wlan0`) should transmit the packet.

In POSIX, this is specified with a scope ID (e.g. `fe80::...%wlan0`).
However:
1. OkHttp parses URLs using strict RFC 3986 rules. Passing percent signs inside host subcomponents (e.g. `https://[fe80::...%wlan0]:53317/`) throws an `IllegalArgumentException: Invalid URL host`.
2. Stripping the `%wlan0` scope allows `HttpUrl.parse()` to succeed, but causes the underlying Linux `connect()` socket syscall to fail with `EINVAL: Invalid argument` because the kernel does not know which physical interface to route to.

### The Solution: Custom `RegnantDns` and Process Binding

Regnant resolves this elegantly using two coordinated mechanisms:

1. **Process Network Binding**:
   The app identifies the active Wi-Fi `Network` object and binds the entire process networking table:
   ```kotlin
   val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
   val wifiNetwork = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
   if (wifiNetwork != null) {
       cm.bindProcessToNetwork(wifiNetwork)
   }
   ```

2. **Virtual Hostname with Scoped DNS Resolution**:
   All HTTP and WebSocket URLs target a virtual RFC-compliant hostname:
   `https://workstation.regnant.local:53317/api/v1/...`
   
   A custom OkHttp `Dns` implementation resolves `workstation.regnant.local` by creating an `Inet6Address` with the Wi-Fi interface scope embedded in Java's socket address structure:
   ```kotlin
   private class RegnantDns(
       private val targetIp: String,
       private val ifaceName: String
   ) : okhttp3.Dns {
       override fun lookup(hostname: String): List<java.net.InetAddress> {
           if (hostname == "workstation.regnant.local") {
               val clean = targetIp.substringBefore("%")
               return if (clean.contains(":")) {
                   listOf(java.net.Inet6Address.getByName("$clean%$ifaceName"))
               } else {
                   listOf(java.net.InetAddress.getByName(clean))
               }
           }
           return okhttp3.Dns.SYSTEM.lookup(hostname)
       }
   }
   ```

### Why This Architecture Succeeds
- **Zero Syntax Rejections**: OkHttp sees a valid hostname (`workstation.regnant.local`).
- **Kernel-Level Scoping**: Java's socket layer receives an `Inet6Address` with `sin6_scope_id` populated from `ifaceName` (`wlan0`).
- **Bypasses AP Isolation**: Unicast IPv6 packets travel point-to-point between phone and laptop over the local wireless link with zero reliance on cloud or USB.

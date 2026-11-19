# Known Limitations

This document records platform and verification boundaries. A component's existence does not prove every deployment path has been validated.

## Platform

- The strongest integration target is Omarchy Linux with Hyprland and Wayland.
- Quickshell/PAM remote unlock is Omarchy-specific and requires the documented user plugin integration.
- Audio control depends on PipeWire/WirePlumber and MPRIS availability.
- Desktop capture depends on supported Wayland capture tools.
- Browser integration targets Chromium Manifest V3 APIs.
- Android background reliability depends on OS and OEM battery policies. The foreground service has user-visible notification requirements.

## Feature verification

The Linux host automated suite currently covers 32 tests. This does not prove physical-device operation of every integration.

- AI agent adapters can detect and describe installed CLIs; interactive task streaming must be validated separately for each CLI version.
- Browser extension installation and live browser control require manual browser-side setup and acceptance testing.
- Wake-on-LAN packet construction is tested; successful wake depends on firmware, BIOS, NIC and network configuration.
- Download management inspects the host download directory; it is not a universal browser download controller.
- Automation rule persistence and trigger matching are tested; each real-world trigger source requires separate verification.
- Android Gradle currently reports no unit-test source files.
- Some platform modules may be unavailable outside the developer's Omarchy/Hyprland machine.

## Network

- Peer-to-peer Wi-Fi access may be blocked by access-point client isolation.
- IPv6 link-local connectivity depends on the network permitting local multicast and peer traffic.
- If the laptop is asleep or powered off, the host service is unreachable. Wake-on-LAN is a separate, hardware-dependent path.

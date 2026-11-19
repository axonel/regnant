# Feature Inventory

This is an implementation-oriented inventory, not a guarantee that every capability is portable or fully accepted on every device.

| Subsystem | Code area | Current scope | Verification boundary |
|---|---|---|---|
| Android client | `android/` | Compose UI, device pairing, dashboard, project/file views, terminal and control screens | Debug build succeeds; current Gradle test task has no test sources |
| Host API | `linux/host/src/server.js` | HTTPS/WSS endpoints and authenticated device requests | Host integration tests |
| Pairing and security | Host crypto/database modules | Pairing codes, tokens, revocation and capabilities | Automated host tests |
| Workspace control | Hyprland manager and Android selector | Workspace query, switch and event sync | Requires Hyprland session |
| Remote lock/unlock | Power manager and Omarchy integration | Lock and PAM-backed Omarchy unlock | Requires Omarchy/Quickshell/PAM |
| Terminal | Terminal manager | Persistent PTY sessions | Host manager tests; manual interactive acceptance remains useful |
| AI adapters | Agent modules | CLI discovery and adapter integration points | CLI-specific streaming must be verified independently |
| Git/GitHub | Git managers | Local repository and GitHub-oriented operations | Host tests; requires local Git/GitHub CLI configuration |
| Desktop capture | Desktop peek manager | Capture active desktop output | Requires Wayland capture environment |
| Audio/media | Audio/media manager | PipeWire volume and MPRIS state/actions | Requires user-session services |
| Search | Search manager | Local indexed search | Host tests |
| Downloads | Download manager | Inspect host Downloads directory | Not a generic browser download controller |
| Automations | Automation engine/scheduler | Persist rules and schedule operations | Real trigger integrations need separate verification |
| Wake-on-LAN | WoL manager | Discover configuration and send magic packet | Actual wake requires firmware/NIC/network support |
| Browser companion | `linux/browser-extension/` | MV3 extension and local bridge components | Manual install and live integration acceptance needed |

Update this table when implementation scope or verification changes.

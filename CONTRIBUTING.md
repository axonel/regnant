# Contributing to Regnant

Regnant is a local-first Android-to-Linux control plane. Contributions should preserve explicit user control, least privilege and predictable behaviour.

## Before you start

1. Read [the architecture](docs/ARCHITECTURE.md), [security notes](docs/SECURITY.md) and [known limitations](docs/KNOWN_LIMITATIONS.md).
2. Search existing issues and pull requests.
3. For substantial changes, propose the design in an issue or discussion first.

## Development setup

- Android: JDK 21 and Android SDK API 36.
- Linux host: Node.js 26 and npm.
- Desktop integrations: compatible Linux session; certain features require Omarchy, Hyprland, Wayland, PipeWire or other host tools.

Run host tests:

```bash
cd linux/host
npm ci
npm test
```

Build Android:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

Android currently has no unit-test source files; Gradle may report `NO-SOURCE`. Do not interpret that as Android test coverage.

## Pull requests

- Use a focused branch and a clear Conventional Commit message.
- Explain behaviour and implementation.
- Add tests for changed behaviour where practical.
- Update documentation and known limitations.
- Include UI screenshots where appropriate, with private information redacted.
- Do not add credentials, generated build output, local databases or machine-specific configuration.

## Engineering expectations

- Prefer typed, allowlisted host capabilities over arbitrary shell execution.
- Validate input at the host boundary.
- Keep network and background behaviour explicit.
- Handle unsupported desktop environments without pretending an operation succeeded.
- Document platform prerequisites and failure modes.

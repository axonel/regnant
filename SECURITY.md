# Security Policy

## Scope

Regnant controls a Linux workstation from a paired Android device. Security-sensitive areas include pairing, TLS certificate pinning, bearer-token handling, capability checks, terminal sessions, file access, browser integration and Omarchy PAM-backed unlock.

## Reporting a vulnerability

Do not open a public issue for an exploitable vulnerability.

Use GitHub's **Report a vulnerability** feature in the repository Security tab when available. Include:
- Affected version or commit.
- Concise description and impact.
- Reproduction steps using synthetic credentials and data.
- Relevant logs with tokens, passwords, IP addresses and personal information removed.

Never include real passwords, pairing codes, access tokens, private keys or unredacted device logs.

## Supported versions

Security fixes are currently directed at the latest development state on the default branch. No formal release-support window has been established.

See [docs/SECURITY.md](docs/SECURITY.md) for the security model and limitations.

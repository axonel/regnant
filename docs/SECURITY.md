# Security Model

Regnant can perform consequential operations on a personal workstation. Its security boundary is the paired device, authenticated local transport and host-side capability enforcement.

## Existing design

The implementation documents:
- TLS transport and Android-side host certificate pinning.
- Short-lived, single-use pairing codes with rate limiting.
- High-entropy bearer tokens, with token hashes stored by the host.
- Per-device capabilities and revocation.
- Request identifiers and replay prevention.
- Deterministic command parsing and operation-specific execution paths.
- Android encrypted storage for pairing material.
- User-session execution rather than a root daemon.

See [the detailed security design](security.md) and [remote unlock integration](remote-unlock.md).

## Important limitations

- Local network membership is not a trust guarantee. Keep host firewall and pairing controls enabled.
- Desktop integrations run with the logged-in user's privileges and can affect that user's files and session.
- Remote unlock sends the login password to the local host over authenticated TLS so Omarchy's PAM flow can validate it. It is not intentionally persisted by the application, but credentials exist transiently in process memory and transport buffers.
- The browser extension requests broad host permissions. Review its manifest and browser-bridge authentication before enabling it in sensitive browsing profiles.
- Host security tests are not a formal independent security audit.

## Reporting

Use GitHub's private vulnerability reporting feature. Never include real credentials, tokens, private keys or unredacted logs in an issue.

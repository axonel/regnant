# Regnant — Security Architecture & Threat Model

## 1. Security Principles

1. **Zero Cloud Trust**: Regnant sends zero telemetry, zero analytics, zero external API requests, and contacts zero remote relay servers.
2. **Local Cryptographic Isolation**: Even on an untrusted or public local Wi-Fi network, devices must explicitly authenticate using TLS with pinned fingerprints and high-entropy device bearer tokens.
3. **No Arbitrary Execution**: The Linux host acts as a restricted executor. It disallows shell invocation, piping, concatenation, or arbitrary command arguments.
4. **Hardware-Backed Secret Storage**: Android tokens and host fingerprints are stored using Android Keystore-backed encrypted storage.
5. **Least Privilege**: The host daemon runs strictly under the logged-in user's privileges, never as root or sudo.

## 2. Threat Analysis & Mitigations

| Threat Vector | Mitigation Strategy |
|---|---|
| **Eavesdropping on Local Wi-Fi** | All HTTP and WebSocket communications use TLS 1.3 with self-signed certificate pinning. |
| **Rogue Host Spoofing** | During pairing, the host certificate SHA-256 fingerprint is recorded by the Android client and pinned for all subsequent connections. |
| **Pairing Code Brute Force** | Pairing codes expire in 5 minutes, are single-use, rate-limited to 3 failed attempts, and verified using constant-time string comparison (`timingSafeEqual`). |
| **Replay Attacks** | Every command request carries a unique UUID `requestId` and timestamp. The host checks and records request IDs in a persistent replay-prevention table in SQLite. Replayed requests within the expiry window are rejected. |
| **Arbitrary Command Injection** | Command parsing is deterministic. Actions map strictly to typed allowlist targets. Binaries are executed via `execFile` without shell spawning (`shell: false`). |
| **Unauthorized Device Access** | The host checks `Authorization: Bearer <token>` against the active SQLite device registry. Paired devices can be revoked instantly from either device. |
| **Secret Leakage in Logs** | Pairing codes, bearer tokens, and private keys are scrubbed and forbidden from standard loggers. |

## 3. Pairing Security Protocol

1. **Code Generation**:
   - Host generates a random 6-character uppercase alphanumeric code using `crypto.randomBytes(4)`.
   - Entropy: \(36^6 \approx 2.17 \times 10^9\) combinations.
   - Code validity duration: 300 seconds (5 minutes).
2. **Constant-Time Verification**:
   - Submissions are compared using `crypto.timingSafeEqual` over SHA-256 hashes of the codes to prevent side-channel timing attacks.
3. **Token Issuance**:
   - Upon successful verification, host generates a 256-bit secure token (`crypto.randomBytes(32).toString('hex')`).
   - Token is hashed with SHA-256 before persisting to SQLite (`token_hash`), so a compromised host database does not reveal plaintext bearer tokens.
   - Plaintext token is delivered once over the established TLS channel to the authenticated Android client.
4. **Revocation**:
   - The desktop user can click "Revoke" on any paired device.
   - Android user can click "Forget Host".
   - Revocation deletes the device record from SQLite and immediately closes any active WebSocket connections for that device ID.

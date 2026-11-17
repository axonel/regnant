# REGNANT — Remote File Browser & Bidirectional Transfer Specification

## 1. Overview

Regnant provides a secure local remote file browser and streaming transfer engine. It enables browsing authorized workstation directories, viewing text file previews, and transferring files bidirectionally between the Linux laptop and Android phone over the local network.

---

## 2. Directory Sandboxing & Traversal Defenses

Remote filesystem access is strictly confined to an allowlist of approved directory roots. By default, Regnant authorizes:
- `~/Projects`
- `~/Downloads`

### Traversal Prevention Implementation

```javascript
isPathWithinApprovedRoots(targetPath) {
  const roots = this.getApprovedRoots();
  const resolvedTarget = path.resolve(targetPath);

  for (const root of roots) {
    const resolvedRoot = path.resolve(root.path);
    const relative = path.relative(resolvedRoot, resolvedTarget);
    if (!relative.startsWith('..') && !path.isAbsolute(relative)) {
      // Realpath verification to defeat symlink escapes
      if (fs.existsSync(resolvedTarget)) {
        const realTarget = fs.realpathSync(resolvedTarget);
        const realRoot = fs.realpathSync(resolvedRoot);
        const realRelative = path.relative(realRoot, realTarget);
        if (realRelative.startsWith('..') || path.isAbsolute(realRelative)) {
          return false;
        }
      }
      return true;
    }
  }
  return false;
}
```

### Protection Guarantees
1. **Dot-Dot Directory Traversal Rejection**: Paths like `../../../../etc/shadow` are resolved against the approved roots; any attempt to navigate above the root root is immediately blocked with HTTP 403.
2. **Symlink Escape Defense**: Symlinks pointing outside approved boundaries (e.g. `~/Projects/sym_to_root -> /`) are canonicalized using `fs.realpathSync()`. If the canonical target resides outside the approved root, access is rejected.
3. **Hidden File Filter**: Dotfiles and hidden system directories (`.git`, `.ssh`, `.config`) are filtered out of file browsing responses.

---

## 3. Remote File Browser API

### Listing Files
`GET /api/v1/files?path=<encoded_path>`
- If `path` is empty, returns the list of approved roots (e.g. `Projects`, `Downloads`).
- Returns directory items sorted with subdirectories first, followed by files alphabetically.
- Each item contains `name`, `path`, `isDirectory`, `size` (bytes), and `modified` (epoch timestamp).

### In-App Text File Preview
`GET /api/v1/files/read?path=<encoded_path>`
- Detects whether a file is plain text based on extension and content inspection.
- Enforces a preview threshold (maximum 512 KB) to prevent memory exhaustion on mobile.
- Returns `{ path, fileName, size, isText: true, content: "<content_string>" }`.
- Binary files return `{ isText: false, content: null }`.

---

## 4. Chunked Transfer Architecture

File transfers are executed over authenticated TLS 1.3 in sequential 256 KB chunks.

```
       [ Android Client ]                       [ Linux Host ]
               │                                      │
               │  POST /api/v1/transfer/init          │
               │─────────────────────────────────────>│ Create Transfer Record
               │  {"fileName", "fileSize", "sha256"}  │ Stage into .tmp/
               │<─────────────────────────────────────│
               │  {"transferId", "chunkSize"}         │
               │                                      │
               │  POST /api/v1/transfer/chunk         │
               │─────────────────────────────────────>│ Append chunk & verify
               │  {"transferId", "chunkIndex", data}  │ bytes received
               │<─────────────────────────────────────│
               │  {"received": true}                  │
               │                                      │
               │  POST /api/v1/transfer/complete      │
               │─────────────────────────────────────>│ Calculate final SHA-256
               │  {"transferId"}                      │ Verify against expected hash
               │                                      │ Atomic move to ~/Downloads/
               │<─────────────────────────────────────│ Regnant Transfers/
               │  {"status": "COMPLETED", "path"}     │
```

### Staging & Atomic Finalization
1. Uploads are staged in `$DATA_DIR/staging/<transferId>.tmp`.
2. Each chunk's offset is tracked sequentially to prevent out-of-order writes or sparse file corruption.
3. Upon completion, the server calculates the full-file SHA-256 checksum and compares it to the client-provided digest.
4. If valid, the file is atomically moved (`fs.renameSync`) into `~/Downloads/Regnant Transfers/<fileName>`.
5. If the checksum fails, the staged temp file is immediately removed.

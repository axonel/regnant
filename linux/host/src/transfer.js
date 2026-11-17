import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { dbManager } from './db.js';
import { notifier } from './notifier.js';
import { fileBrowser } from './filebrowser.js';

export class TransferManager {
  constructor() {
    this.activeUploads = new Map(); // id -> { id, fileName, fileSize, sha256, tempPath, destPath, hash, writtenBytes }
    this.transferDir = path.join(os.homedir(), 'Downloads', 'Regnant Transfers');
    fs.mkdirSync(this.transferDir, { recursive: true, mode: 0o755 });
  }

  initUpload(fileName, fileSize, expectedSha256) {
    // Sanitize filename to prevent directory traversal
    const safeName = path.basename(fileName).replace(/[^a-zA-Z0-9._-]/g, '_');
    const transferId = `xfer-${Date.now().toString(36)}-${crypto.randomBytes(4).toString('hex')}`;
    const tempPath = path.join(os.tmpdir(), `regnant-xfer-${transferId}.part`);
    const destPath = path.join(this.transferDir, safeName);

    const record = {
      id: transferId,
      direction: 'upload',
      fileName: safeName,
      fileSize,
      sha256: expectedSha256 || '',
      destination: destPath,
      status: 'IN_PROGRESS',
      createdAt: Date.now(),
    };

    dbManager.recordTransfer(record);

    this.activeUploads.set(transferId, {
      ...record,
      tempPath,
      hash: crypto.createHash('sha256'),
      writtenBytes: 0,
      stream: fs.createWriteStream(tempPath, { flags: 'w', mode: 0o600 }),
    });

    return {
      transferId,
      fileName: safeName,
      destination: destPath,
    };
  }

  appendChunk(transferId, chunkBuffer, offset = 0) {
    const upload = this.activeUploads.get(transferId);
    if (!upload) {
      throw new Error(`Upload transfer session "${transferId}" not found or expired`);
    }

    upload.stream.write(chunkBuffer);
    upload.hash.update(chunkBuffer);
    upload.writtenBytes += chunkBuffer.length;

    return {
      transferId,
      writtenBytes: upload.writtenBytes,
      totalBytes: upload.fileSize,
      progress: Math.min(100, Math.round((upload.writtenBytes / upload.fileSize) * 100)),
    };
  }

  async finalizeUpload(transferId) {
    const upload = this.activeUploads.get(transferId);
    if (!upload) {
      throw new Error(`Upload transfer session "${transferId}" not found`);
    }

    return new Promise((resolve, reject) => {
      upload.stream.end(async () => {
        try {
          const computedSha256 = upload.hash.digest('hex');

          if (upload.sha256 && upload.sha256.toLowerCase() !== computedSha256.toLowerCase()) {
            fs.unlinkSync(upload.tempPath);
            dbManager.updateTransferStatus(transferId, 'INTEGRITY_FAILED');
            this.activeUploads.delete(transferId);
            return reject(new Error(`SHA-256 integrity mismatch! Expected ${upload.sha256} but got ${computedSha256}`));
          }

          // Atomic move into Regnant Transfers
          fs.renameSync(upload.tempPath, upload.destination);
          dbManager.updateTransferStatus(transferId, 'COMPLETED');
          this.activeUploads.delete(transferId);

          notifier.notify('Regnant File Received', `Saved ${upload.fileName} (${Math.round(upload.writtenBytes / 1024)} KB)`);

          resolve({
            success: true,
            transferId,
            fileName: upload.fileName,
            destination: upload.destination,
            fileSize: upload.writtenBytes,
            sha256: computedSha256,
          });
        } catch (e) {
          reject(e);
        }
      });
    });
  }

  getDownloadInfo(filePath) {
    const resolved = path.resolve(filePath);
    if (!fileBrowser.isPathWithinApprovedRoots(resolved)) {
      throw new Error(`Access denied: path is outside approved roots: ${filePath}`);
    }

    if (!fs.existsSync(resolved)) {
      throw new Error(`File does not exist: ${filePath}`);
    }

    const stat = fs.statSync(resolved);
    if (stat.isDirectory()) {
      throw new Error(`Cannot download directory: ${filePath}`);
    }

    // Compute SHA-256
    const fileBuffer = fs.readFileSync(resolved);
    const sha256 = crypto.createHash('sha256').update(fileBuffer).digest('hex');

    return {
      path: resolved,
      fileName: path.basename(resolved),
      fileSize: stat.size,
      sha256,
    };
  }
}

export const transferManager = new TransferManager();

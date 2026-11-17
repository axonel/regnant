import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('regnantApi', {
  getStatus: () => ipcRenderer.invoke('get-status'),
  generatePairingCode: () => ipcRenderer.invoke('generate-pairing-code'),
  revokeDevice: (deviceId) => ipcRenderer.invoke('revoke-device', deviceId),
  restartHost: () => ipcRenderer.invoke('restart-host'),
  closeWindow: () => ipcRenderer.invoke('close-window'),
  minimizeWindow: () => ipcRenderer.invoke('minimize-window'),
});

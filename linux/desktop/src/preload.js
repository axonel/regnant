import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('regnantApi', {
  getStatus: () => ipcRenderer.invoke('get-status'),
  generatePairingCode: () => ipcRenderer.invoke('generate-pairing-code'),
  revokeDevice: (deviceId) => ipcRenderer.invoke('revoke-device', deviceId),
  restartHost: () => ipcRenderer.invoke('restart-host'),
  getMetrics: () => ipcRenderer.invoke('get-metrics'),
  getProjects: () => ipcRenderer.invoke('get-projects'),
  getRoots: () => ipcRenderer.invoke('get-roots'),
  getPermissions: (deviceId) => ipcRenderer.invoke('get-permissions', deviceId),
  setPermission: (payload) => ipcRenderer.invoke('set-permission', payload),
  getPendingApprovals: () => ipcRenderer.invoke('get-pending-approvals'),
  respondApproval: (payload) => ipcRenderer.invoke('respond-approval', payload),
  closeWindow: () => ipcRenderer.invoke('close-window'),
  minimizeWindow: () => ipcRenderer.invoke('minimize-window'),
});

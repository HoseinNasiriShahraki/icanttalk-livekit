const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('icanttalk', {
  getSettings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (settings) => ipcRenderer.invoke('settings:save', settings),
  setAccessKey: (accessKey) => ipcRenderer.invoke('access-key:set', accessKey),
  requestToken: (request) => ipcRenderer.invoke('token:request', request),
  requestPresence: (request) => ipcRenderer.invoke('presence:request', request),
  listDisplaySources: () => ipcRenderer.invoke('display-sources:list'),
  selectDisplaySource: (sourceId) => ipcRenderer.invoke('display-source:select', sourceId),
  getAppVersion: () => ipcRenderer.invoke('app:version'),
  setNativeFullscreen: (enabled) => ipcRenderer.invoke('app:fullscreen', enabled),
});

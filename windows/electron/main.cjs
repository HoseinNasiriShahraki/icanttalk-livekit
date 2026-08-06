const {
  app,
  BrowserWindow,
  desktopCapturer,
  ipcMain,
  safeStorage,
  session,
} = require('electron');
const { execFileSync } = require('node:child_process');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const AVATAR_IDS = Array.from({ length: 37 }, (_, index) => String(index + 1).padStart(2, '0'));
const DEFAULT_SETTINGS = Object.freeze({
  username: '',
  endpointUrl: '',
  installId: '',
  avatarId: '',
  inputDeviceId: 'default',
  outputDeviceId: 'default',
  cameraDeviceId: 'default',
  voiceMode: 'vad',
  pttKey: 'Space',
  noiseMode: 'standard',
  noiseSuppression: true,
  echoCancellation: true,
  autoGainControl: true,
  cameraQuality: '720p',
  screenResolution: '1080p',
  screenFps: 30,
  encryptedAccessKey: '',
});

let mainWindow = null;
let selectedDisplaySourceId = null;

function randomAvatarId() {
  return AVATAR_IDS[crypto.randomInt(0, AVATAR_IDS.length)];
}

function settingsPath() {
  return path.join(app.getPath('userData'), 'settings.json');
}

function normalizeSettings(parsed = {}) {
  const noiseMode = ['off', 'standard', 'krisp'].includes(parsed.noiseMode)
    ? parsed.noiseMode
    : parsed.noiseSuppression === false
      ? 'off'
      : 'standard';
  return {
    ...DEFAULT_SETTINGS,
    ...parsed,
    installId: parsed.installId || crypto.randomUUID(),
    avatarId: AVATAR_IDS.includes(parsed.avatarId) ? parsed.avatarId : randomAvatarId(),
    noiseMode,
    noiseSuppression: noiseMode !== 'off',
  };
}

function readSettingsFile() {
  try {
    return normalizeSettings(JSON.parse(fs.readFileSync(settingsPath(), 'utf8')));
  } catch {
    return normalizeSettings();
  }
}

function writeSettingsFile(settings) {
  const filePath = settingsPath();
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const temporaryPath = `${filePath}.tmp`;
  fs.writeFileSync(temporaryPath, JSON.stringify(settings, null, 2), {
    encoding: 'utf8',
    mode: 0o600,
  });
  fs.renameSync(temporaryPath, filePath);
}

function sanitizePublicSettings(settings) {
  const { encryptedAccessKey, ...publicSettings } = settings;
  return {
    ...publicSettings,
    accessKeyConfigured: Boolean(encryptedAccessKey),
  };
}

function setEncryptedAccessKey(settings, accessKey) {
  const normalized = String(accessKey || '').trim();
  if (!normalized) throw new Error('Access key cannot be empty.');
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error('Windows secure storage is unavailable on this device.');
  }
  return {
    ...settings,
    encryptedAccessKey: safeStorage.encryptString(normalized).toString('base64'),
  };
}

function decryptAccessKey(settings) {
  if (!settings.encryptedAccessKey) {
    throw new Error('No access key is configured. Open Settings and enter one.');
  }
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error('Windows secure storage is unavailable on this device.');
  }
  return safeStorage.decryptString(Buffer.from(settings.encryptedAccessKey, 'base64'));
}

function importInstallerAccessKey() {
  if (process.platform !== 'win32') return;
  const settings = readSettingsFile();
  if (settings.encryptedAccessKey) return;
  try {
    const output = execFileSync(
      'reg.exe',
      ['query', 'HKCU\\Software\\iCANTTalk', '/v', 'PendingAccessKey'],
      { encoding: 'utf8', windowsHide: true },
    );
    const accessKey = output.match(/PendingAccessKey\s+REG_SZ\s+(.+)$/m)?.[1]?.trim();
    if (!accessKey) return;
    writeSettingsFile(setEncryptedAccessKey(settings, accessKey));
    execFileSync(
      'reg.exe',
      ['delete', 'HKCU\\Software\\iCANTTalk', '/v', 'PendingAccessKey', '/f'],
      { windowsHide: true, stdio: 'ignore' },
    );
  } catch {
    // Installer value is optional for development and portable builds.
  }
}

function validateEndpointUrl(rawUrl) {
  let url;
  try {
    url = new URL(String(rawUrl || '').trim());
  } catch {
    throw new Error('The token endpoint URL is invalid.');
  }
  if (!['https:', 'http:'].includes(url.protocol)) {
    throw new Error('The token endpoint must use HTTP or HTTPS. HTTPS is strongly recommended.');
  }
  return url.toString();
}

async function callEndpoint(endpointUrl, accessKey, payload) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const response = await fetch(endpointUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-iCANTTalk-Access-Key': accessKey,
      },
      body: JSON.stringify(payload),
      signal: controller.signal,
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(body.error || body.detail || `Endpoint returned HTTP ${response.status}.`);
    }
    return body;
  } catch (error) {
    if (error?.name === 'AbortError') throw new Error('The endpoint timed out.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function requestLiveKitToken(payload) {
  const settings = readSettingsFile();
  const endpointUrl = validateEndpointUrl(payload.endpointUrl || settings.endpointUrl);
  const accessKey = decryptAccessKey(settings);
  const roomName = String(payload.roomName || '');
  const participantName = String(payload.participantName || '').trim();
  const participantIdentity = String(settings.installId || '').trim();

  if (!['Room 1', 'Room 2'].includes(roomName)) throw new Error('Only Room 1 and Room 2 are allowed.');
  if (!participantName || participantName.length > 40) throw new Error('Username must contain 1 to 40 characters.');
  if (!/^[A-Za-z0-9._:-]{8,128}$/.test(participantIdentity)) {
    throw new Error('The local installation identity is invalid.');
  }

  const body = await callEndpoint(endpointUrl, accessKey, {
    room_name: roomName,
    participant_identity: participantIdentity,
    participant_name: participantName,
    avatar_id: settings.avatarId,
    platform: 'windows',
  });
  const serverUrl = body.server_url || body.url || body.livekit_url;
  const participantToken = body.participant_token || body.token;
  if (!serverUrl || !participantToken) {
    throw new Error('The token endpoint response is missing server_url or participant_token.');
  }
  return { serverUrl, participantToken };
}

async function requestRoomPresence(payload) {
  const settings = readSettingsFile();
  const endpointUrl = validateEndpointUrl(payload.endpointUrl || settings.endpointUrl);
  const accessKey = decryptAccessKey(settings);
  const body = await callEndpoint(endpointUrl, accessKey, {
    action: 'room_presence',
    room_names: ['Room 1', 'Room 2'],
    client_version: '1.1.1',
  });
  if (!Array.isArray(body.rooms)) throw new Error('The presence response is missing rooms.');
  return body;
}

function isTrustedRendererUrl(url) {
  return url.startsWith('file://') || url.startsWith('http://127.0.0.1:5173');
}

function configureMediaPermissions() {
  const currentSession = session.defaultSession;
  currentSession.setPermissionCheckHandler((_webContents, permission, requestingOrigin) =>
    isTrustedRendererUrl(requestingOrigin) && ['media', 'display-capture'].includes(permission),
  );
  currentSession.setPermissionRequestHandler((webContents, permission, callback) => {
    callback(isTrustedRendererUrl(webContents.getURL()) && ['media', 'display-capture'].includes(permission));
  });
  currentSession.setDisplayMediaRequestHandler(async (_request, callback) => {
    try {
      const sources = await desktopCapturer.getSources({
        types: ['screen', 'window'],
        thumbnailSize: { width: 480, height: 270 },
        fetchWindowIcons: true,
      });
      const selected = sources.find((source) => source.id === selectedDisplaySourceId) || sources[0];
      selectedDisplaySourceId = null;
      callback(selected ? { video: selected, audio: process.platform === 'win32' ? 'loopback' : undefined } : {});
    } catch {
      callback({});
    }
  });
}

function registerIpcHandlers() {
  ipcMain.handle('settings:get', () => {
    const settings = readSettingsFile();
    writeSettingsFile(settings);
    return sanitizePublicSettings(settings);
  });

  ipcMain.handle('settings:save', (_event, updates) => {
    const current = readSettingsFile();
    const noiseMode = ['off', 'standard', 'krisp'].includes(updates.noiseMode)
      ? updates.noiseMode
      : 'standard';
    const next = {
      ...current,
      username: String(updates.username || '').trim().slice(0, 40),
      endpointUrl: String(updates.endpointUrl || '').trim().slice(0, 2048),
      avatarId: AVATAR_IDS.includes(updates.avatarId) ? updates.avatarId : current.avatarId,
      inputDeviceId: String(updates.inputDeviceId || 'default'),
      outputDeviceId: String(updates.outputDeviceId || 'default'),
      cameraDeviceId: String(updates.cameraDeviceId || 'default'),
      voiceMode: updates.voiceMode === 'ptt' ? 'ptt' : 'vad',
      pttKey: String(updates.pttKey || 'Space').slice(0, 32),
      noiseMode,
      noiseSuppression: noiseMode !== 'off',
      echoCancellation: Boolean(updates.echoCancellation),
      autoGainControl: Boolean(updates.autoGainControl),
      cameraQuality: ['480p', '720p', '1080p'].includes(updates.cameraQuality) ? updates.cameraQuality : '720p',
      screenResolution: ['480p', '720p', '1080p'].includes(updates.screenResolution) ? updates.screenResolution : '1080p',
      screenFps: Number(updates.screenFps) === 60 ? 60 : 30,
    };
    writeSettingsFile(next);
    return sanitizePublicSettings(next);
  });

  ipcMain.handle('access-key:set', (_event, accessKey) => {
    const next = setEncryptedAccessKey(readSettingsFile(), accessKey);
    writeSettingsFile(next);
    return { accessKeyConfigured: true };
  });
  ipcMain.handle('token:request', (_event, payload) => requestLiveKitToken(payload));
  ipcMain.handle('presence:request', (_event, payload) => requestRoomPresence(payload));
  ipcMain.handle('display-sources:list', async () => {
    const sources = await desktopCapturer.getSources({
      types: ['screen', 'window'],
      thumbnailSize: { width: 480, height: 270 },
      fetchWindowIcons: true,
    });
    return sources.map((source) => ({
      id: source.id,
      name: source.name,
      thumbnail: source.thumbnail.toDataURL(),
      appIcon: source.appIcon && !source.appIcon.isEmpty() ? source.appIcon.toDataURL() : null,
      kind: source.id.startsWith('screen:') ? 'screen' : 'window',
    }));
  });
  ipcMain.handle('display-source:select', (_event, sourceId) => {
    selectedDisplaySourceId = String(sourceId || '');
    return true;
  });
  ipcMain.handle('app:version', () => app.getVersion());
  ipcMain.handle('app:fullscreen', (event, enabled) => {
    const window = BrowserWindow.fromWebContents(event.sender) || mainWindow;
    if (!window || window.isDestroyed()) return false;
    window.setFullScreen(Boolean(enabled));
    return window.isFullScreen();
  });
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1320,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    backgroundColor: '#111318',
    title: 'iCANTTalk',
    icon: path.join(__dirname, '..', 'build', 'icon.ico'),
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  });
  mainWindow.once('ready-to-show', () => mainWindow?.show());
  mainWindow.on('closed', () => { mainWindow = null; });
  if (process.env.VITE_DEV_SERVER_URL) mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
  else mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'));
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!isTrustedRendererUrl(url)) event.preventDefault();
  });
}

app.on('window-all-closed', () => app.quit());
app.on('before-quit', () => { selectedDisplaySourceId = null; });
app.whenReady().then(() => {
  app.setAppUserModelId('com.icanttalk.desktop');
  importInstallerAccessKey();
  configureMediaPermissions();
  registerIpcHandlers();
  createMainWindow();
});

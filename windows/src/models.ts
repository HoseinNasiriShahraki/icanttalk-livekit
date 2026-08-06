export const AVATAR_IDS = Array.from({ length: 37 }, (_, index) => String(index + 1).padStart(2, '0'));

export type NoiseMode = 'off' | 'standard' | 'krisp';

export type PublicSettings = {
  username: string;
  endpointUrl: string;
  installId: string;
  avatarId: string;
  inputDeviceId: string;
  outputDeviceId: string;
  cameraDeviceId: string;
  voiceMode: 'vad' | 'ptt';
  pttKey: string;
  noiseMode: NoiseMode;
  noiseSuppression: boolean;
  echoCancellation: boolean;
  autoGainControl: boolean;
  cameraQuality: '480p' | '720p' | '1080p';
  screenResolution: '480p' | '720p' | '1080p';
  screenFps: 30 | 60;
  accessKeyConfigured: boolean;
};

export type DisplaySource = {
  id: string;
  name: string;
  thumbnail: string;
  appIcon: string | null;
  kind: 'screen' | 'window';
};

export type PresenceParticipant = {
  identity: string;
  name: string;
  avatarId: string;
  platform: 'windows' | 'android' | 'unknown';
  camera: boolean;
  screenShare: boolean;
};

export type RoomPresence = {
  name: 'Room 1' | 'Room 2';
  participants: PresenceParticipant[];
};

export function avatarUrl(avatarId: string): string {
  const safe = AVATAR_IDS.includes(avatarId) ? avatarId : AVATAR_IDS[0];
  return `./avatars/${safe}.jpg`;
}

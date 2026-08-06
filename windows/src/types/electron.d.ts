import type { DisplaySource, PublicSettings, RoomPresence } from '../models';

declare global {
  interface Window {
    icanttalk: {
      getSettings(): Promise<PublicSettings>;
      saveSettings(settings: PublicSettings): Promise<PublicSettings>;
      setAccessKey(accessKey: string): Promise<{ accessKeyConfigured: boolean }>;
      requestToken(request: {
        endpointUrl: string;
        roomName: string;
        participantName: string;
        participantIdentity: string;
      }): Promise<{ serverUrl: string; participantToken: string }>;
      requestPresence(request: { endpointUrl: string }): Promise<{ rooms: RoomPresence[] }>;
      listDisplaySources(): Promise<DisplaySource[]>;
      selectDisplaySource(sourceId: string): Promise<boolean>;
      getAppVersion(): Promise<string>;
      setNativeFullscreen(enabled: boolean): Promise<boolean>;
    };
  }
}

export {};

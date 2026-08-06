import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  ConnectionState,
  LocalParticipant,
  RemoteParticipant,
  RemoteTrackPublication,
  Room,
  RoomEvent,
  Track,
} from 'livekit-client';
import {
  Camera,
  CameraOff,
  ChevronDown,
  HeadphoneOff,
  Headphones,
  LogOut,
  Mic,
  MicOff,
  MonitorUp,
  Pause,
  Play,
  Radio,
  RefreshCw,
  Settings,
  ShieldCheck,
  Users,
  Volume2,
} from 'lucide-react';
import type { PublicSettings, RoomPresence } from './models';
import { avatarUrl } from './models';
import Avatar from './components/Avatar';
import SettingsModal from './components/SettingsModal';
import ScreenShareModal, { type ScreenShareChoice } from './components/ScreenShareModal';
import {
  ParticipantAudio,
  ParticipantTile,
  ScreenShareAudio,
  ScreenShareTile,
  findPublication,
  hasScreenShare,
  publicationWatchKey,
  participantAvatarId,
  type AnyParticipant,
} from './components/ParticipantMedia';
import { KrispController, type KrispState } from './audio/KrispController';

const DEFAULT_SETTINGS: PublicSettings = {
  username: '', endpointUrl: '', installId: '', avatarId: '01',
  inputDeviceId: 'default', outputDeviceId: 'default', cameraDeviceId: 'default',
  voiceMode: 'vad', pttKey: 'Space', noiseMode: 'standard', noiseSuppression: true,
  echoCancellation: true, autoGainControl: true, cameraQuality: '720p',
  screenResolution: '1080p', screenFps: 30, accessKeyConfigured: false,
};
const ROOMS = ['Room 1', 'Room 2'] as const;
type RoomName = (typeof ROOMS)[number];
const QUALITY = {
  '480p': { width: 854, height: 480 },
  '720p': { width: 1280, height: 720 },
  '1080p': { width: 1920, height: 1080 },
} as const;
const EMPTY_PRESENCE: RoomPresence[] = ROOMS.map((name) => ({ name, participants: [] }));

export default function App() {
  const roomRef = useRef<Room | null>(null);
  const krispRef = useRef<KrispController | null>(null);
  const [settings, setSettings] = useState<PublicSettings>(DEFAULT_SETTINGS);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [currentRoom, setCurrentRoom] = useState<RoomName | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>(ConnectionState.Disconnected);
  const [participants, setParticipants] = useState<AnyParticipant[]>([]);
  const [presence, setPresence] = useState<RoomPresence[]>(EMPTY_PRESENCE);
  const [presenceBusy, setPresenceBusy] = useState(false);
  const [activeSpeakers, setActiveSpeakers] = useState<Set<string>>(new Set());
  const [volumes, setVolumes] = useState<Record<string, number>>({});
  const [watching, setWatching] = useState<Record<string, boolean>>({});
  const [screenAudioVolumes, setScreenAudioVolumes] = useState<Record<string, number>>({});
  const [screenAudioMuted, setScreenAudioMuted] = useState<Record<string, boolean>>({});
  const [micEnabled, setMicEnabled] = useState(false);
  const [cameraEnabled, setCameraEnabled] = useState(false);
  const [screenSharing, setScreenSharing] = useState(false);
  const [screenPaused, setScreenPaused] = useState(false);
  const [deafened, setDeafened] = useState(false);
  const [micBeforeDeafen, setMicBeforeDeafen] = useState(false);
  const [status, setStatus] = useState('Choose a room to connect.');
  const [appVersion, setAppVersion] = useState('');
  const [krispState, setKrispState] = useState<KrispState>('disabled');

  useEffect(() => {
    void window.icanttalk.getSettings().then((loaded) => {
      setSettings(loaded);
      if (!loaded.username || !loaded.endpointUrl || !loaded.accessKeyConfigured) setSettingsOpen(true);
    });
    void window.icanttalk.getAppVersion().then(setAppVersion);
  }, []);

  const refreshPresence = useCallback(async (quiet = true) => {
    if (!settings.endpointUrl || !settings.accessKeyConfigured) return;
    if (!quiet) setPresenceBusy(true);
    try {
      const response = await window.icanttalk.requestPresence({ endpointUrl: settings.endpointUrl });
      const normalized = ROOMS.map((name) => response.rooms.find((room) => room.name === name) || { name, participants: [] });
      setPresence(normalized);
    } catch (error) {
      if (!quiet) {
        const message = error instanceof Error ? error.message : 'Unable to load room previews.';
        setStatus(message.includes('Unknown room')
          ? 'Room previews require the v1.1.1 Django endpoint. Update the server module.'
          : message);
      }
    } finally {
      if (!quiet) setPresenceBusy(false);
    }
  }, [settings.accessKeyConfigured, settings.endpointUrl]);

  useEffect(() => {
    void refreshPresence(true);
    const timer = window.setInterval(() => void refreshPresence(true), 5000);
    return () => window.clearInterval(timer);
  }, [refreshPresence]);

  const refreshRoomState = useCallback(() => {
    const room = roomRef.current;
    if (!room) { setParticipants([]); return; }
    setParticipants([room.localParticipant, ...Array.from(room.remoteParticipants.values())]);
    const mic = room.localParticipant.getTrackPublication(Track.Source.Microphone);
    const camera = room.localParticipant.getTrackPublication(Track.Source.Camera);
    const screen = room.localParticipant.getTrackPublication(Track.Source.ScreenShare);
    setMicEnabled(Boolean(mic?.track && !mic.isMuted));
    setCameraEnabled(Boolean(camera?.track && !camera.isMuted));
    setScreenSharing(Boolean(screen?.track));
    setScreenPaused(Boolean(screen?.isMuted));
  }, []);

  const bindRoomEvents = useCallback((room: Room) => {
    const refreshAndPreview = () => { refreshRoomState(); void refreshPresence(true); };
    room.on(RoomEvent.ParticipantConnected, refreshAndPreview);
    room.on(RoomEvent.ParticipantDisconnected, refreshAndPreview);
    room.on(RoomEvent.TrackPublished, refreshAndPreview);
    room.on(RoomEvent.TrackUnpublished, refreshAndPreview);
    room.on(RoomEvent.TrackSubscribed, refreshRoomState);
    room.on(RoomEvent.TrackUnsubscribed, refreshRoomState);
    room.on(RoomEvent.TrackMuted, refreshAndPreview);
    room.on(RoomEvent.TrackUnmuted, refreshAndPreview);
    room.on(RoomEvent.LocalTrackPublished, refreshAndPreview);
    room.on(RoomEvent.LocalTrackUnpublished, refreshAndPreview);
    room.on(RoomEvent.ActiveSpeakersChanged, (speakers) => setActiveSpeakers(new Set(speakers.map((participant) => participant.identity))));
    room.on(RoomEvent.ConnectionStateChanged, (state) => {
      setConnectionState(state);
      if (state === ConnectionState.Reconnecting) setStatus('Connection interrupted. Reconnecting…');
      if (state === ConnectionState.Connected) setStatus('Voice connected.');
    });
    room.on(RoomEvent.Reconnected, () => setStatus('Connection restored.'));
    room.on(RoomEvent.Disconnected, () => {
      setConnectionState(ConnectionState.Disconnected);
      setStatus('Disconnected.');
      void refreshPresence(true);
    });
  }, [refreshPresence, refreshRoomState]);

  const disconnect = useCallback(async () => {
    const room = roomRef.current;
    roomRef.current = null;
    if (krispRef.current) await krispRef.current.dispose();
    krispRef.current = null;
    if (room) { room.removeAllListeners(); await room.disconnect(); }
    setCurrentRoom(null);
    setConnectionState(ConnectionState.Disconnected);
    setParticipants([]);
    setActiveSpeakers(new Set());
    setWatching({});
    setMicEnabled(false); setCameraEnabled(false); setScreenSharing(false); setScreenPaused(false); setDeafened(false);
    setStatus('Disconnected. Choose a room to reconnect.');
    void refreshPresence(true);
  }, [refreshPresence]);

  useEffect(() => {
    const handleUnload = () => { roomRef.current?.disconnect(); };
    window.addEventListener('beforeunload', handleUnload);
    return () => window.removeEventListener('beforeunload', handleUnload);
  }, []);

  function audioCaptureOptions(selected = settings) {
    return {
      deviceId: selected.inputDeviceId === 'default' ? undefined : selected.inputDeviceId,
      noiseSuppression: selected.noiseMode !== 'off',
      echoCancellation: selected.echoCancellation,
      autoGainControl: selected.autoGainControl,
    };
  }

  async function connectToRoom(roomName: RoomName) {
    if (connectionState === ConnectionState.Connecting) return;
    if (!settings.username || !settings.endpointUrl || !settings.accessKeyConfigured) {
      setStatus('Complete profile, endpoint, and access-key settings first.'); setSettingsOpen(true); return;
    }
    if (currentRoom === roomName && connectionState === ConnectionState.Connected) return;
    if (roomRef.current) await disconnect();
    setCurrentRoom(roomName); setConnectionState(ConnectionState.Connecting); setStatus(`Connecting to ${roomName}…`);
    try {
      const credentials = await window.icanttalk.requestToken({ endpointUrl: settings.endpointUrl, roomName, participantName: settings.username, participantIdentity: settings.installId });
      const cameraResolution = QUALITY[settings.cameraQuality];
      const room = new Room({
        adaptiveStream: true, dynacast: true,
        audioCaptureDefaults: audioCaptureOptions(),
        videoCaptureDefaults: { deviceId: settings.cameraDeviceId === 'default' ? undefined : settings.cameraDeviceId, resolution: { ...cameraResolution, frameRate: 30 } },
        publishDefaults: { simulcast: true, dtx: true, red: true },
      });
      const krisp = new KrispController();
      krisp.onStateChange(setKrispState);
      krisp.bind(room);
      await krisp.setEnabled(settings.noiseMode === 'krisp');
      krispRef.current = krisp;
      roomRef.current = room;
      bindRoomEvents(room);
      await room.connect(credentials.serverUrl, credentials.participantToken, { autoSubscribe: true });
      if (settings.outputDeviceId !== 'default') await room.switchActiveDevice('audiooutput', settings.outputDeviceId).catch(() => false);
      await room.localParticipant.setMicrophoneEnabled(true, audioCaptureOptions());
      if (settings.voiceMode === 'ptt') await room.localParticipant.setMicrophoneEnabled(false);
      refreshRoomState(); setStatus(`Connected to ${roomName}.`); void refreshPresence(true);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to connect.';
      await disconnect(); setStatus(message);
    }
  }

  async function toggleMicrophone() {
    const room = roomRef.current;
    if (!room || deafened || settings.voiceMode === 'ptt') return;
    try { await room.localParticipant.setMicrophoneEnabled(!micEnabled, audioCaptureOptions()); refreshRoomState(); }
    catch (error) { setStatus(error instanceof Error ? error.message : 'Unable to change microphone state.'); }
  }

  async function toggleDeafen() {
    const room = roomRef.current; if (!room) return;
    const next = !deafened;
    if (next) { setMicBeforeDeafen(micEnabled); await room.localParticipant.setMicrophoneEnabled(false); }
    else if (micBeforeDeafen && settings.voiceMode === 'vad') await room.localParticipant.setMicrophoneEnabled(true, audioCaptureOptions());
    setDeafened(next); refreshRoomState();
  }

  async function toggleCamera() {
    const room = roomRef.current; if (!room) return;
    try {
      const resolution = QUALITY[settings.cameraQuality];
      await room.localParticipant.setCameraEnabled(!cameraEnabled, { deviceId: settings.cameraDeviceId === 'default' ? undefined : settings.cameraDeviceId, resolution: { ...resolution, frameRate: 30 } });
      refreshRoomState();
    } catch (error) { setStatus(error instanceof Error ? error.message : 'Unable to change camera state.'); }
  }

  async function startScreenShare(choice: ScreenShareChoice) {
    const room = roomRef.current; if (!room) throw new Error('Connect to a room first.');
    await window.icanttalk.selectDisplaySource(choice.sourceId);
    const resolution = QUALITY[choice.resolution];
    const maxBitrate = choice.resolution === '480p' ? (choice.fps === 60 ? 2_200_000 : 1_400_000) : choice.resolution === '720p' ? (choice.fps === 60 ? 4_500_000 : 2_800_000) : (choice.fps === 60 ? 8_000_000 : 5_000_000);
    await room.localParticipant.setScreenShareEnabled(true, { audio: choice.shareAudio, resolution: { ...resolution, frameRate: choice.fps } }, {
      simulcast: true, degradationPreference: 'maintain-resolution', screenShareEncoding: { maxBitrate, maxFramerate: choice.fps },
    });
    refreshRoomState(); setStatus(`Sharing at ${choice.resolution}, ${choice.fps} FPS${choice.shareAudio ? ' with system audio' : ''}.`);
  }

  async function stopScreenShare() {
    const room = roomRef.current; if (!room) return;
    await room.localParticipant.setScreenShareEnabled(false); refreshRoomState(); setStatus('Screen sharing stopped.');
  }

  async function toggleScreenPause() {
    const publication = roomRef.current?.localParticipant.getTrackPublication(Track.Source.ScreenShare);
    const track = publication?.track; if (!track) return;
    if (publication.isMuted) { await track.unmute(); setStatus('Screen sharing resumed.'); }
    else { await track.mute(); setStatus('Screen sharing paused.'); }
    refreshRoomState();
  }

  async function toggleWatching(participant: AnyParticipant, publication: RemoteTrackPublication, next: boolean) {
    if (participant.isLocal) return;
    try {
      await publication.setSubscribed(next);
      if (publication.source === Track.Source.ScreenShare) {
        const screenAudio = findPublication(participant, Track.Source.ScreenShareAudio);
        if (screenAudio instanceof RemoteTrackPublication) await screenAudio.setSubscribed(next);
      }
      setWatching((current) => ({ ...current, [publication.trackSid]: next }));
      refreshRoomState();
    } catch (error) { setStatus(error instanceof Error ? error.message : 'Unable to change stream subscription.'); }
  }

  useEffect(() => {
    let pressed = false;
    const keyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const typing = target?.matches('input, textarea, select, button') || target?.isContentEditable;
      if (typing || event.repeat || pressed || event.code !== settings.pttKey || settings.voiceMode !== 'ptt' || deafened || !roomRef.current) return;
      pressed = true; event.preventDefault(); void roomRef.current.localParticipant.setMicrophoneEnabled(true, audioCaptureOptions()).then(refreshRoomState);
    };
    const keyUp = (event: KeyboardEvent) => {
      if (!pressed || event.code !== settings.pttKey || !roomRef.current) return;
      pressed = false; event.preventDefault(); void roomRef.current.localParticipant.setMicrophoneEnabled(false).then(refreshRoomState);
    };
    const blur = () => { if (pressed && roomRef.current) { pressed = false; void roomRef.current.localParticipant.setMicrophoneEnabled(false).then(refreshRoomState); } };
    window.addEventListener('keydown', keyDown); window.addEventListener('keyup', keyUp); window.addEventListener('blur', blur);
    return () => { window.removeEventListener('keydown', keyDown); window.removeEventListener('keyup', keyUp); window.removeEventListener('blur', blur); };
  }, [deafened, refreshRoomState, settings]);

  async function saveSettings(saved: PublicSettings) {
    const previous = settings;
    setSettings(saved);
    const room = roomRef.current;
    if (!room) { void refreshPresence(true); return; }
    if (saved.outputDeviceId !== 'default') await room.switchActiveDevice('audiooutput', saved.outputDeviceId).catch(() => false);
    if (krispRef.current) await krispRef.current.setEnabled(saved.noiseMode === 'krisp');
    if (micEnabled && (previous.inputDeviceId !== saved.inputDeviceId || previous.noiseMode !== saved.noiseMode || previous.echoCancellation !== saved.echoCancellation || previous.autoGainControl !== saved.autoGainControl)) {
      await room.localParticipant.setMicrophoneEnabled(false);
      await room.localParticipant.setMicrophoneEnabled(true, audioCaptureOptions(saved));
    }
    void refreshPresence(true);
  }

  const remoteParticipants = useMemo(() => participants.filter((participant): participant is RemoteParticipant => !participant.isLocal), [participants]);
  const screenSharers = useMemo(() => participants.filter(hasScreenShare), [participants]);
  const isConnected = connectionState === ConnectionState.Connected;
  const currentPreview = presence.find((room) => room.name === currentRoom);

  return (
    <div className="app-shell">
      <aside className="brand-rail">
        <img className="brand-mark-image" src="./app-icon.png" alt="iCANTTalk" />
        <div className="rail-spacer" />
        <button className="rail-button" type="button" onClick={() => setSettingsOpen(true)} aria-label="Settings"><Settings size={21} /></button>
      </aside>

      <aside className="channel-sidebar">
        <header className="workspace-header"><div><strong>iCANTTalk</strong><span>Voice workspace</span></div><ChevronDown size={18} /></header>
        <section className="channel-list">
          <div className="channel-section-title"><span>VOICE ROOMS</span><button type="button" className="presence-refresh" onClick={() => void refreshPresence(false)} title="Refresh room previews"><RefreshCw size={14} className={presenceBusy ? 'spin' : ''} /></button></div>
          {ROOMS.map((roomName) => {
            const preview = presence.find((item) => item.name === roomName) || { name: roomName, participants: [] };
            return <div className="room-block" key={roomName}>
              <button type="button" className={`room-button ${currentRoom === roomName ? 'active' : ''}`} onClick={() => void connectToRoom(roomName)} disabled={connectionState === ConnectionState.Connecting}>
                <Volume2 size={19} /><span>{roomName}</span><span className="room-count">{preview.participants.length}</span>{currentRoom === roomName && isConnected && <span className="live-dot" />}
              </button>
              <div className="room-preview-list">
                {preview.participants.map((person) => <div className="room-preview-user" key={`${roomName}-${person.identity}`} title={`${person.name} · ${person.platform}`}><Avatar avatarId={person.avatarId} label={person.name} className="avatar-preview" /><span>{person.name}</span>{person.camera && <Camera size={12} />}{person.screenShare && <MonitorUp size={12} />}</div>)}
                {!preview.participants.length && <span className="room-empty-preview">Empty</span>}
              </div>
            </div>;
          })}
        </section>
        <footer className="profile-strip"><Avatar avatarId={settings.avatarId} label={settings.username || '?'} className="avatar-small" /><div className="profile-copy"><strong>{settings.username || 'Set your name'}</strong><span>{isConnected ? currentRoom : 'Offline'}</span></div><button className="icon-button subtle" type="button" onClick={() => setSettingsOpen(true)} aria-label="Edit settings"><Settings size={18} /></button></footer>
      </aside>

      <main className="main-panel">
        <header className="room-header"><div className="room-title"><Volume2 size={21} /><div><strong>{currentRoom || 'Voice rooms'}</strong><span>{status}</span></div></div><div className="header-status"><ShieldCheck size={17} /><span>{settings.noiseMode === 'krisp' ? `Krisp: ${krispState}` : 'LiveKit secured'}</span><span className={`connection-pill ${isConnected ? 'online' : ''}`}>{connectionState}</span></div></header>

        <section className={`stage ${screenSharers.length ? 'has-screens' : ''}`}>
          {!isConnected && <div className="empty-stage"><div className="empty-icon"><Volume2 size={32} /></div><h1>Ready when you are</h1><p>Preview who is online, then click Room 1 or Room 2 to join.</p><div className="room-shortcuts">{ROOMS.map((roomName) => <button className="primary-button" type="button" key={roomName} onClick={() => void connectToRoom(roomName)}>Join {roomName}</button>)}</div></div>}
          {isConnected && screenSharers.length > 0 && <div className="screen-grid">{screenSharers.map((participant) => {
            const key = publicationWatchKey(participant, Track.Source.ScreenShare);
            const isWatching = participant.isLocal ? true : watching[key] !== false;
            return <ScreenShareTile key={`screen-${participant.identity}`} participant={participant} watching={isWatching} onToggleWatching={(publication, next) => void toggleWatching(participant, publication, next)} audioVolume={screenAudioVolumes[participant.identity] ?? 100} audioMuted={screenAudioMuted[participant.identity] ?? false} onAudioVolume={(value) => setScreenAudioVolumes((current) => ({ ...current, [participant.identity]: value }))} onAudioMuted={() => setScreenAudioMuted((current) => ({ ...current, [participant.identity]: !current[participant.identity] }))} />;
          })}</div>}
          {isConnected && <div className={`participant-grid ${screenSharers.length ? 'with-screen' : ''}`}>{participants.map((participant) => {
            const key = publicationWatchKey(participant, Track.Source.Camera);
            return <ParticipantTile key={participant.identity} participant={participant} active={activeSpeakers.has(participant.identity)} avatarId={settings.avatarId} watching={participant.isLocal ? true : watching[key] !== false} onToggleWatching={(publication, next) => void toggleWatching(participant, publication, next)} />;
          })}</div>}
        </section>

        <footer className="control-dock-shell"><div className="control-dock" aria-label="Call controls">
          <ControlButton label={settings.voiceMode === 'ptt' ? `Hold ${settings.pttKey}` : micEnabled ? 'Mute' : 'Unmute'} active={!micEnabled} danger={!micEnabled} disabled={!isConnected || deafened || settings.voiceMode === 'ptt'} onClick={() => void toggleMicrophone()} icon={micEnabled ? <Mic size={21} /> : <MicOff size={21} />} />
          <ControlButton label={deafened ? 'Undeafen' : 'Deafen'} active={deafened} danger={deafened} disabled={!isConnected} onClick={() => void toggleDeafen()} icon={deafened ? <HeadphoneOff size={21} /> : <Headphones size={21} />} />
          <ControlButton label={cameraEnabled ? 'Camera off' : 'Camera on'} active={cameraEnabled} disabled={!isConnected} onClick={() => void toggleCamera()} icon={cameraEnabled ? <Camera size={21} /> : <CameraOff size={21} />} />
          <ControlButton label={screenSharing ? 'Stop sharing' : 'Share screen'} active={screenSharing} disabled={!isConnected} onClick={() => screenSharing ? void stopScreenShare() : setShareOpen(true)} icon={<MonitorUp size={21} />} />
          {screenSharing && <ControlButton label={screenPaused ? 'Resume share' : 'Pause share'} active={screenPaused} onClick={() => void toggleScreenPause()} icon={screenPaused ? <Play size={21} /> : <Pause size={21} />} />}
          <ControlButton label="Settings" onClick={() => setSettingsOpen(true)} icon={<Settings size={21} />} />
          <ControlButton label="Disconnect" danger disabled={!isConnected && connectionState !== ConnectionState.Connecting} onClick={() => void disconnect()} icon={<LogOut size={21} />} />
        </div></footer>
      </main>

      <aside className="members-sidebar"><header><Users size={18} /><strong>Members — {participants.length || currentPreview?.participants.length || 0}</strong></header>{participants.length > 10 && <div className="capacity-warning">This room is designed for up to 10 participants.</div>}<div className="member-list">{participants.map((participant) => {
        const label = participant.name || participant.identity; const volume = volumes[participant.identity] ?? 100; const isLocal = participant.isLocal;
        return <div className="member-row" key={`member-${participant.identity}`}><Avatar avatarId={participantAvatarId(participant, settings.avatarId)} label={label} className={`avatar-small ${activeSpeakers.has(participant.identity) ? 'speaking' : ''}`} /><div className="member-copy"><strong>{label}{isLocal ? ' (You)' : ''}</strong><span>{activeSpeakers.has(participant.identity) ? 'Speaking' : 'Connected'}</span>{!isLocal && <label className="volume-control"><Volume2 size={13} /><input type="range" min="0" max="100" value={volume} aria-label={`${label} voice volume`} onChange={(event) => setVolumes((current) => ({ ...current, [participant.identity]: Number(event.target.value) }))} /><span>{volume}%</span></label>}</div></div>;
      })}{!participants.length && (currentPreview?.participants || []).map((person) => <div className="member-row" key={`preview-${person.identity}`}><Avatar avatarId={person.avatarId} label={person.name} className="avatar-small" /><div className="member-copy"><strong>{person.name}</strong><span>In {currentRoom || 'room'} · {person.platform}</span></div></div>)}{!participants.length && !currentPreview?.participants.length && <div className="member-empty">Nobody is connected.</div>}</div><footer className="version-label">iCANTTalk {appVersion ? `v${appVersion}` : ''}</footer></aside>

      {remoteParticipants.map((participant) => <ParticipantAudio participant={participant} volume={volumes[participant.identity] ?? 100} deafened={deafened} key={`voice-${participant.identity}`} />)}
      {remoteParticipants.map((participant) => <ScreenShareAudio participant={participant} volume={screenAudioVolumes[participant.identity] ?? 100} muted={screenAudioMuted[participant.identity] ?? false} deafened={deafened} key={`screen-audio-${participant.identity}`} />)}
      {settingsOpen && <SettingsModal settings={settings} onClose={() => setSettingsOpen(false)} onSaved={(saved) => void saveSettings(saved)} />}
      {shareOpen && <ScreenShareModal settings={settings} onClose={() => setShareOpen(false)} onShare={startScreenShare} />}
    </div>
  );
}

function ControlButton({ label, icon, active = false, danger = false, disabled = false, onClick }: { label: string; icon: ReactNode; active?: boolean; danger?: boolean; disabled?: boolean; onClick: () => void }) {
  return <button type="button" className={`control-button ${active ? 'active' : ''} ${danger ? 'danger' : ''}`} disabled={disabled} onClick={onClick} title={label}>{icon}<span>{label}</span></button>;
}

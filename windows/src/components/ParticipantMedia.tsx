import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  LocalParticipant,
  RemoteParticipant,
  RemoteTrackPublication,
  Track,
  TrackPublication,
} from 'livekit-client';
import {
  Eye,
  EyeOff,
  Maximize2,
  MicOff,
  MonitorUp,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import Avatar from './Avatar';
import { AVATAR_IDS } from '../models';

export type AnyParticipant = LocalParticipant | RemoteParticipant;

export function publications(participant: AnyParticipant): TrackPublication[] {
  return Array.from(participant.trackPublications.values());
}

export function findPublication(participant: AnyParticipant, source: Track.Source) {
  return publications(participant).find((publication) => publication.source === source);
}

function deterministicAvatarId(identity: string): string {
  // FNV-1a gives every legacy participant (whose token has no metadata) a stable
  // avatar instead of incorrectly borrowing the local user's avatar.
  let hash = 0x811c9dc5;
  for (let index = 0; index < identity.length; index += 1) {
    hash ^= identity.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return AVATAR_IDS[Math.abs(hash >>> 0) % AVATAR_IDS.length];
}

export function participantAvatarId(participant: AnyParticipant, localAvatarId: string): string {
  if (participant.isLocal) return AVATAR_IDS.includes(localAvatarId) ? localAvatarId : AVATAR_IDS[0];

  try {
    const metadata = JSON.parse(participant.metadata || '{}') as {
      avatar_id?: unknown;
      avatarId?: unknown;
      profile?: { avatar_id?: unknown; avatarId?: unknown };
    };
    const candidate = String(
      metadata.avatar_id
      ?? metadata.avatarId
      ?? metadata.profile?.avatar_id
      ?? metadata.profile?.avatarId
      ?? '',
    );
    if (AVATAR_IDS.includes(candidate)) return candidate;
  } catch {
    // Legacy clients can have empty or non-JSON metadata. Use a stable fallback.
  }

  return deterministicAvatarId(participant.identity || participant.sid || 'guest');
}

function VideoTrackView({
  publication,
  fit = true,
  onAspectRatio,
}: {
  publication?: TrackPublication;
  fit?: boolean;
  onAspectRatio?: (ratio: number) => void;
}) {
  const elementRef = useRef<HTMLVideoElement>(null);
  const track = publication?.track;

  useEffect(() => {
    const element = elementRef.current;
    if (!element || !track || track.kind !== Track.Kind.Video) return;
    track.attach(element);
    const updateRatio = () => {
      if (element.videoWidth > 0 && element.videoHeight > 0) {
        onAspectRatio?.(element.videoWidth / element.videoHeight);
      }
    };
    element.addEventListener('loadedmetadata', updateRatio);
    element.addEventListener('resize', updateRatio);
    updateRatio();
    return () => {
      element.removeEventListener('loadedmetadata', updateRatio);
      element.removeEventListener('resize', updateRatio);
      track.detach(element);
    };
  }, [onAspectRatio, track]);

  if (!track || publication?.isMuted) return null;
  return <video ref={elementRef} autoPlay playsInline muted className={fit ? 'media-fit' : 'media-fill'} />;
}

function FullscreenMedia({
  publication,
  label,
  onClose,
}: {
  publication: TrackPublication;
  label: string;
  onClose: () => void;
}) {
  const closeRef = useRef(onClose);

  useEffect(() => {
    closeRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    void window.icanttalk.setNativeFullscreen(true).catch(() => undefined);
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeRef.current();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      void window.icanttalk.setNativeFullscreen(false).catch(() => undefined);
    };
  }, []);

  return createPortal(
    <div className="media-fullscreen-overlay" role="dialog" aria-modal="true" aria-label={label}>
      <VideoTrackView publication={publication} fit />
      <button
        type="button"
        className="fullscreen-exit-button"
        onClick={onClose}
        title="Exit fullscreen"
        aria-label="Exit fullscreen"
      >
        <X size={26} />
      </button>
    </div>,
    document.body,
  );
}

function AudioTrackView({
  publication,
  volume,
  muted,
}: {
  publication?: TrackPublication;
  volume: number;
  muted: boolean;
}) {
  const elementRef = useRef<HTMLAudioElement>(null);
  const track = publication?.track;

  useEffect(() => {
    const element = elementRef.current;
    if (!element || !track || track.kind !== Track.Kind.Audio) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);

  useEffect(() => {
    const normalized = Math.max(0, Math.min(1, volume / 100));
    if (track?.kind === Track.Kind.Audio && 'setVolume' in track) {
      (track as { setVolume(value: number): void }).setVolume(muted ? 0 : normalized);
    }
    if (elementRef.current) {
      elementRef.current.volume = normalized;
      elementRef.current.muted = muted;
    }
  }, [muted, track, volume]);

  return track ? <audio ref={elementRef} autoPlay /> : null;
}

export function ParticipantAudio({
  participant,
  volume,
  deafened,
}: {
  participant: RemoteParticipant;
  volume: number;
  deafened: boolean;
}) {
  const publication = findPublication(participant, Track.Source.Microphone);
  return <AudioTrackView publication={publication} volume={volume} muted={deafened} />;
}

export function ScreenShareAudio({
  participant,
  volume,
  muted,
  deafened,
}: {
  participant: RemoteParticipant;
  volume: number;
  muted: boolean;
  deafened: boolean;
}) {
  const publication = findPublication(participant, Track.Source.ScreenShareAudio);
  return <AudioTrackView publication={publication} volume={volume} muted={muted || deafened} />;
}

export function ParticipantTile({
  participant,
  active,
  avatarId,
  watching,
  onToggleWatching,
}: {
  participant: AnyParticipant;
  active: boolean;
  avatarId: string;
  watching: boolean;
  onToggleWatching: (publication: RemoteTrackPublication, watching: boolean) => void;
}) {
  const camera = findPublication(participant, Track.Source.Camera);
  const microphone = findPublication(participant, Track.Source.Microphone);
  const isRemote = !participant.isLocal;
  const cameraPublished = Boolean(camera && !camera.isMuted);
  const cameraVisible = Boolean(cameraPublished && camera.track && (!isRemote || watching));
  const micMuted = !microphone || microphone.isMuted;
  const label = participant.name || participant.identity;
  const [ratio, setRatio] = useState(16 / 10);
  const [fullscreen, setFullscreen] = useState(false);
  const resolvedAvatar = participantAvatarId(participant, avatarId);

  useEffect(() => {
    if (!cameraVisible) setFullscreen(false);
  }, [cameraVisible]);

  return (
    <>
      <article className={`participant-tile ${active ? 'speaking' : ''}`}>
        <div
          className={`participant-video ${cameraVisible ? 'has-video' : ''}`}
          style={{ aspectRatio: cameraVisible ? String(Math.max(0.45, Math.min(2.4, ratio))) : '16 / 10' }}
        >
          {cameraVisible ? (
            <VideoTrackView publication={camera} onAspectRatio={setRatio} />
          ) : (
            <Avatar avatarId={resolvedAvatar} label={label} className="avatar-large" />
          )}
          {!cameraVisible && cameraPublished && isRemote && !watching && (
            <div className="not-watching-overlay">Camera hidden on this device</div>
          )}
          {!cameraPublished && <MicOff className="camera-off" size={18} aria-label="Camera off" />}
          <div className="media-actions">
            {cameraPublished && isRemote && camera instanceof RemoteTrackPublication && (
              <button
                type="button"
                className="media-action-button"
                onClick={() => onToggleWatching(camera, !watching)}
                title={watching ? 'Stop watching camera' : 'Start watching camera'}
              >
                {watching ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            )}
            {cameraVisible && camera && (
              <button
                type="button"
                className="media-action-button"
                onClick={() => setFullscreen(true)}
                title="Fullscreen camera"
              >
                <Maximize2 size={17} />
              </button>
            )}
          </div>
        </div>
        <footer>
          <span className="participant-name">{label}</span>
          {micMuted && <MicOff size={16} aria-label="Microphone muted" />}
        </footer>
      </article>
      {fullscreen && camera && (
        <FullscreenMedia publication={camera} label={`${label}'s camera`} onClose={() => setFullscreen(false)} />
      )}
    </>
  );
}

export function ScreenShareTile({
  participant,
  watching,
  onToggleWatching,
  audioVolume,
  audioMuted,
  onAudioVolume,
  onAudioMuted,
}: {
  participant: AnyParticipant;
  watching: boolean;
  onToggleWatching: (publication: RemoteTrackPublication, watching: boolean) => void;
  audioVolume: number;
  audioMuted: boolean;
  onAudioVolume: (value: number) => void;
  onAudioMuted: () => void;
}) {
  const publication = findPublication(participant, Track.Source.ScreenShare);
  const audioPublication = findPublication(participant, Track.Source.ScreenShareAudio);
  const isRemote = !participant.isLocal;
  const visible = Boolean(publication?.track && !publication.isMuted && (!isRemote || watching));
  const label = participant.name || participant.identity;
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    if (!visible) setFullscreen(false);
  }, [visible]);

  return (
    <>
      <article className="screen-tile">
        <header>
          <MonitorUp size={17} />
          <span>{label}&apos;s screen</span>
          {publication?.isMuted && <span className="paused-badge">Paused</span>}
          <span className="screen-header-spacer" />
          {isRemote && publication instanceof RemoteTrackPublication && (
            <button
              type="button"
              className="media-action-button"
              onClick={() => onToggleWatching(publication, !watching)}
              title={watching ? 'Stop watching screen' : 'Start watching screen'}
            >
              {watching ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          )}
          {visible && publication && (
            <button
              type="button"
              className="media-action-button"
              onClick={() => setFullscreen(true)}
              title="Fullscreen screen share"
            >
              <Maximize2 size={16} />
            </button>
          )}
        </header>
        <div className="screen-video">
          {visible ? (
            <VideoTrackView publication={publication} fit />
          ) : (
            <button
              type="button"
              className="start-watching-card"
              onClick={() => {
                if (publication instanceof RemoteTrackPublication) onToggleWatching(publication, true);
              }}
            >
              <Eye size={24} />
              <span>{publication?.isMuted ? 'Screen share is paused' : 'Start watching this screen'}</span>
            </button>
          )}
        </div>
        {isRemote && audioPublication && (
          <div className="screen-audio-control">
            <button type="button" className="media-action-button" onClick={onAudioMuted} title="Mute shared audio">
              {audioMuted || audioVolume === 0 ? <VolumeX size={16} /> : <Volume2 size={16} />}
            </button>
            <input
              type="range"
              min="0"
              max="100"
              value={audioVolume}
              onChange={(event) => onAudioVolume(Number(event.target.value))}
              aria-label={`${label} shared audio volume`}
            />
            <span>{audioMuted ? 'Muted' : `${audioVolume}%`}</span>
          </div>
        )}
      </article>
      {fullscreen && publication && (
        <FullscreenMedia publication={publication} label={`${label}'s screen`} onClose={() => setFullscreen(false)} />
      )}
    </>
  );
}

export function hasScreenShare(participant: AnyParticipant) {
  const publication = findPublication(participant, Track.Source.ScreenShare);
  return Boolean(publication && !publication.isMuted);
}

export function publicationWatchKey(participant: AnyParticipant, source: Track.Source): string {
  const publication = findPublication(participant, source);
  return publication?.trackSid || `${participant.identity}:${source}`;
}

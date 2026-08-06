import { useEffect, useMemo, useState } from 'react';
import { AppWindow, Monitor, RefreshCw, Volume2, X } from 'lucide-react';
import type { DisplaySource, PublicSettings } from '../models';

export type ScreenShareChoice = {
  sourceId: string;
  resolution: PublicSettings['screenResolution'];
  fps: 30 | 60;
  shareAudio: boolean;
};

export default function ScreenShareModal({
  settings,
  onClose,
  onShare,
}: {
  settings: PublicSettings;
  onClose: () => void;
  onShare: (choice: ScreenShareChoice) => Promise<void>;
}) {
  const [sources, setSources] = useState<DisplaySource[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [kind, setKind] = useState<'all' | 'screen' | 'window'>('all');
  const [resolution, setResolution] = useState(settings.screenResolution);
  const [fps, setFps] = useState<30 | 60>(settings.screenFps);
  const [shareAudio, setShareAudio] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function loadSources() {
    setMessage('');
    try {
      const result = await window.icanttalk.listDisplaySources();
      setSources(result);
      setSelectedId((current) => current || result[0]?.id || '');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to list screens and windows.');
    }
  }

  useEffect(() => {
    void loadSources();
  }, []);

  const filtered = useMemo(
    () => sources.filter((source) => kind === 'all' || source.kind === kind),
    [sources, kind],
  );

  async function share() {
    if (!selectedId) {
      setMessage('Choose a screen or application window.');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      await onShare({ sourceId: selectedId, resolution, fps, shareAudio });
      onClose();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Screen sharing failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="share-modal" role="dialog" aria-modal="true" aria-label="Share your screen">
        <header className="modal-header">
          <div>
            <h2>Share your screen</h2>
            <p>Choose a monitor or an application window.</p>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Close">
            <X size={20} />
          </button>
        </header>

        <div className="share-toolbar">
          <div className="segmented-control compact-tabs">
            <button type="button" className={kind === 'all' ? 'active' : ''} onClick={() => setKind('all')}>All</button>
            <button type="button" className={kind === 'screen' ? 'active' : ''} onClick={() => setKind('screen')}>
              <Monitor size={15} /> Screens
            </button>
            <button type="button" className={kind === 'window' ? 'active' : ''} onClick={() => setKind('window')}>
              <AppWindow size={15} /> Windows
            </button>
          </div>
          <button className="secondary-button compact" type="button" onClick={() => void loadSources()}>
            <RefreshCw size={15} /> Refresh
          </button>
        </div>

        <div className="source-grid">
          {filtered.map((source) => (
            <button
              type="button"
              key={source.id}
              className={`source-card ${selectedId === source.id ? 'selected' : ''}`}
              onClick={() => setSelectedId(source.id)}
            >
              <img src={source.thumbnail} alt={`Preview of ${source.name}`} />
              <span>
                {source.kind === 'screen' ? <Monitor size={15} /> : <AppWindow size={15} />}
                <strong>{source.name}</strong>
              </span>
            </button>
          ))}
          {!filtered.length && <div className="empty-sources">No shareable sources found.</div>}
        </div>

        <div className="share-options">
          <label>
            Resolution
            <select value={resolution} onChange={(event) => setResolution(event.target.value as PublicSettings['screenResolution'])}>
              <option value="480p">480p</option>
              <option value="720p">720p</option>
              <option value="1080p">1080p</option>
            </select>
          </label>
          <label>
            Frame rate
            <select value={fps} onChange={(event) => setFps(Number(event.target.value) as 30 | 60)}>
              <option value={30}>30 FPS</option>
              <option value={60}>60 FPS</option>
            </select>
          </label>
          <label className="check-row share-audio-option">
            <input type="checkbox" checked={shareAudio} onChange={(event) => setShareAudio(event.target.checked)} />
            <Volume2 size={17} />
            <span>Share computer audio</span>
          </label>
        </div>

        <footer className="modal-footer">
          <div className="form-message" role="status">{message}</div>
          <button className="secondary-button" type="button" onClick={onClose}>Cancel</button>
          <button className="primary-button" type="button" onClick={() => void share()} disabled={busy || !selectedId}>
            {busy ? 'Starting…' : 'Go live'}
          </button>
        </footer>
      </section>
    </div>
  );
}

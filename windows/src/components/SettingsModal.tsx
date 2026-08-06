import { useEffect, useState } from 'react';
import { KeyRound, RefreshCw, X } from 'lucide-react';
import { AVATAR_IDS, avatarUrl, type PublicSettings } from '../models';

type DeviceGroups = {
  audioInputs: MediaDeviceInfo[];
  audioOutputs: MediaDeviceInfo[];
  cameras: MediaDeviceInfo[];
};

const EMPTY_DEVICES: DeviceGroups = { audioInputs: [], audioOutputs: [], cameras: [] };

export default function SettingsModal({
  settings,
  onClose,
  onSaved,
}: {
  settings: PublicSettings;
  onClose: () => void;
  onSaved: (settings: PublicSettings) => void;
}) {
  const [draft, setDraft] = useState(settings);
  const [accessKey, setAccessKey] = useState('');
  const [devices, setDevices] = useState<DeviceGroups>(EMPTY_DEVICES);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [capturingKey, setCapturingKey] = useState(false);

  useEffect(() => { void enumerateDevices(false); }, []);

  async function enumerateDevices(requestPermission: boolean) {
    setMessage('');
    let stream: MediaStream | undefined;
    try {
      if (requestPermission) stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
      const all = await navigator.mediaDevices.enumerateDevices();
      setDevices({
        audioInputs: all.filter((device) => device.kind === 'audioinput'),
        audioOutputs: all.filter((device) => device.kind === 'audiooutput'),
        cameras: all.filter((device) => device.kind === 'videoinput'),
      });
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to enumerate media devices.');
    } finally {
      stream?.getTracks().forEach((track) => track.stop());
    }
  }

  async function save() {
    setBusy(true);
    setMessage('');
    try {
      if (!draft.username.trim()) throw new Error('Enter a username.');
      if (!draft.endpointUrl.trim()) throw new Error('Enter the Django token endpoint URL.');
      if (!draft.accessKeyConfigured && !accessKey.trim()) throw new Error('Enter the pre-install access key.');
      if (accessKey.trim()) await window.icanttalk.setAccessKey(accessKey);
      const saved = await window.icanttalk.saveSettings(draft);
      onSaved({ ...saved, accessKeyConfigured: saved.accessKeyConfigured || Boolean(accessKey) });
      onClose();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to save settings.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="settings-modal" role="dialog" aria-modal="true" aria-label="Settings">
        <header className="modal-header">
          <div>
            <h2>Settings</h2>
            <p>Profile and device changes are saved locally. Audio processing updates apply immediately when possible.</p>
          </div>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Close settings"><X size={20} /></button>
        </header>

        <div className="settings-scroll">
          <section className="settings-section">
            <h3>Profile & connection</h3>
            <label>
              Display name
              <input value={draft.username} maxLength={40} onChange={(event) => setDraft({ ...draft, username: event.target.value })} placeholder="Your name" />
            </label>
            <label>
              Django token endpoint
              <input type="url" value={draft.endpointUrl} onChange={(event) => setDraft({ ...draft, endpointUrl: event.target.value })} placeholder="https://example.com/api/livekit/token/" />
            </label>
            <label>
              Access key {draft.accessKeyConfigured && <span className="configured">Configured</span>}
              <div className="input-with-icon">
                <KeyRound size={17} />
                <input type="password" value={accessKey} onChange={(event) => setAccessKey(event.target.value)} placeholder={draft.accessKeyConfigured ? 'Enter only to replace it' : 'Required'} />
              </div>
            </label>
          </section>

          <section className="settings-section">
            <h3>Profile picture</h3>
            <p className="section-help">A random picture is selected on first launch. You can change it at any time.</p>
            <div className="avatar-picker" role="radiogroup" aria-label="Profile picture">
              {AVATAR_IDS.map((avatarId) => (
                <button
                  key={avatarId}
                  type="button"
                  role="radio"
                  aria-checked={draft.avatarId === avatarId}
                  className={`avatar-choice ${draft.avatarId === avatarId ? 'selected' : ''}`}
                  onClick={() => setDraft({ ...draft, avatarId })}
                >
                  <img src={avatarUrl(avatarId)} alt={`Profile picture ${Number(avatarId)}`} />
                </button>
              ))}
            </div>
          </section>

          <section className="settings-section">
            <div className="section-heading-row">
              <h3>Devices</h3>
              <button className="secondary-button compact" type="button" onClick={() => void enumerateDevices(true)}><RefreshCw size={15} /> Refresh permissions</button>
            </div>
            <div className="two-column">
              <DeviceSelect label="Microphone" value={draft.inputDeviceId} devices={devices.audioInputs} onChange={(value) => setDraft({ ...draft, inputDeviceId: value })} />
              <DeviceSelect label="Speakers" value={draft.outputDeviceId} devices={devices.audioOutputs} onChange={(value) => setDraft({ ...draft, outputDeviceId: value })} />
              <DeviceSelect label="Camera" value={draft.cameraDeviceId} devices={devices.cameras} onChange={(value) => setDraft({ ...draft, cameraDeviceId: value })} />
              <label>
                Camera quality
                <select value={draft.cameraQuality} onChange={(event) => setDraft({ ...draft, cameraQuality: event.target.value as PublicSettings['cameraQuality'] })}>
                  <option value="480p">480p</option><option value="720p">720p</option><option value="1080p">1080p</option>
                </select>
              </label>
            </div>
          </section>

          <section className="settings-section">
            <h3>Voice</h3>
            <div className="segmented-control" role="group" aria-label="Voice mode">
              <button type="button" className={draft.voiceMode === 'vad' ? 'active' : ''} onClick={() => setDraft({ ...draft, voiceMode: 'vad' })}>Voice activity</button>
              <button type="button" className={draft.voiceMode === 'ptt' ? 'active' : ''} onClick={() => setDraft({ ...draft, voiceMode: 'ptt' })}>Push to talk</button>
            </div>
            {draft.voiceMode === 'ptt' && (
              <label>
                Push-to-talk key
                <button type="button" className={`key-capture ${capturingKey ? 'capturing' : ''}`} onClick={() => setCapturingKey(true)} onKeyDown={(event) => {
                  if (!capturingKey) return;
                  event.preventDefault();
                  setDraft({ ...draft, pttKey: event.code });
                  setCapturingKey(false);
                }}>{capturingKey ? 'Press a key…' : draft.pttKey}</button>
                <small>Hold this key while the iCANTTalk window is focused.</small>
              </label>
            )}
            <label>
              Noise filtering
              <select value={draft.noiseMode} onChange={(event) => setDraft({ ...draft, noiseMode: event.target.value as PublicSettings['noiseMode'], noiseSuppression: event.target.value !== 'off' })}>
                <option value="off">Off</option>
                <option value="standard">Standard WebRTC filtering</option>
                <option value="krisp">Krisp enhanced filtering</option>
              </select>
              <small>Krisp removes stronger ambient noise and downloads its model the first time it is enabled.</small>
            </label>
            <div className="check-grid">
              <CheckRow label="Echo cancellation" checked={draft.echoCancellation} onChange={(checked) => setDraft({ ...draft, echoCancellation: checked })} />
              <CheckRow label="Automatic gain control" checked={draft.autoGainControl} onChange={(checked) => setDraft({ ...draft, autoGainControl: checked })} />
            </div>
          </section>

          <section className="settings-section">
            <h3>Screen sharing defaults</h3>
            <div className="two-column">
              <label>Resolution<select value={draft.screenResolution} onChange={(event) => setDraft({ ...draft, screenResolution: event.target.value as PublicSettings['screenResolution'] })}><option value="480p">480p</option><option value="720p">720p</option><option value="1080p">1080p</option></select></label>
              <label>Frame rate<select value={draft.screenFps} onChange={(event) => setDraft({ ...draft, screenFps: Number(event.target.value) as 30 | 60 })}><option value={30}>30 FPS</option><option value={60}>60 FPS</option></select></label>
            </div>
          </section>
        </div>

        <footer className="modal-footer">
          <div className="form-message" role="status">{message}</div>
          <button className="secondary-button" type="button" onClick={onClose}>Cancel</button>
          <button className="primary-button" type="button" onClick={() => void save()} disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
        </footer>
      </section>
    </div>
  );
}

function DeviceSelect({ label, value, devices, onChange }: { label: string; value: string; devices: MediaDeviceInfo[]; onChange: (value: string) => void }) {
  return <label>{label}<select value={value} onChange={(event) => onChange(event.target.value)}><option value="default">System default</option>{devices.filter((device) => device.deviceId !== 'default').map((device, index) => <option value={device.deviceId} key={device.deviceId}>{device.label || `${label} ${index + 1}`}</option>)}</select></label>;
}

function CheckRow({ label, checked, onChange }: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return <label className="check-row"><input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} /><span>{label}</span></label>;
}

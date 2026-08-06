import { LocalAudioTrack, RoomEvent, Track } from 'livekit-client';
import type { LocalTrackPublication, Room } from 'livekit-client';

export type KrispState = 'disabled' | 'initializing' | 'enabled' | 'unsupported' | 'error';

type Processor = {
  setEnabled(enabled: boolean): Promise<void>;
  isEnabled(): boolean;
};

export class KrispController {
  private processor?: Processor;
  private track?: LocalAudioTrack;
  private desired = false;
  private state: KrispState = 'disabled';
  private listener?: (state: KrispState) => void;

  onStateChange(listener: (state: KrispState) => void): void {
    this.listener = listener;
    listener(this.state);
  }

  bind(room: Room): void {
    room.on(RoomEvent.LocalTrackPublished, (publication: LocalTrackPublication) => {
      void this.attach(publication);
    });
  }

  async setEnabled(enabled: boolean): Promise<void> {
    this.desired = enabled;
    if (!enabled) {
      if (this.processor) await this.processor.setEnabled(false);
      this.setState('disabled');
      return;
    }
    if (this.processor) {
      await this.processor.setEnabled(true);
      this.setState('enabled');
      return;
    }
    this.setState('initializing');
  }

  async attach(publication: LocalTrackPublication): Promise<void> {
    if (publication.source !== Track.Source.Microphone || !(publication.track instanceof LocalAudioTrack)) return;
    if (publication.track === this.track && this.processor) {
      await this.processor.setEnabled(this.desired);
      this.setState(this.desired ? 'enabled' : 'disabled');
      return;
    }

    this.track = publication.track;
    if (!this.desired) return;

    try {
      this.setState('initializing');
      const module = await import('@livekit/krisp-noise-filter');
      if (!module.isKrispNoiseFilterSupported()) {
        this.setState('unsupported');
        return;
      }
      const processor = module.KrispNoiseFilter() as Processor;
      await publication.track.setProcessor(processor);
      await processor.setEnabled(true);
      this.processor = processor;
      this.setState('enabled');
    } catch (error) {
      console.error('Krisp initialization failed', error);
      this.setState('error');
    }
  }

  async dispose(): Promise<void> {
    try {
      if (this.processor) await this.processor.setEnabled(false);
      if (this.track) await this.track.stopProcessor();
    } catch {
      // Track may already have been stopped during disconnect.
    }
    this.processor = undefined;
    this.track = undefined;
    this.setState('disabled');
  }

  private setState(state: KrispState): void {
    this.state = state;
    this.listener?.(state);
  }
}

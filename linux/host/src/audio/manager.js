import { execFileSync, execSync } from 'node:child_process';

export class AudioMediaManager {
  getAudioState() {
    try {
      const out = execFileSync('wpctl', ['get-volume', '@DEFAULT_AUDIO_SINK@'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 2000
      }).trim();

      // Output format: "Volume: 0.53" or "Volume: 0.53 [MUTED]"
      const match = out.match(/Volume:\s+([0-9.]+)(\s+\[MUTED\])?/i);
      if (match) {
        const volFloat = parseFloat(match[1]);
        const isMuted = !!match[2];
        return {
          volume: Math.round(volFloat * 100),
          muted: isMuted,
          sink: 'Default Audio Sink'
        };
      }
      return { volume: 50, muted: false, sink: 'Default Audio Sink' };
    } catch (err) {
      return { volume: 0, muted: false, sink: 'Unknown', error: err.message };
    }
  }

  setVolume(percent) {
    const safePercent = Math.max(0, Math.min(parseInt(percent, 10) || 0, 150));
    const volDecimal = (safePercent / 100).toFixed(2);
    try {
      execFileSync('wpctl', ['set-volume', '@DEFAULT_AUDIO_SINK@', String(volDecimal)], {
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 2000
      });
      return this.getAudioState();
    } catch (err) {
      throw new Error(`Failed to set volume: ${err.message}`);
    }
  }

  toggleMute() {
    try {
      execFileSync('wpctl', ['set-mute', '@DEFAULT_AUDIO_SINK@', 'toggle'], {
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 2000
      });
      return this.getAudioState();
    } catch (err) {
      throw new Error(`Failed to toggle mute: ${err.message}`);
    }
  }

  getActivePlayer() {
    try {
      const out = execSync('busctl --user list --no-legend 2>/dev/null | grep org.mpris.MediaPlayer2 || true', {
        encoding: 'utf8',
        timeout: 2000
      });
      const lines = out.split('\n').filter(l => l.trim().length > 0);
      if (lines.length === 0) return null;
      const firstLine = lines[0].trim();
      const parts = firstLine.split(/\s+/);
      return parts[0] || null;
    } catch {
      return null;
    }
  }

  getMediaState() {
    const player = this.getActivePlayer();
    if (!player) {
      return {
        available: false,
        player: null,
        playbackStatus: 'Stopped',
        title: '',
        artist: ''
      };
    }

    let playbackStatus = 'Stopped';
    let title = '';
    let artist = '';

    try {
      const statusOut = execFileSync('busctl', [
        '--user', 'get-property', player,
        '/org/mpris/MediaPlayer2',
        'org.mpris.MediaPlayer2.Player',
        'PlaybackStatus'
      ], { encoding: 'utf8', timeout: 2000 }).trim();
      const match = statusOut.match(/"([^"]+)"/);
      if (match) playbackStatus = match[1];
    } catch {
      // ignore
    }

    try {
      const metaOut = execFileSync('busctl', [
        '--user', 'get-property', player,
        '/org/mpris/MediaPlayer2',
        'org.mpris.MediaPlayer2.Player',
        'Metadata'
      ], { encoding: 'utf8', timeout: 2000 }).trim();
      
      const titleMatch = metaOut.match(/xesam:title"\s+s\s+"([^"]+)"/);
      if (titleMatch) title = titleMatch[1];
      const artistMatch = metaOut.match(/xesam:artist"\s+as\s+\d+\s+"([^"]+)"/);
      if (artistMatch) artist = artistMatch[1];
    } catch {
      // ignore
    }

    return {
      available: true,
      player,
      playbackStatus,
      title,
      artist
    };
  }

  mediaControl(action) {
    const player = this.getActivePlayer();
    if (!player) {
      return { success: false, message: 'No active MPRIS media player found' };
    }

    const actionMap = {
      play: 'Play',
      pause: 'Pause',
      play_pause: 'PlayPause',
      toggle: 'PlayPause',
      next: 'Next',
      previous: 'Previous',
      prev: 'Previous',
      stop: 'Stop'
    };

    const method = actionMap[action.toLowerCase()];
    if (!method) {
      throw new Error(`Unsupported media action: ${action}`);
    }

    try {
      execFileSync('busctl', [
        '--user', 'call', player,
        '/org/mpris/MediaPlayer2',
        'org.mpris.MediaPlayer2.Player',
        method
      ], { stdio: ['ignore', 'pipe', 'pipe'], timeout: 3000 });
      return { success: true, action: method, player };
    } catch (err) {
      throw new Error(`Media control failed: ${err.message}`);
    }
  }
}

let _audioMediaInstance = null;
export const audioMediaManager = new Proxy({}, {
  get(target, prop) {
    if (!_audioMediaInstance) {
      _audioMediaInstance = new AudioMediaManager();
    }
    const val = _audioMediaInstance[prop];
    return typeof val === 'function' ? val.bind(_audioMediaInstance) : val;
  }
});

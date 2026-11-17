import fs from 'node:fs';
import os from 'node:os';

export class MetricsCollector {
  constructor() {
    this.lastCpuStats = this.readCpuTimes();
  }

  readCpuTimes() {
    try {
      const stat = fs.readFileSync('/proc/stat', 'utf8');
      const firstLine = stat.split('\n')[0];
      const parts = firstLine.trim().split(/\s+/).slice(1).map(Number);
      const idle = parts[3] + (parts[4] || 0); // idle + iowait
      const total = parts.reduce((acc, val) => acc + val, 0);
      return { idle, total };
    } catch (e) {
      return { idle: 0, total: 0 };
    }
  }

  getCpuUsagePercent() {
    const current = this.readCpuTimes();
    const idleDelta = current.idle - this.lastCpuStats.idle;
    const totalDelta = current.total - this.lastCpuStats.total;
    this.lastCpuStats = current;

    if (totalDelta <= 0) return 0;
    const usage = 100 * (1 - idleDelta / totalDelta);
    return Math.max(0, Math.min(100, Math.round(usage * 10) / 10));
  }

  getMemoryUsage() {
    try {
      const meminfo = fs.readFileSync('/proc/meminfo', 'utf8');
      let totalKb = 0;
      let availKb = 0;

      for (const line of meminfo.split('\n')) {
        if (line.startsWith('MemTotal:')) {
          totalKb = parseInt(line.split(/\s+/)[1], 10);
        } else if (line.startsWith('MemAvailable:')) {
          availKb = parseInt(line.split(/\s+/)[1], 10);
        }
      }

      const usedKb = totalKb - availKb;
      const totalMb = Math.round(totalKb / 1024);
      const usedMb = Math.round(usedKb / 1024);
      const percent = totalKb > 0 ? Math.round((usedKb / totalKb) * 1000) / 10 : 0;

      return {
        totalMb,
        usedMb,
        availableMb: Math.round(availKb / 1024),
        percent,
      };
    } catch (e) {
      return { totalMb: 0, usedMb: 0, availableMb: 0, percent: 0 };
    }
  }

  getBatteryStatus() {
    try {
      const powerSupplyDir = '/sys/class/power_supply';
      if (!fs.existsSync(powerSupplyDir)) {
        return { present: false, percent: 100, status: 'AC' };
      }

      const entries = fs.readdirSync(powerSupplyDir);
      const batName = entries.find(e => e.startsWith('BAT'));

      if (!batName) {
        return { present: false, percent: 100, status: 'AC Powered' };
      }

      const batDir = `${powerSupplyDir}/${batName}`;
      const capacity = parseInt(fs.readFileSync(`${batDir}/capacity`, 'utf8').trim(), 10);
      const status = fs.readFileSync(`${batDir}/status`, 'utf8').trim();

      return {
        present: true,
        percent: capacity,
        status, // 'Charging', 'Discharging', 'Full', 'Not charging'
        isCharging: status === 'Charging',
      };
    } catch (e) {
      return { present: false, percent: 100, status: 'Unknown' };
    }
  }

  getDiskUsage() {
    try {
      const home = os.homedir();
      const stats = fs.statfsSync(home);
      const totalBytes = stats.blocks * stats.bsize;
      const freeBytes = stats.bfree * stats.bsize;
      const usedBytes = totalBytes - freeBytes;

      const totalGb = Math.round((totalBytes / (1024 * 1024 * 1024)) * 10) / 10;
      const usedGb = Math.round((usedBytes / (1024 * 1024 * 1024)) * 10) / 10;
      const freeGb = Math.round((freeBytes / (1024 * 1024 * 1024)) * 10) / 10;
      const percent = totalBytes > 0 ? Math.round((usedBytes / totalBytes) * 1000) / 10 : 0;

      return {
        path: home,
        totalGb,
        usedGb,
        freeGb,
        percent,
      };
    } catch (e) {
      return { totalGb: 0, usedGb: 0, freeGb: 0, percent: 0 };
    }
  }

  getUptimeSeconds() {
    try {
      const content = fs.readFileSync('/proc/uptime', 'utf8');
      return Math.round(parseFloat(content.split(' ')[0]));
    } catch (e) {
      return Math.round(os.uptime());
    }
  }

  getSnapshot() {
    return {
      timestamp: Date.now(),
      cpuPercent: this.getCpuUsagePercent(),
      memory: this.getMemoryUsage(),
      battery: this.getBatteryStatus(),
      disk: this.getDiskUsage(),
      uptimeSeconds: this.getUptimeSeconds(),
      loadAverage: os.loadavg(),
      hostname: os.hostname(),
    };
  }
}

export const metricsCollector = new MetricsCollector();

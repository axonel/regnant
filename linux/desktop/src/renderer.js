const hostStatusDot = document.getElementById('hostStatusDot');
const hostStatusText = document.getElementById('hostStatusText');
const pairingCodeDisplay = document.getElementById('pairingCodeDisplay');
const pairingTimerDisplay = document.getElementById('pairingTimerDisplay');
const btnGenCode = document.getElementById('btnGenCode');
const devicesList = document.getElementById('devicesList');
const historyList = document.getElementById('historyList');
const historyCount = document.getElementById('historyCount');
const btnRestartHost = document.getElementById('btnRestartHost');

const appTerminal = document.getElementById('appTerminal');
const appBrave = document.getElementById('appBrave');
const appFiles = document.getElementById('appFiles');
const appCalc = document.getElementById('appCalc');

const metaPort = document.getElementById('metaPort');
const metaVersion = document.getElementById('metaVersion');
const metaFingerprint = document.getElementById('metaFingerprint');

let countdownInterval = null;
let currentRemainingSeconds = 0;

async function refreshStatus() {
  try {
    const status = await window.regnantApi.getStatus();
    if (status.offline || status.error) {
      hostStatusDot.classList.remove('online');
      hostStatusText.textContent = 'HOST OFFLINE';
      return;
    }

    hostStatusDot.classList.add('online');
    hostStatusText.textContent = 'HOST ACTIVE';

    if (status.version) metaVersion.textContent = status.version;
    if (status.certFingerprint) metaFingerprint.textContent = status.certFingerprint.substring(0, 23) + '...';

    // Update pairing code
    if (status.pairingSession) {
      pairingCodeDisplay.textContent = status.pairingSession.code;
      currentRemainingSeconds = status.pairingSession.remainingSeconds;
      pairingTimerDisplay.textContent = `Expires in ${currentRemainingSeconds}s • ${status.pairingSession.attemptsLeft} attempts left`;
    } else {
      pairingCodeDisplay.textContent = '------';
      pairingTimerDisplay.textContent = 'No active code. Click "Generate Code".';
    }

    // Update devices list
    if (status.devices && status.devices.length > 0) {
      devicesList.innerHTML = status.devices.map(d => `
        <div class="device-card">
          <div>
            <div class="device-name">${escapeHtml(d.device_name || 'Android Phone')}</div>
            <div class="device-meta">${escapeHtml(d.device_id.substring(0, 18))}... • Last seen: ${formatTime(d.last_seen)}</div>
          </div>
          <button class="btn btn-sm btn-outline" onclick="revoke('${escapeHtml(d.device_id)}')">Revoke</button>
        </div>
      `).join('');
    } else {
      devicesList.innerHTML = '<div class="empty-state">No paired devices yet</div>';
    }

    // Update apps
    if (status.supportedApplications) {
      if (status.supportedApplications.terminal) appTerminal.textContent = status.supportedApplications.terminal;
      if (status.supportedApplications.brave) appBrave.textContent = status.supportedApplications.brave;
      if (status.supportedApplications.fileManager) appFiles.textContent = status.supportedApplications.fileManager;
      if (status.supportedApplications.calculator) appCalc.textContent = status.supportedApplications.calculator;
    }

    // Update history
    if (status.history && status.history.length > 0) {
      historyCount.textContent = `${status.history.length} entries`;
      historyList.innerHTML = status.history.map(h => `
        <div class="history-card">
          <div>
            <div class="history-command">${escapeHtml(h.raw_command || h.action + ' ' + h.target)}</div>
            <div class="history-sub">${escapeHtml(h.message || '')} &bull; ${formatTime(h.timestamp)}</div>
          </div>
          <div class="history-status ${escapeHtml(h.status)}">${escapeHtml(h.status)}</div>
        </div>
      `).join('');
    } else {
      historyList.innerHTML = '<div class="empty-state">No commands executed yet</div>';
    }

    // Telemetry metrics
    try {
      const metrics = await window.regnantApi.getMetrics();
      if (metrics && !metrics.error) {
        const battEl = document.getElementById('metricBattery');
        const cpuEl = document.getElementById('metricCpu');
        const ramEl = document.getElementById('metricRam');
        if (battEl && metrics.battery) {
          battEl.textContent = `🔋 ${metrics.battery.percent}% ${metrics.battery.charging ? '⚡' : ''}`;
        }
        if (cpuEl && metrics.cpu) {
          cpuEl.textContent = `⚡ CPU ${metrics.cpu.usagePercent}%`;
        }
        if (ramEl && metrics.ram) {
          ramEl.textContent = `💾 RAM ${metrics.ram.usedPercent}%`;
        }
      }
    } catch (e) {}

    // Projects list
    try {
      const projData = await window.regnantApi.getProjects();
      if (projData && projData.projects) {
        const pCountEl = document.getElementById('projectCount');
        const pListEl = document.getElementById('projectsList');
        if (pCountEl) pCountEl.textContent = `${projData.projects.length} workspaces`;
        if (pListEl && projData.projects.length > 0) {
          pListEl.innerHTML = projData.projects.map(p => `
            <div class="device-card" style="margin-bottom: 6px;">
              <div>
                <div class="device-name" style="font-size: 13px;">${escapeHtml(p.name)}</div>
                <div class="device-meta" style="font-family: monospace;">${escapeHtml(p.path)}</div>
              </div>
              <span class="badge" style="font-size: 10px;">${escapeHtml(p.ide || 'auto')}</span>
            </div>
          `).join('');
        }
      }
    } catch (e) {}

    // Approved roots
    try {
      const rootsData = await window.regnantApi.getRoots();
      const rootsListEl = document.getElementById('rootsList');
      if (rootsListEl && rootsData && rootsData.roots) {
        rootsListEl.innerHTML = rootsData.roots.map(r => `
          <div class="device-card" style="margin-bottom: 6px;">
            <div>
              <div class="device-name" style="font-size: 13px;">${escapeHtml(r.label)}</div>
              <div class="device-meta" style="font-family: monospace;">${escapeHtml(r.path)}</div>
            </div>
            <span class="badge" style="font-size: 10px;">Approved</span>
          </div>
        `).join('');
      }
    } catch (e) {}

  } catch (err) {
    hostStatusDot.classList.remove('online');
    hostStatusText.textContent = 'HOST OFFLINE';
  }
}

btnGenCode.addEventListener('click', async () => {
  btnGenCode.disabled = true;
  await window.regnantApi.generatePairingCode();
  await refreshStatus();
  btnGenCode.disabled = false;
});

btnRestartHost.addEventListener('click', async () => {
  btnRestartHost.disabled = true;
  await window.regnantApi.restartHost();
  setTimeout(async () => {
    await refreshStatus();
    btnRestartHost.disabled = false;
  }, 1000);
});

window.revoke = async (deviceId) => {
  if (confirm(`Revoke device access for ${deviceId}?`)) {
    await window.regnantApi.revokeDevice(deviceId);
    await refreshStatus();
  }
};

function formatTime(ts) {
  if (!ts) return 'Never';
  const d = new Date(ts);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function escapeHtml(str) {
  return (str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Initial fetch and start interval
refreshStatus();
setInterval(refreshStatus, 2000);

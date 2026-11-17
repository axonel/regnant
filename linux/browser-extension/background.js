/**
 * Regnant Browser Companion — Service Worker (Manifest V3)
 */

const DEFAULT_WS_URL = 'ws://127.0.0.1:53317/browser-bridge';
let ws = null;
let reconnectTimer = null;

async function getStoredToken() {
  const data = await chrome.storage.local.get(['regnant_token', 'regnant_url']);
  return {
    token: data.regnant_token || 'default-regnant-companion-token',
    url: data.regnant_url || DEFAULT_WS_URL
  };
}

async function connect() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return;
  }

  const { token, url } = await getStoredToken();
  const wsUrl = `${url}?token=${encodeURIComponent(token)}`;

  try {
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      chrome.action.setBadgeText({ text: 'ON' });
      chrome.action.setBadgeBackgroundColor({ color: '#898861' });
      sendTabList();
    };

    ws.onmessage = async (event) => {
      try {
        const msg = JSON.parse(event.data);
        handleMessage(msg);
      } catch (err) {
        console.error('[Regnant Bridge] Failed to parse message', err);
      }
    };

    ws.onclose = () => {
      chrome.action.setBadgeText({ text: 'OFF' });
      chrome.action.setBadgeBackgroundColor({ color: '#7D2826' });
      scheduleReconnect();
    };

    ws.onerror = (err) => {
      ws.close();
    };
  } catch (err) {
    scheduleReconnect();
  }
}

function scheduleReconnect() {
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(connect, 5000);
}

async function handleMessage(msg) {
  const { id, type, payload } = msg;

  try {
    switch (type) {
      case 'GET_TABS': {
        const tabs = await chrome.tabs.query({});
        sendReply(id, {
          tabs: tabs.map(t => ({
            id: t.id,
            windowId: t.windowId,
            title: t.title,
            url: t.url,
            active: t.active,
            favIconUrl: t.favIconUrl
          }))
        });
        break;
      }

      case 'ACTIVATE_TAB': {
        const { tabId, windowId } = payload;
        await chrome.tabs.update(tabId, { active: true });
        if (windowId) {
          await chrome.windows.update(windowId, { focused: true });
        }
        sendReply(id, { success: true });
        break;
      }

      case 'CLOSE_TAB': {
        const { tabId } = payload;
        await chrome.tabs.remove(tabId);
        sendReply(id, { success: true });
        break;
      }

      case 'OPEN_TAB': {
        const { url } = payload;
        const newTab = await chrome.tabs.create({ url: url || 'about:blank' });
        sendReply(id, { success: true, tabId: newTab.id });
        break;
      }

      case 'INSPECT_PAGE': {
        const [activeTab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (!activeTab) {
          sendReply(id, { error: 'No active tab found' });
          return;
        }

        const results = await chrome.scripting.executeScript({
          target: { tabId: activeTab.id },
          func: () => {
            const title = document.title;
            const url = window.location.href;
            const metaDesc = document.querySelector('meta[name="description"]')?.content || '';
            const bodyText = document.body ? document.body.innerText.slice(0, 4000) : '';
            return { title, url, description: metaDesc, content: bodyText };
          }
        });

        sendReply(id, results[0]?.result || { error: 'Execution failed' });
        break;
      }

      case 'GET_DOWNLOADS': {
        const downloads = await chrome.downloads.search({ limit: 20 });
        sendReply(id, { downloads });
        break;
      }

      case 'DOWNLOAD_ACTION': {
        const { downloadId, action } = payload;
        if (action === 'pause') await chrome.downloads.pause(downloadId);
        else if (action === 'resume') await chrome.downloads.resume(downloadId);
        else if (action === 'cancel') await chrome.downloads.cancel(downloadId);
        sendReply(id, { success: true });
        break;
      }

      default:
        sendReply(id, { error: `Unknown action: ${type}` });
    }
  } catch (err) {
    sendReply(id, { error: err.message });
  }
}

function sendReply(id, result) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ id, reply: true, ...result }));
  }
}

async function sendTabList() {
  if (ws && ws.readyState === WebSocket.OPEN) {
    const tabs = await chrome.tabs.query({});
    ws.send(JSON.stringify({
      type: 'TABS_CHANGED',
      tabs: tabs.map(t => ({
        id: t.id,
        windowId: t.windowId,
        title: t.title,
        url: t.url,
        active: t.active,
        favIconUrl: t.favIconUrl
      }))
    }));
  }
}

// Tab event listeners
chrome.tabs.onUpdated.addListener(() => sendTabList());
chrome.tabs.onRemoved.addListener(() => sendTabList());
chrome.tabs.onActivated.addListener(() => sendTabList());

// Download listener
chrome.downloads.onChanged.addListener((delta) => {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({
      type: 'DOWNLOAD_CHANGED',
      delta
    }));
  }
});

// Initial start
connect();

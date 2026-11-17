document.addEventListener('DOMContentLoaded', async () => {
  const urlInput = document.getElementById('url');
  const tokenInput = document.getElementById('token');
  const statusEl = document.getElementById('status');
  const saveBtn = document.getElementById('save');

  const data = await chrome.storage.local.get(['regnant_url', 'regnant_token']);
  if (data.regnant_url) urlInput.value = data.regnant_url;
  if (data.regnant_token) tokenInput.value = data.regnant_token;

  saveBtn.addEventListener('click', async () => {
    await chrome.storage.local.set({
      regnant_url: urlInput.value.trim(),
      regnant_token: tokenInput.value.trim()
    });
    statusEl.textContent = 'Settings saved. Reconnecting...';
    statusEl.style.color = '#898861';
    chrome.runtime.reload();
  });
});

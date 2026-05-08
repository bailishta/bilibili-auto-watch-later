// 内容脚本 — 在 bilibili.com 页面上下文中代理 API 调用
// 页面上下文天然拥有登录 cookie，确保 API 请求带正确的认证信息

console.log('[稍后观看助手] 内容脚本已加载');

// ── API 调用（在页面上下文中执行，cookie 自动携带） ──

const ALLOWED_API_ORIGIN = 'https://api.bilibili.com';

function validateApiUrl(url) {
  try {
    const u = new URL(url);
    return u.origin === ALLOWED_API_ORIGIN;
  } catch {
    return false;
  }
}

async function apiCall({ url, method, body }) {
  if (!validateApiUrl(url)) {
    return { success: false, error: `不允许的请求域名: ${url}` };
  }

  const opts = {
    method: method || 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Referer': 'https://www.bilibili.com/'
    }
  };
  if (body) opts.body = body;

  try {
    const resp = await fetch(url, opts);
    const data = await resp.json();
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

// ── 添加稍后观看（页面上下文 — 可读取 bili_jct） ──

function getBiliJct() {
  const cookies = {};
  document.cookie.split(';').forEach(c => {
    const idx = c.indexOf('=');
    if (idx > 0) cookies[c.substring(0, idx).trim()] = c.substring(idx + 1);
  });
  return cookies.bili_jct || null;
}

async function addToWatchLaterProxy(aid) {
  const biliJct = getBiliJct();
  if (!biliJct) {
    return { success: false, error: '页面cookie中未找到bili_jct' };
  }

  const body = new URLSearchParams({
    aid: String(aid),
    csrf: biliJct
  }).toString();

  try {
    const resp = await fetch('https://api.bilibili.com/x/v2/history/toview/add', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Referer': 'https://www.bilibili.com/',
        'Origin': 'https://www.bilibili.com'
      },
      body
    });
    const data = await resp.json();
    return { success: true, data };
  } catch (e) {
    return { success: false, error: e.message };
  }
}

async function addToWatchLaterBatch(aids) {
  const biliJct = getBiliJct();
  if (!biliJct) {
    return { success: false, error: '页面cookie中未找到bili_jct' };
  }

  const results = [];
  for (const aid of aids) {
    const body = new URLSearchParams({
      aid: String(aid),
      csrf: biliJct
    }).toString();

    try {
      const resp = await fetch('https://api.bilibili.com/x/v2/history/toview/add', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Referer': 'https://www.bilibili.com/',
          'Origin': 'https://www.bilibili.com'
        },
        body
      });
      const data = await resp.json();
      results.push({ aid, success: true, code: data.code });
    } catch (e) {
      results.push({ aid, success: false, error: e.message });
    }
    // 小间隔防止B站限流
    if (aids.length > 1) {
      await new Promise(r => setTimeout(r, 200));
    }
  }
  return { success: true, results };
}

// ── 监听来自 service worker 的消息 ──

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'addToWatchLater') {
    addToWatchLaterProxy(msg.aid).then(sendResponse);
    return true;
  }
  if (msg.type === 'addToWatchLaterBatch') {
    addToWatchLaterBatch(msg.aids).then(sendResponse);
    return true;
  }
  if (msg.type === 'apiCall') {
    apiCall(msg).then(sendResponse);
    return true; // 保持通道开放
  }
  if (msg.type === 'ping') {
    sendResponse({ pong: true });
    return true;
  }
  return false;
});

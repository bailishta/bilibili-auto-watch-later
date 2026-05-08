// ── MD5 纯JS实现 ──
// 来源：https://css-tricks.com/snippets/javascript/javascript-md5/
// 稍作改写，用于WBI签名

function md5(str) {
  function rotateLeft(lValue, iShiftBits) {
    return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits));
  }
  function addUnsigned(lX, lY) {
    let lX4, lY4, lX8, lY8, lResult;
    lX8 = (lX & 0x80000000);
    lY8 = (lY & 0x80000000);
    lX4 = (lX & 0x40000000);
    lY4 = (lY & 0x40000000);
    lResult = (lX & 0x3FFFFFFF) + (lY & 0x3FFFFFFF);
    if (lX4 & lY4) return (lResult ^ 0x80000000 ^ lX8 ^ lY8);
    if (lX4 | lY4) {
      if (lResult & 0x40000000) return (lResult ^ 0xC0000000 ^ lX8 ^ lY8);
      else return (lResult ^ 0x40000000 ^ lX8 ^ lY8);
    } else {
      return (lResult ^ lX8 ^ lY8);
    }
  }
  function F(x, y, z) { return (x & y) | ((~x) & z); }
  function G(x, y, z) { return (x & z) | (y & (~z)); }
  function H(x, y, z) { return (x ^ y ^ z); }
  function I(x, y, z) { return (y ^ (x | (~z))); }
  function FF(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(F(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function GG(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(G(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function HH(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(H(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function II(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(I(b, c, d), x), ac));
    return addUnsigned(rotateLeft(a, s), b);
  }
  function convertToWordArray(str) {
    let lWordCount;
    const lMessageLength = str.length;
    const lNumberOfWords_temp1 = lMessageLength + 8;
    const lNumberOfWords_temp2 = (lNumberOfWords_temp1 - (lNumberOfWords_temp1 % 64)) / 64;
    const lNumberOfWords = (lNumberOfWords_temp2 + 1) * 16;
    const lWordArray = Array(lNumberOfWords - 1);
    let lBytePosition = 0;
    let lByteCount = 0;
    while (lByteCount < lMessageLength) {
      lWordCount = (lByteCount - (lByteCount % 4)) / 4;
      lBytePosition = (lByteCount % 4) * 8;
      lWordArray[lWordCount] = (lWordArray[lWordCount] | (str.charCodeAt(lByteCount) << lBytePosition));
      lByteCount++;
    }
    lWordCount = (lByteCount - (lByteCount % 4)) / 4;
    lBytePosition = (lByteCount % 4) * 8;
    lWordArray[lWordCount] = lWordArray[lWordCount] | (0x80 << lBytePosition);
    lWordArray[lNumberOfWords - 2] = lMessageLength << 3;
    lWordArray[lNumberOfWords - 1] = lMessageLength >>> 29;
    return lWordArray;
  }
  function wordToHex(lValue) {
    let WordToHexValue = "", WordToHexValue_temp = "", lByte, lCount;
    for (lCount = 0; lCount <= 3; lCount++) {
      lByte = (lValue >>> (lCount * 8)) & 255;
      WordToHexValue_temp = "0" + lByte.toString(16);
      WordToHexValue = WordToHexValue + WordToHexValue_temp.substr(WordToHexValue_temp.length - 2, 2);
    }
    return WordToHexValue;
  }

  const x = convertToWordArray(str);
  let a = 0x67452301, b = 0xEFCDAB89, c = 0x98BADCFE, d = 0x10325476;

  for (let k = 0; k < x.length; k += 16) {
    const AA = a, BB = b, CC = c, DD = d;
    a = FF(a, b, c, d, x[k + 0], 7, 0xD76AA478);
    d = FF(d, a, b, c, x[k + 1], 12, 0xE8C7B756);
    c = FF(c, d, a, b, x[k + 2], 17, 0x242070DB);
    b = FF(b, c, d, a, x[k + 3], 22, 0xC1BDCEEE);
    a = FF(a, b, c, d, x[k + 4], 7, 0xF57C0FAF);
    d = FF(d, a, b, c, x[k + 5], 12, 0x4787C62A);
    c = FF(c, d, a, b, x[k + 6], 17, 0xA8304613);
    b = FF(b, c, d, a, x[k + 7], 22, 0xFD469501);
    a = FF(a, b, c, d, x[k + 8], 7, 0x698098D8);
    d = FF(d, a, b, c, x[k + 9], 12, 0x8B44F7AF);
    c = FF(c, d, a, b, x[k + 10], 17, 0xFFFF5BB1);
    b = FF(b, c, d, a, x[k + 11], 22, 0x895CD7BE);
    a = FF(a, b, c, d, x[k + 12], 7, 0x6B901122);
    d = FF(d, a, b, c, x[k + 13], 12, 0xFD987193);
    c = FF(c, d, a, b, x[k + 14], 17, 0xA679438E);
    b = FF(b, c, d, a, x[k + 15], 22, 0x49B40821);
    a = GG(a, b, c, d, x[k + 1], 5, 0xF61E2562);
    d = GG(d, a, b, c, x[k + 6], 9, 0xC040B340);
    c = GG(c, d, a, b, x[k + 11], 14, 0x265E5A51);
    b = GG(b, c, d, a, x[k + 0], 20, 0xE9B6C7AA);
    a = GG(a, b, c, d, x[k + 5], 5, 0xD62F105D);
    d = GG(d, a, b, c, x[k + 10], 9, 0x2441453);
    c = GG(c, d, a, b, x[k + 15], 14, 0xD8A1E681);
    b = GG(b, c, d, a, x[k + 4], 20, 0xE7D3FBC8);
    a = GG(a, b, c, d, x[k + 9], 5, 0x21E1CDE6);
    d = GG(d, a, b, c, x[k + 14], 9, 0xC33707D6);
    c = GG(c, d, a, b, x[k + 3], 14, 0xF4D50D87);
    b = GG(b, c, d, a, x[k + 8], 20, 0x455A14ED);
    a = GG(a, b, c, d, x[k + 13], 5, 0xA9E3E905);
    d = GG(d, a, b, c, x[k + 2], 9, 0xFCEFA3F8);
    c = GG(c, d, a, b, x[k + 7], 14, 0x676F02D9);
    b = GG(b, c, d, a, x[k + 12], 20, 0x8D2A4C8A);
    a = HH(a, b, c, d, x[k + 5], 4, 0xFFFA3942);
    d = HH(d, a, b, c, x[k + 8], 11, 0x8771F681);
    c = HH(c, d, a, b, x[k + 11], 16, 0x6D9D6122);
    b = HH(b, c, d, a, x[k + 14], 23, 0xFDE5380C);
    a = HH(a, b, c, d, x[k + 1], 4, 0xA4BEEA44);
    d = HH(d, a, b, c, x[k + 4], 11, 0x4BDECFA9);
    c = HH(c, d, a, b, x[k + 7], 16, 0xF6BB4B60);
    b = HH(b, c, d, a, x[k + 10], 23, 0xBEBFBC70);
    a = HH(a, b, c, d, x[k + 13], 4, 0x289B7EC6);
    d = HH(d, a, b, c, x[k + 0], 11, 0xEAA127FA);
    c = HH(c, d, a, b, x[k + 3], 16, 0xD4EF3085);
    b = HH(b, c, d, a, x[k + 6], 23, 0x4881D05);
    a = HH(a, b, c, d, x[k + 9], 4, 0xD9D4D039);
    d = HH(d, a, b, c, x[k + 12], 11, 0xE6DB99E5);
    c = HH(c, d, a, b, x[k + 15], 16, 0x1FA27CF8);
    b = HH(b, c, d, a, x[k + 2], 23, 0xC4AC5665);
    a = II(a, b, c, d, x[k + 0], 6, 0xF4292244);
    d = II(d, a, b, c, x[k + 7], 10, 0x432AFF97);
    c = II(c, d, a, b, x[k + 14], 15, 0xAB9423A7);
    b = II(b, c, d, a, x[k + 5], 21, 0xFC93A039);
    a = II(a, b, c, d, x[k + 12], 6, 0x655B59C3);
    d = II(d, a, b, c, x[k + 3], 10, 0x8F0CCC92);
    c = II(c, d, a, b, x[k + 10], 15, 0xFFEFF47D);
    b = II(b, c, d, a, x[k + 1], 21, 0x85845DD1);
    a = II(a, b, c, d, x[k + 8], 6, 0x6FA87E4F);
    d = II(d, a, b, c, x[k + 15], 10, 0xFE2CE6E0);
    c = II(c, d, a, b, x[k + 6], 15, 0xA3014314);
    b = II(b, c, d, a, x[k + 13], 21, 0x4E0811A1);
    a = II(a, b, c, d, x[k + 4], 6, 0xF7537E82);
    d = II(d, a, b, c, x[k + 11], 10, 0xBD3AF235);
    c = II(c, d, a, b, x[k + 2], 15, 0x2AD7D2BB);
    b = II(b, c, d, a, x[k + 9], 21, 0xEB86D391);
    a = addUnsigned(a, AA);
    b = addUnsigned(b, BB);
    c = addUnsigned(c, CC);
    d = addUnsigned(d, DD);
  }

  return (wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d)).toLowerCase();
}

// ── Cookie 读取 ──

export async function getCookies() {
  try {
    const [sessdata, biliJct, dedeUserId] = await Promise.all([
      chrome.cookies.get({ url: 'https://www.bilibili.com', name: 'SESSDATA' }),
      chrome.cookies.get({ url: 'https://www.bilibili.com', name: 'bili_jct' }),
      chrome.cookies.get({ url: 'https://www.bilibili.com', name: 'DedeUserID' })
    ]);
    return {
      SESSDATA: sessdata?.value || null,
      bili_jct: biliJct?.value || null,
      DedeUserID: dedeUserId?.value || null
    };
  } catch {
    return { SESSDATA: null, bili_jct: null, DedeUserID: null };
  }
}

async function getCookieString() {
  const cookies = await getCookies();
  if (!cookies.SESSDATA) return null;
  return `SESSDATA=${cookies.SESSDATA}; bili_jct=${cookies.bili_jct}; DedeUserID=${cookies.DedeUserID}`;
}

// ── WBI签名（基于 SocialSisterYi/bilibili-API-collect 官方实现） ──

const MIXIN_KEY_ENC_TAB = [
  46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
  27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
  37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
  22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
];

let wbiKeys = null;

async function getWbiKeys() {
  if (wbiKeys) return wbiKeys;

  try {
    // 使用内容脚本代理获取 WBI keys（确保携带正确的登录cookie）
    const data = await biliFetch('https://api.bilibili.com/x/web-interface/nav');
    if (data.code !== 0 || !data.data?.wbi_img) {
      console.warn('[api] getWbiKeys失败: nav API code=', data.code);
      return null;
    }

    // B站nav API返回字段是 img_url / sub_url
    const img_key = data.data.wbi_img.img_url || data.data.wbi_img.img_key;
    const sub_key = data.data.wbi_img.sub_url || data.data.wbi_img.sub_key;
    if (!img_key || !sub_key) {
      console.warn('[api] getWbiKeys失败: 无法从wbi_img提取密钥');
      return null;
    }
    const imgKey = img_key.split('/').pop().split('.')[0];
    const subKey = sub_key.split('/').pop().split('.')[0];
    wbiKeys = { imgKey, subKey };
    return wbiKeys;
  } catch (e) {
    console.error('[api] getWbiKeys异常:', e.message);
    return null;
  }
}

function getMixinKey(orig) {
  let temp = '';
  MIXIN_KEY_ENC_TAB.forEach(n => { temp += orig[n]; });
  return temp.slice(0, 32);
}

// B站专用URL编码：过滤 !'()* → encodeURIComponent → 大写十六进制
function encWbi(s) {
  const filtered = String(s).replace(/[!'()*]/g, '');
  return encodeURIComponent(filtered).replace(/%[0-9a-f]{2}/g, m => m.toUpperCase());
}

// 使用encoder计算hash（官方标准实现）
function encWbiSign(params, mixinKey) {
  // 过滤并编码所有参数
  const encoded = {};
  for (const [k, v] of Object.entries(params)) {
    encoded[k] = encWbi(String(v).replace(/[!'()*]/g, ''));
  }
  // 按key排序后拼接
  const query = Object.keys(encoded).sort().map(k => `${k}=${encoded[k]}`).join('&');
  // MD5(querystring + mixin_key)
  return md5(query + mixinKey);
}

async function signParams(params) {
  const keys = await getWbiKeys();
  if (!keys) return null;

  const mixinKey = getMixinKey(keys.imgKey + keys.subKey);
  const wts = Math.floor(Date.now() / 1000);
  const withWts = { ...params, wts: String(wts) };
  const w_rid = encWbiSign(withWts, mixinKey);

  return { ...params, wts, w_rid };
}

// ── 通用请求封装 ──
// 优先直接fetch（已证实在service worker中credentials:'include'有效）
// 失败时回退到内容脚本代理

async function biliFetch(url, options = {}) {
  // 先尝试直接 fetch
  try {
    const cookies = await getCookies();
    if (!cookies.SESSDATA) {
      console.warn('[api] 未登录，无SESSDATA cookie');
      throw new Error('NOT_LOGGED_IN');
    }

    const resp = await fetch(url, {
      ...options,
      credentials: 'include',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Referer': 'https://www.bilibili.com/',
        ...(options.headers || {})
      }
    });
    return resp.json();
  } catch (e) {
    // 直接fetch失败，尝试内容脚本代理
    console.warn('[api] 直接fetch失败:', e.message, '尝试代理');
    return proxyFetch(url, options);
  }
}

async function proxyFetch(url, options = {}) {
  const tabs = await chrome.tabs.query({ url: 'https://*.bilibili.com/*' });
  if (tabs.length === 0) throw new Error('无bilibili标签页，无法代理请求');

  const result = await chrome.tabs.sendMessage(tabs[0].id, {
    type: 'apiCall',
    url,
    method: options.method || 'GET',
    body: options.body || null
  });
  if (result?.success) return result.data;
  throw new Error(result?.error || '代理请求失败');
}

// 通过内容脚本代理添加稍后观看（内容脚本可从页面cookie读取bili_jct）
async function proxyAddToWatchLater(aid) {
  try {
    const tabs = await chrome.tabs.query({ url: 'https://*.bilibili.com/*' });
    if (tabs.length === 0) {
      console.error('[api] proxyAddToWatchLater失败: 无bilibili标签页，请先打开一个B站页面');
      return false;
    }

    const result = await chrome.tabs.sendMessage(tabs[0].id, {
      type: 'addToWatchLater',
      aid: String(aid)
    });

    if (result?.success) {
      return result.data?.code === 0;
    }
    console.error('[api] proxyAddToWatchLater 失败:', result?.error);
    return false;
  } catch (e) {
    console.error('[api] proxyAddToWatchLater 异常:', e.message, '(内容脚本可能未加载，请刷新B站页面后重试)');
    return false;
  }
}

// 批量代理添加稍后观看，一次消息往返处理多个视频
async function proxyAddToWatchLaterBatch(aids) {
  try {
    const tabs = await chrome.tabs.query({ url: 'https://*.bilibili.com/*' });
    if (tabs.length === 0) {
      console.error('[api] proxyAddToWatchLaterBatch失败: 无bilibili标签页');
      return [];
    }

    const result = await chrome.tabs.sendMessage(tabs[0].id, {
      type: 'addToWatchLaterBatch',
      aids: aids.map(String)
    });

    if (result?.success && result.results) {
      return result.results;
    }
    console.error('[api] proxyAddToWatchLaterBatch 失败:', result?.error);
    return [];
  } catch (e) {
    console.error('[api] proxyAddToWatchLaterBatch 异常:', e.message);
    return [];
  }
}

export async function addToWatchLaterBatch(aids) {
  if (aids.length === 0) return [];
  // 已知 service worker 取不到 bili_jct，直接走批量代理
  if (_biliJctAvailable === false) {
    return await proxyAddToWatchLaterBatch(aids);
  }
  // 单个的情况走原有逻辑
  if (aids.length === 1) {
    const ok = await addToWatchLater(aids[0]);
    return [{ aid: aids[0], success: ok, code: ok ? 0 : -1 }];
  }
  return await proxyAddToWatchLaterBatch(aids);
}

// ── 业务API ──

export async function getCurrentUser() {
  const data = await biliFetch('https://api.bilibili.com/x/web-interface/nav');
  if (data.code !== 0 || !data.data?.mid) return null;
  return {
    uid: data.data.mid,
    name: data.data.uname,
    face: data.data.face
  };
}

export async function getFollowList(uid, page = 1, ps = 50) {
  const data = await biliFetch(
    `https://api.bilibili.com/x/relation/followings?vmid=${uid}&pn=${page}&ps=${ps}&order=desc&order_type=attention`
  );
  if (data.code !== 0) return { list: [], total: 0, hasMore: false };
  return {
    list: (data.data.list || []).map(item => ({
      mid: item.mid,
      name: item.uname,
      face: item.face,
      sign: item.sign || ''
    })),
    total: data.data.total || 0,
    hasMore: data.data.list?.length === ps && data.data.list?.length + (page - 1) * ps < (data.data.total || 0)
  };
}

export async function getUserVideos(mid, page = 1, ps = 10) {
  // 参数完全对齐 bilibili-api-python 官方库 (Nemo2011/bilibili-api)
  const params = await signParams({
    mid: String(mid),
    ps: String(ps),
    pn: String(page),
    tid: '0',
    order: 'pubdate',
    platform: 'web',
    order_avoided: 'true'
  });
  if (!params) return { error: 'WBI签名失败，请确认已登录B站', videos: [] };

  const query = new URLSearchParams(params).toString();
  let data;
  try {
    data = await biliFetch(
      `https://api.bilibili.com/x/space/wbi/arc/search?${query}`
    );
  } catch (e) {
    return { error: `网络请求失败: ${e.message}`, videos: [] };
  }

  if (data.code !== 0) {
    console.warn(`[api] getUserVideos mid=${mid} API错误 code=${data.code}`);
    return { error: `API返回错误 code=${data.code}`, videos: [] };
  }

  // B站API返回结构: data.list.vlist
  const vlist = data.data?.list?.vlist || data.data?.list?.videos || data.data?.archives || [];
  console.log(`[api] getUserVideos mid=${mid} 获取到${vlist.length}个视频`);
  const videos = (vlist || []).map(v => ({
    aid: v.aid,
    bvid: v.bvid,
    title: v.title,
    pubdate: v.pubdate || v.created || 0,
    author: v.author || '',
    mid: v.mid || mid,
    description: v.description || '',
    pic: v.pic || ''
  }));

  return { error: null, videos, upName: videos[0]?.author || '' };
}

// 获取UP主基本信息（用card API，无需WBI签名，更可靠）
export async function getUpInfo(mid) {
  try {
    const data = await biliFetch(
      `https://api.bilibili.com/x/web-interface/card?mid=${mid}`
    );
    if (data.code === 0 && data.data?.card) {
      const card = data.data.card;
      return { mid: card.mid, name: card.name, face: card.face, exists: true };
    }
    // card API失败，尝试 WBI 签名的 wbi/acc/info
    const signed = await signParams({ mid: String(mid) });
    if (signed) {
      const query2 = new URLSearchParams(signed).toString();
      const data2 = await biliFetch(
        `https://api.bilibili.com/x/space/wbi/acc/info?${query2}`
      );
      if (data2.code === 0 && data2.data) {
        return {
          mid: data2.data.mid || mid,
          name: data2.data.name || '未知UP主',
          face: data2.data.face || '',
          exists: true
        };
      }
    }
    console.warn('[api] getUpInfo 两个API都失败，使用占位名');
    return { mid, name: `UP主_${mid}`, face: '', exists: false };
  } catch (e) {
    console.error('[api] getUpInfo 异常:', e.message);
    return { mid, name: `UP主_${mid}`, face: '', exists: false };
  }
}

// 缓存 bili_jct 在 service worker 中的可用性，避免每次调用都重复查询
let _biliJctAvailable = null;

export async function addToWatchLater(aid) {
  // 已知 service worker 取不到 bili_jct，直接走内容脚本代理
  if (_biliJctAvailable === false) {
    return await proxyAddToWatchLater(aid);
  }

  const allCookies = await chrome.cookies.getAll({ domain: '.bilibili.com' });
  const biliJct = allCookies.find(c => c.name === 'bili_jct');

  if (!biliJct) {
    _biliJctAvailable = false;
    return await proxyAddToWatchLater(aid);
  }

  _biliJctAvailable = true;
  const cookieStr = allCookies.map(c => `${c.name}=${c.value}`).join('; ');
  const bodyStr = new URLSearchParams({
    aid: String(aid),
    csrf: biliJct.value
  }).toString();

  try {
    const resp = await fetch('https://api.bilibili.com/x/v2/history/toview/add', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
        'Referer': 'https://www.bilibili.com/',
        'Origin': 'https://www.bilibili.com',
        'Cookie': cookieStr
      },
      body: bodyStr
    });

    const text = await resp.text();

    try {
      const data = JSON.parse(text);
      return data.code === 0;
    } catch {
      // 响应非JSON，回退代理
    }
  } catch (e) {
    console.log('[api] addToWatchLater 直接请求异常:', e.message, '回退代理');
  }

  return await proxyAddToWatchLater(aid);
}

export async function getWatchLaterList() {
  const data = await biliFetch('https://api.bilibili.com/x/v2/history/toview');
  if (data.code !== 0) return [];
  return data.data?.list || [];
}

// 获取观看历史（用于过滤已看过的视频）
export async function getWatchHistory(ps = 30) {
  const data = await biliFetch(
    `https://api.bilibili.com/x/v2/history?ps=${ps}&pn=1`
  );
  if (data.code !== 0) {
    console.warn('[api] getWatchHistory API失败 code=', data.code);
    return [];
  }
  // B站API返回 data.list（对象内含list数组），部分旧版直接返回data数组
  const list = Array.isArray(data.data) ? data.data : (data.data?.list || []);
  return list.map(item => item.bvid || item.history?.bvid).filter(Boolean);
}

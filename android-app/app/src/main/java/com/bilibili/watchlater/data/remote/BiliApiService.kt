package com.bilibili.watchlater.data.remote

import com.bilibili.watchlater.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BiliApiService(private val cookieProvider: CookieProvider) {

    private val logInterceptor = Interceptor { chain ->
        val req = chain.request()
        val hasSessdata = req.header("Cookie")?.contains("SESSDATA=") == true
        AppLogger.i(TAG, ">>> ${req.method} ${req.url}")
        AppLogger.i(TAG, ">>> Cookie: SESSDATA=$hasSessdata")
        val resp: Response = chain.proceed(req)
        AppLogger.i(TAG, "<<< ${resp.code}")
        resp
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(logInterceptor)
        .build()

    private val wbiMutex = Mutex()
    @Volatile
    private var wbiKeysReady = false
    private var debugCount = 0

    private suspend fun ensureWbiKeys() {
        if (!wbiKeysReady && !WbiSigner.hasKeys()) {
            wbiMutex.withLock {
                if (!wbiKeysReady && !WbiSigner.hasKeys()) {
                    wbiKeysReady = fetchWbiKeys()
                }
            }
        }
    }

    private val baseHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Referer" to "https://www.bilibili.com/",
            "Accept" to "application/json, text/plain, */*"
        )

    // ── 通用请求 ──

    private fun requestHeaders(): Map<String, String> {
        val headers = baseHeaders.toMutableMap()
        val cookieStr = cookieProvider.getCookieString()
        if (!cookieStr.isNullOrEmpty()) {
            headers["Cookie"] = cookieStr
        }
        return headers
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).apply {
                requestHeaders().forEach { (k, v) -> addHeader(k, v) }
            }.build()
            client.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string() ?: "{}")
            }
        } catch (e: java.io.IOException) {
            AppLogger.w(TAG, "GET请求失败: ${e.message}")
            JSONObject("""{"code":-1,"message":"网络错误"}""")
        }
    }

    private suspend fun post(url: String, body: FormBody): JSONObject = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).apply {
                requestHeaders().forEach { (k, v) -> addHeader(k, v) }
                header("Origin", "https://www.bilibili.com")
            }.post(body).build()
            client.newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string() ?: "{}")
            }
        } catch (e: java.io.IOException) {
            AppLogger.w(TAG, "POST请求失败: ${e.message}")
            JSONObject("""{"code":-1,"message":"网络错误"}""")
        }
    }

    // ── 用户信息 ──

    suspend fun getCurrentUser(): JSONObject {
        return get("https://api.bilibili.com/x/web-interface/nav")
    }

    // ── 获取 WBI keys ──

    suspend fun fetchWbiKeys(): Boolean {
        val data = getCurrentUser()
        if (data.optInt("code") != 0) {
            AppLogger.w(TAG, "获取WBI密钥失败，nav接口code=${data.optInt("code")}")
            return false
        }
        val wbiImg = data.optJSONObject("data")?.optJSONObject("wbi_img")
        if (wbiImg == null) {
            AppLogger.w(TAG, "获取WBI密钥失败，wbi_img字段缺失")
            return false
        }
        val imgUrl = wbiImg.optString("img_url")
        val subUrl = wbiImg.optString("sub_url")
        if (imgUrl.isEmpty() || subUrl.isEmpty()) return false

        val imgKey = imgUrl.split("/").last().split(".").first()
        val subKey = subUrl.split("/").last().split(".").first()
        WbiSigner.setKeys(imgKey, subKey)
        AppLogger.i(TAG, "WBI密钥已初始化")
        return true
    }

    // ── UP主信息 ──

    suspend fun getUpInfo(mid: Long): UpInfo {
        // 先尝试 card API（无需WBI签名）
        val cardData = get("https://api.bilibili.com/x/web-interface/card?mid=$mid")
        if (cardData.optInt("code") == 0) {
            val card = cardData.optJSONObject("data")?.optJSONObject("card")
            if (card != null) {
                return UpInfo(
                    mid = card.optLong("mid", mid),
                    name = card.optString("name", "UP主_$mid"),
                    face = card.optString("face", "")
                )
            }
        }
        // 回退 WBI 签名 API
        ensureWbiKeys()
        val signed = WbiSigner.signParams(mapOf("mid" to mid.toString()))
        if (signed != null) {
            val query = signed.entries.joinToString("&") { "${it.key}=${WbiSigner.encWbi(it.value)}" }
            val data2 = get("https://api.bilibili.com/x/space/wbi/acc/info?$query")
            if (data2.optInt("code") == 0) {
                val d = data2.optJSONObject("data")
                if (d != null) {
                    return UpInfo(
                        mid = d.optLong("mid", mid),
                        name = d.optString("name", "UP主_$mid"),
                        face = d.optString("face", "")
                    )
                }
            }
        }
        return UpInfo(mid = mid, name = "UP主_$mid", face = "")
    }

    // ── UP主视频列表 ──

    suspend fun getUserVideos(mid: Long, page: Int = 1, ps: Int = 10): UserVideosResult {
        ensureWbiKeys()
        val params = WbiSigner.signParams(
            mapOf(
                "mid" to mid.toString(),
                "ps" to ps.toString(),
                "pn" to page.toString(),
                "tid" to "0",
                "order" to "pubdate",
                "platform" to "web",
                "order_avoided" to "true"
            )
        ) ?: return UserVideosResult(error = "WBI签名失败，请确认已登录B站", videos = emptyList())

        val query = params.entries
            .joinToString("&") { "${it.key}=${WbiSigner.encWbi(it.value)}" }
        val url = "https://api.bilibili.com/x/space/wbi/arc/search?$query"
        val data = get(url)

        if (data.optInt("code") != 0) {
            AppLogger.w(TAG, "获取UP主${mid}视频列表失败 code=${data.optInt("code")}")
            return UserVideosResult(error = "API返回错误 code=${data.optInt("code")}", videos = emptyList())
        }

        val vlist = data.optJSONObject("data")
            ?.optJSONObject("list")
            ?.optJSONArray("vlist") ?: org.json.JSONArray()

        if (vlist.length() > 0 && debugCount < 3) {
            debugCount++
            val firstV = vlist.getJSONObject(0)
            AppLogger.i(TAG, ">>> 调试#$debugCount: mid=$mid 返回${vlist.length()}个视频")
            AppLogger.i(TAG, ">>> 调试#$debugCount: 首视频JSON=${firstV.toString().take(300)}")
            AppLogger.i(TAG, ">>> 调试#$debugCount: pubdate字段=${firstV.optLong("pubdate", -1)} created字段=${firstV.optLong("created", -1)}")
        }

        val videos = mutableListOf<BiliVideo>()
        for (i in 0 until vlist.length()) {
            val v = vlist.getJSONObject(i)
            videos.add(
                BiliVideo(
                    aid = v.optLong("aid", 0),
                    bvid = v.optString("bvid", ""),
                    title = v.optString("title", ""),
                    pubdate = v.optLong("pubdate", 0).let { if (it == 0L) v.optLong("created", 0) else it },
                    author = v.optString("author", ""),
                    mid = v.optLong("mid", mid),
                    pic = v.optString("pic", "")
                )
            )
        }

        val upName = videos.firstOrNull()?.author ?: ""
        return UserVideosResult(error = null, videos = videos, upName = upName)
    }

    // ── 添加稍后观看 ──

    suspend fun addToWatchLater(aid: Long): Boolean {
        val jct = cookieProvider.biliJct ?: return false
        val body = FormBody.Builder()
            .add("aid", aid.toString())
            .add("csrf", jct)
            .build()
        val data = post("https://api.bilibili.com/x/v2/history/toview/add", body)
        return data.optInt("code") == 0
    }

    suspend fun addToWatchLaterBatch(aids: List<Long>): List<BatchResult> {
        val jct = cookieProvider.biliJct
        if (jct == null) {
            AppLogger.e(TAG, "addToWatchLaterBatch失败: bili_jct为空，无法获取CSRF token")
            return aids.map { BatchResult(it, false, -1) }
        }
        val results = mutableListOf<BatchResult>()
        for ((i, aid) in aids.withIndex()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (i > 0) delay(200)
            val body = FormBody.Builder()
                .add("aid", aid.toString())
                .add("csrf", jct)
                .build()
            val data = post("https://api.bilibili.com/x/v2/history/toview/add", body)
            val code = data.optInt("code")
            results.add(BatchResult(aid, code == 0, code))
        }
        return results
    }

    // ── 观看历史 ──

    suspend fun getWatchHistory(targetCount: Int = 500): Set<String> {
        val bvids = mutableSetOf<String>()
        var cursor = 0L
        val ps = 30

        while (bvids.size < targetCount) {
            val data = get("https://api.bilibili.com/x/v2/history?ps=$ps&type=archive&max=$cursor")
            if (data.optInt("code") != 0) break

            val list = data.optJSONArray("data")
                ?: data.optJSONObject("data")?.optJSONArray("list")
                ?: break
            if (list.length() == 0) break

            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val bvid = item.optString("bvid", "")
                    .ifEmpty { item.optJSONObject("history")?.optString("bvid", "").orEmpty() }
                if (bvid.isNotEmpty()) bvids.add(bvid)
            }

            val last = list.getJSONObject(list.length() - 1)
            val nextCursor = last.optLong("view_at", 0)
                .let { if (it == 0L) last.optJSONObject("history")?.optLong("view_at", 0) ?: 0 else it }
            if (nextCursor == 0L) break
            cursor = nextCursor
        }

        return bvids
    }

    // ── 关注列表 ──

    suspend fun getFollowList(uid: Long, page: Int = 1, ps: Int = 50): FollowListResult {
        val data = get("https://api.bilibili.com/x/relation/followings?vmid=$uid&pn=$page&ps=$ps&order=desc&order_type=attention")
        if (data.optInt("code") != 0) return FollowListResult(list = emptyList(), total = 0, hasMore = false)

        val d = data.optJSONObject("data") ?: return FollowListResult(emptyList(), 0, false)
        val list = d.optJSONArray("list") ?: return FollowListResult(emptyList(), 0, false)
        val total = d.optInt("total", 0)

        val users = mutableListOf<FollowUser>()
        for (i in 0 until list.length()) {
            val u = list.getJSONObject(i)
            users.add(
                FollowUser(
                    mid = u.optLong("mid", 0),
                    name = u.optString("uname", ""),
                    face = u.optString("face", ""),
                    sign = u.optString("sign", "")
                )
            )
        }
        val hasMore = list.length() == ps && (users.size + (page - 1) * ps) < total
        return FollowListResult(users, total, hasMore)
    }
}

// ── 数据类 ──

data class UpInfo(
    val mid: Long,
    val name: String,
    val face: String
)

data class BiliVideo(
    val aid: Long,
    val bvid: String,
    val title: String,
    val pubdate: Long,
    val author: String,
    val mid: Long,
    val pic: String
)

data class UserVideosResult(
    val error: String?,
    val videos: List<BiliVideo>,
    val upName: String = ""
)

data class BatchResult(
    val aid: Long,
    val success: Boolean,
    val code: Int
)

data class FollowUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String
)

data class FollowListResult(
    val list: List<FollowUser>,
    val total: Int,
    val hasMore: Boolean
)

private const val TAG = "BiliAPI"

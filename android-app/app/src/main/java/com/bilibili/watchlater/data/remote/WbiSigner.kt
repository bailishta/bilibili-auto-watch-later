package com.bilibili.watchlater.data.remote

import com.bilibili.watchlater.util.md5

object WbiSigner {

    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    @Volatile
    private var wbiKeys: Pair<String, String>? = null

    fun setKeys(imgKey: String, subKey: String) {
        wbiKeys = Pair(imgKey, subKey)
    }

    fun hasKeys(): Boolean = wbiKeys != null

    private fun getMixinKey(orig: String): String {
        if (orig.length < 64) {
            return orig.substring(0, minOf(32, orig.length))
        }
        val sb = StringBuilder()
        for (n in MIXIN_KEY_ENC_TAB) {
            sb.append(orig[n])
        }
        return sb.substring(0, 32)
    }

    fun encWbi(s: String): String {
        // 与 JS encodeURIComponent 行为完全一致，除过滤 !'()* 外不依赖 URLEncoder
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '!', '\'', '(', ')', '*' -> { /* 过滤掉 */ }
                in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> sb.append(c)
                ' ' -> sb.append("%20")
                else -> {
                    for (byte in c.toString().toByteArray(Charsets.UTF_8)) {
                        sb.append(String.format("%%%02X", byte))
                    }
                }
            }
        }
        return sb.toString()
    }

    fun signParams(params: Map<String, String>): Map<String, String>? {
        val keys = wbiKeys ?: return null
        val mixinKey = getMixinKey(keys.first + keys.second)
        val wts = (System.currentTimeMillis() / 1000).toString()

        // wts must participate in signing (same as JS: { ...params, wts })
        val withWts = LinkedHashMap(params)
        withWts["wts"] = wts

        // Build filtered+encoded params sorted by key
        val encoded = LinkedHashMap<String, String>()
        for ((k, v) in withWts) {
            encoded[k] = encWbi(v)
        }

        val query = encoded.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }

        val wRid = md5(query + mixinKey)

        return params + mapOf("wts" to wts, "w_rid" to wRid)
    }
}

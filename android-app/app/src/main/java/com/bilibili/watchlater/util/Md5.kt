package com.bilibili.watchlater.util

import java.security.MessageDigest

fun md5(str: String): String {
    val digest = MessageDigest.getInstance("MD5")
    val bytes = digest.digest(str.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

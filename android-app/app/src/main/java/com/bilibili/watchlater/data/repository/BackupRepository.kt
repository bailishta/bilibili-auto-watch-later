package com.bilibili.watchlater.data.repository

import androidx.room.withTransaction
import com.bilibili.watchlater.data.local.AppDatabase
import com.bilibili.watchlater.data.local.entity.CreatorEntity
import com.bilibili.watchlater.data.local.entity.PublicationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class ConfigurationBackup(
    val configuration: AppConfiguration,
    val creators: List<CreatorEntity>,
    val publications: List<PublicationEntity>,
    val browserCheckerCount: Int = 0
)

class BackupRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    suspend fun exportTrackingList(): String = db.withTransaction {
        val creators = db.creatorDao().getAllList()
        JSONObject().put("version", 1).put("exportedAt", Instant.now().toString())
            .put("count", creators.size).put("creators", JSONArray().apply {
                creators.forEach { put(JSONObject().put("mid", it.mid.toString()).put("name", it.name).put("face", it.face)) }
            }).toString(2)
    }

    suspend fun exportConfiguration(): String = db.withTransaction {
        val config = settings.configuration()
        val cutoff = publicationCutoff(config.historyWeeks)
        val records = db.publicationDao().getAll().filter { it.pubdate in cutoff..Instant.now().epochSecond }.groupBy { it.mid }
        JSONObject().put("format", FORMAT).put("version", 1).put("platform", "android")
            .put("exportedAt", Instant.now().toString())
            .put("settings", JSONObject().put("checkers", JSONArray())
                .put("newVideoWindowHours", config.newVideoWindowHours).put("historyWeeks", config.historyWeeks)
                .put("sortMode", config.sortMode).put("importDone", config.importDone).put("themeMode", config.themeMode))
            .put("creators", JSONArray().apply {
                db.creatorDao().getAllList().forEach { creator ->
                    put(JSONObject().put("mid", creator.mid.toString()).put("name", creator.name).put("face", creator.face)
                        .put("addedAt", creator.addedAt).put("fans", creator.fans ?: JSONObject.NULL)
                        .put("lastPubdate", creator.lastPubdate ?: JSONObject.NULL)
                        .put("metaUpdatedAt", creator.metaUpdatedAt ?: JSONObject.NULL)
                        .put("publications", JSONArray().apply {
                            records[creator.mid].orEmpty().take(500).forEach { put(JSONObject().put("bvid", it.bvid).put("pubdate", it.pubdate)) }
                        }))
                }
            }).toString(2)
    }

    fun parseTrackingList(text: String): List<CreatorEntity> {
        val json = JSONObject(text)
        require(!json.has("format")) { "这是配置备份，请使用「导入备份」" }
        val array = json.getJSONArray("creators")
        require(array.length() > 0) { "文件中的追踪名单为空" }
        return (0 until array.length()).map { parseCreator(array.getJSONObject(it), System.currentTimeMillis()) }.distinctBy { it.mid }
    }

    fun parseConfiguration(text: String): ConfigurationBackup {
        val json = JSONObject(text)
        require(json.optString("format") == FORMAT && json.get("version") == 1) { "不是支持的配置备份文件（需要格式版本 1）" }
        val raw = json.getJSONObject("settings")
        val rawHours = integer(raw, "newVideoWindowHours", 24)
        val rawWeeks = integer(raw, "historyWeeks", 3)
        require(rawHours in SettingsRepository.WINDOW_HOURS.map { it.toLong() }) { "备份的检查时间范围无效" }
        require(rawWeeks in 1L..52L) { "备份的记录保留周数无效" }
        val hours = rawHours.toInt()
        val weeks = rawWeeks.toInt()
        val mode = raw.optString("sortMode", "default")
        val theme = raw.optString("themeMode", "system")
        require(hours in SettingsRepository.WINDOW_HOURS) { "备份的检查时间范围无效" }
        require(weeks in 1..52) { "备份的记录保留周数无效" }
        require(mode in SettingsRepository.SORT_MODES) { "备份的排序方式无效" }
        require(theme in SettingsRepository.THEME_MODES) { "备份的主题设置无效" }
        if (raw.has("importDone")) require(raw.get("importDone") is Boolean) { "备份的导入标记无效" }
        val config = AppConfiguration(hours, weeks, mode, raw.optBoolean("importDone", false), theme)
        val array = json.getJSONArray("creators")
        val now = System.currentTimeMillis()
        val cutoff = publicationCutoff(weeks, now)
        val creators = ArrayList<CreatorEntity>()
        val publications = ArrayList<PublicationEntity>()
        val mids = HashSet<Long>()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            val creator = parseCreator(obj, now)
            require(mids.add(creator.mid)) { "备份包含重复的 UID：${creator.mid}" }
            creators.add(creator)
            val records = if (!obj.has("publications")) JSONArray() else obj.getJSONArray("publications")
            val unique = LinkedHashMap<String, PublicationEntity>()
            for (i in 0 until records.length()) {
                val record = records.getJSONObject(i)
                val date = integer(record, "pubdate")
                require(date > 0) { "备份中的发布时间无效" }
                val bvid = string(record, "bvid")
                val key = bvid.ifBlank { "time:$date" }
                if (date in cutoff..(now / 1000)) unique[key] = PublicationEntity(creator.mid, key, bvid, date)
            }
            publications.addAll(unique.values.sortedByDescending { it.pubdate }.take(500))
        }
        val checkers = if (raw.has("checkers")) raw.getJSONArray("checkers").length() else 0
        return ConfigurationBackup(config, creators, publications, checkers)
    }

    suspend fun restore(backup: ConfigurationBackup) = withContext(Dispatchers.IO) {
        val previous = settings.configuration()
        var settingsChanged = false
        try {
            db.withTransaction {
                db.creatorDao().deleteAll() // 外键级联只清理更新记录，保留已处理视频。
                db.creatorDao().insertAll(backup.creators)
                db.publicationDao().insertAll(backup.publications)
                settingsChanged = true
                settings.restoreConfiguration(backup.configuration)
            }
        } catch (error: Exception) {
            if (settingsChanged) withContext(NonCancellable) { settings.restoreConfiguration(previous) }
            throw error
        }
    }

    private fun parseCreator(obj: JSONObject, now: Long): CreatorEntity {
        val id = obj.get("mid").toString()
        require(id.matches(Regex("[0-9]+"))) { "文件中包含无效 UP 主 UID" }
        val mid = id.toLongOrNull()?.takeIf { it > 0 } ?: error("文件中包含无效 UP 主 UID")
        return CreatorEntity(mid, string(obj, "name").ifBlank { "UP主_$mid" }, string(obj, "face"),
            integer(obj, "addedAt", now), fans = nullableInteger(obj, "fans"),
            lastPubdate = nullableInteger(obj, "lastPubdate"), metaUpdatedAt = nullableInteger(obj, "metaUpdatedAt"))
    }

    private fun string(obj: JSONObject, key: String): String {
        if (!obj.has(key) || obj.isNull(key)) return ""
        return (obj.get(key) as? String) ?: error("文件中的 $key 字段无效")
    }

    private fun integer(obj: JSONObject, key: String, fallback: Long? = null): Long {
        if (!obj.has(key)) return fallback ?: error("文件缺少 $key 字段")
        val value = obj.get(key)
        require(value is Int || value is Long) { "文件中的 $key 字段需要整数" }
        val number = (value as Number).toLong()
        require(number >= 0) { "文件中的 $key 字段无效" }
        return number
    }

    private fun nullableInteger(obj: JSONObject, key: String): Long? =
        if (!obj.has(key) || obj.isNull(key)) null else integer(obj, key)

    companion object {
        const val FORMAT = "bilibili-watch-later-configuration"
        const val MAX_BYTES = 25 * 1024 * 1024
    }
}

package com.v2ray.ang.nox

import android.content.Context
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.handler.V2rayConfigManager
import com.v2ray.ang.dto.SubscriptionItem
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.*
import libv2ray.Libv2ray

/**
 * ВСЕ обращения к внутренностям v2rayNG — только здесь.
 * Если после обновления v2rayNG что-то не компилируется, правится только этот файл.
 */
object NoxBridge {
    data class Server(val guid: String, val name: String, val info: String, val subId: String)
    data class Sub(val id: String, val name: String, val url: String)

    const val TEST_URL = "https://www.gstatic.com/generate_204"

    fun servers(): List<Server> = MmkvManager.decodeServerList().mapNotNull { guid ->
        MmkvManager.decodeServerConfig(guid)?.let { p ->
            Server(guid, p.remarks, "${p.configType.name} / ${p.network ?: "tcp"} / ${p.security ?: "none"}", p.subscriptionId)
        }
    }

    fun subs(): List<Sub> = MmkvManager.decodeSubscriptions().map { (id, item) -> Sub(id, item.remarks, item.url) }

    /** Импорт: подписка-ссылка (http), vless/vmess/trojan/ss/hysteria2/tuic/wireguard ссылки, base64-список, JSON-конфиг. */
    fun importText(text: String): Int {
        val t = text.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) { addSub(t.substringAfter("//").substringBefore("/"), t); return 1 }
        return AngConfigManager.importBatchConfig(t, "", false).first
    }

    fun addSub(name: String, url: String) {
        val id = Utils.getUuid()
        val item = SubscriptionItem(remarks = name, url = url)
        MmkvManager.encodeSubscription(id, item)
        AngConfigManager.updateConfigViaSub(Pair(id, item))
    }

    fun updateAllSubs(): Int = MmkvManager.decodeSubscriptions().sumOf { AngConfigManager.updateConfigViaSub(it) }
    fun removeSub(id: String) = MmkvManager.removeSubscription(id)
    fun removeServer(guid: String) = MmkvManager.removeServer(guid)
    fun select(guid: String) = MmkvManager.setSelectServer(guid)
    fun selected(): String = MmkvManager.getSelectServer().orEmpty()
    fun isRunning(): Boolean = V2RayServiceManager.isRunning()
    fun start(ctx: Context) = V2RayServiceManager.startVService(ctx)
    fun stop(ctx: Context) = V2RayServiceManager.stopVService(ctx)

    fun setSetting(key: String, v: Boolean) = MmkvManager.encodeSettings(key, v)
    fun getSetting(key: String, def: Boolean) = MmkvManager.decodeSettingsBool(key, def)

    /**
     * РЕАЛЬНЫЙ пинг: Xray сам делает HTTPS-запрос ЧЕРЕЗ сервер (не TCP-пинг до хоста).
     * Возвращает мс, либо -1 при таймауте/ошибке.
     */
    suspend fun realPing(ctx: Context, guid: String, timeoutMs: Long): Long = withContext(Dispatchers.IO) {
        try {
            val cfg = V2rayConfigManager.getV2rayConfig(ctx, guid)
            if (!cfg.status) return@withContext -1L
            withTimeoutOrNull(timeoutMs) {
                runInterruptible { Libv2ray.measureOutboundDelay(cfg.content, TEST_URL) }
            } ?: -1L
        } catch (e: Throwable) { -1L }
    }
}

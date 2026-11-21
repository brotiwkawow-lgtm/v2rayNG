package com.v2ray.ang.nox

import android.content.Context
import android.util.Log
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Все обращения к внутренностям v2rayNG только здесь. */
object NoxBridge {
    data class Server(val guid: String, val name: String, val info: String, val subId: String)
    data class Sub(val id: String, val name: String, val url: String)

    const val TEST_URL = "https://www.gstatic.com/generate_204"
    private const val TAG = "NoxBridge"
    private const val SERVICE_CLASS = "com.v2ray.ang.core.CoreServiceManager"

    fun servers(): List<Server> = MmkvManager.decodeServerList().mapNotNull { guid ->
        MmkvManager.decodeServerConfig(guid)?.let { p ->
            Server(
                guid,
                p.remarks,
                "${p.configType.name} / ${p.network ?: "tcp"} / ${p.security ?: "none"}",
                p.subscriptionId
            )
        }
    }

    fun subs(): List<Sub> = MmkvManager.decodeSubscriptions().map {
        Sub(it.guid, it.subscription.remarks, it.subscription.url)
    }

    /** Ссылка подписки, vless/vmess/trojan/ss/hysteria2/wireguard, base64-список или JSON. */
    fun importText(text: String): Int {
        val t = text.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) {
            addSub(t.substringAfter("//").substringBefore("/"), t)
            return 1
        }
        return AngConfigManager.importBatchConfig(t, "", false).first
    }

    fun addSub(name: String, url: String) {
        val id = Utils.getUuid()
        val item = SubscriptionItem()
        item.remarks = name
        item.url = url
        MmkvManager.encodeSubscription(id, item)
        AngConfigManager.updateConfigViaSubAll()
    }

    fun updateAllSubs(): Int {
        AngConfigManager.updateConfigViaSubAll()
        return 0
    }

    fun removeSub(id: String) = MmkvManager.removeSubscription(id)
    fun removeServer(guid: String) = MmkvManager.removeServer(guid)
    fun select(guid: String) = MmkvManager.setSelectServer(guid)
    fun selected(): String = MmkvManager.getSelectServer().orEmpty()

    fun setSetting(key: String, v: Boolean) = MmkvManager.encodeSettings(key, v)
    fun getSetting(key: String, def: Boolean) = MmkvManager.decodeSettingsBool(key, def)

    // ---- запуск / остановка VPN (CoreServiceManager) ----
    private fun callService(names: List<String>, vararg args: Any?): Any? {
        return try {
            val cls = Class.forName(SERVICE_CLASS)
            val inst = runCatching { cls.getField("INSTANCE").get(null) }.getOrNull()
            val m = cls.methods.firstOrNull { it.name in names && it.parameterTypes.size == args.size }
            if (m == null) { Log.e(TAG, "no method in $names"); null } else m.invoke(inst, *args)
        } catch (e: Throwable) { Log.e(TAG, "callService failed", e); null }
    }

    fun isRunning(): Boolean = callService(listOf("isRunning")) as? Boolean ?: false
    fun start(ctx: Context) { callService(listOf("startVService", "startService", "startCore", "start"), ctx) }
    fun stop(ctx: Context) { callService(listOf("stopVService", "stopService", "stopCore", "stop"), ctx) }

    /**
     * РЕАЛЬНЫЙ пинг: ядро делает HTTPS-запрос ЧЕРЕЗ сервер.
     * Возвращает мс или -1 при таймауте/ошибке.
     */
    suspend fun realPing(ctx: Context, guid: String, timeoutMs: Long): Long = withContext(Dispatchers.IO) {
        try {
            CoreNativeManager.initCoreEnv(ctx)
            val cfg = CoreConfigManager.getV2rayConfig4Speedtest(ctx, guid)
            if (!cfg.status) return@withContext -1L
            withTimeoutOrNull(timeoutMs) {
                runInterruptible { CoreNativeManager.measureOutboundDelay(cfg.content, TEST_URL) }
            } ?: -1L
        } catch (e: Throwable) { -1L }
    }
}

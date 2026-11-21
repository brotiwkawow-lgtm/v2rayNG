package com.v2ray.ang.nox

import android.content.Context
import android.util.Log
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
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

    /** GUID всех серверов: ручные ("") и из каждой подписки. */
    private fun allGuids(): List<String> {
        val ids = listOf("") + MmkvManager.decodeSubscriptions().map { it.guid }
        return ids.flatMap { id ->
            runCatching { MmkvManager.decodeServerList(id) }.getOrDefault(mutableListOf())
        }.distinct()
    }

    fun servers(): List<Server> = allGuids().mapNotNull { guid ->
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
        val r = AngConfigManager.importBatchConfig(text.trim(), "", false)
        return r.first + r.second
    }

    fun addSub(name: String, url: String) {
        AngConfigManager.importBatchConfig(url.trim(), "", false)
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

    /** РЕАЛЬНЫЙ пинг: ядро делает HTTPS-запрос ЧЕРЕЗ сервер. -1 при таймауте/ошибке. */
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

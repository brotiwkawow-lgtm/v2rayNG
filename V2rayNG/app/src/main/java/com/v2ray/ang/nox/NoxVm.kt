package com.v2ray.ang.nox

import android.app.Application
import android.net.TrafficStats
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

const val AUTO = "auto"

class NoxVm(val app: Application) : AndroidViewModel(app) {
    var servers by mutableStateOf(listOf<NoxBridge.Server>())
    var subs by mutableStateOf(listOf<NoxBridge.Sub>())
    val pings = mutableStateMapOf<String, Long>()   // -2 проверяется, -1 ошибка, иначе мс
    var selected by mutableStateOf(AUTO)
    var connected by mutableStateOf(false)
    var connecting by mutableStateOf(false)
    var pinging by mutableStateOf(false)
    var night by mutableStateOf(NoxPrefs.night(app))
    var starsOn by mutableStateOf(NoxPrefs.stars(app))
    var timeout by mutableIntStateOf(NoxPrefs.timeoutSec(app))
    var sortByPing by mutableStateOf(NoxPrefs.sortByPing(app))
    var autoUpdate by mutableStateOf(NoxPrefs.autoUpdate(app))
    var favs by mutableStateOf(NoxPrefs.favs(app))
    var query by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var seconds by mutableLongStateOf(0L)
    var down by mutableLongStateOf(0L)
    var up by mutableLongStateOf(0L)

    init {
        refresh()
        if (autoUpdate) viewModelScope.launch { updateSubs(silent = true) }
        viewModelScope.launch {
            var lastRx = TrafficStats.getTotalRxBytes(); var lastTx = TrafficStats.getTotalTxBytes()
            while (true) {
                val run = withContext(Dispatchers.IO) { NoxBridge.isRunning() }
                if (run && !connected) seconds = 0
                connected = run
                if (run) { connecting = false; seconds++ }
                val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes()
                down = if (run) rx - lastRx else 0; up = if (run) tx - lastTx else 0
                lastRx = rx; lastTx = tx
                delay(1000)
            }
        }
    }

    fun refresh() {
        servers = NoxBridge.servers(); subs = NoxBridge.subs()
        val s = NoxBridge.selected()
        if (selected != AUTO && servers.none { it.guid == s }) selected = AUTO
    }

    val shown: List<NoxBridge.Server>
        get() {
            var l = servers.filter { query.isBlank() || it.name.contains(query, true) }
            l = if (sortByPing) l.sortedWith(compareBy({ it.guid !in favs }, { pings[it.guid]?.takeIf { p -> p > 0 } ?: Long.MAX_VALUE }))
            else l.sortedBy { it.guid !in favs }
            return l
        }

    fun toggleTheme() { night = !night; NoxPrefs.setNight(app, night) }
    fun toggleFav(g: String) { favs = if (g in favs) favs - g else favs + g; NoxPrefs.setFavs(app, favs) }

    fun pingAll() {
        if (pinging) return
        pinging = true
        viewModelScope.launch {
            val sem = Semaphore(8)
            val list = servers
            list.forEach { pings[it.guid] = -2 }
            list.map { s -> async { sem.withPermit { pings[s.guid] = NoxBridge.realPing(app, s.guid, timeout * 1000L) } } }.awaitAll()
            pinging = false
        }
    }

    /** Перед запуском: если выбран «Автовыбор» — найти самый быстрый сервер. */
    suspend fun resolveSelection(): Boolean {
        refresh()
        if (servers.isEmpty()) { message = "Сначала добавь подписку"; return false }
        if (selected == AUTO) {
            connecting = true; pinging = true
            servers.forEach { pings[it.guid] = -2 }
            val sem = Semaphore(8)
            servers.map { s -> viewModelScope.async { sem.withPermit { pings[s.guid] = NoxBridge.realPing(app, s.guid, timeout * 1000L) } } }.awaitAll()
            pinging = false
            val best = servers.filter { (pings[it.guid] ?: -1) > 0 }.minByOrNull { pings[it.guid]!! }
            if (best == null) { connecting = false; message = "Ни один сервер не ответил"; return false }
            NoxBridge.select(best.guid)
        } else NoxBridge.select(selected)
        connecting = true
        viewModelScope.launch { delay(12000); if (!connected) { connecting = false } }
        return true
    }

    fun pick(g: String) { selected = g; if (g != AUTO) NoxBridge.select(g) }

    fun importText(t: String) = viewModelScope.launch(Dispatchers.IO) {
        val n = runCatching { NoxBridge.importText(t) }.getOrElse { -1 }
        withContext(Dispatchers.Main) { refresh(); message = if (n > 0) "Добавлено" else "Не удалось распознать" }
    }

    fun addSub(name: String, url: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { NoxBridge.addSub(name.ifBlank { "Подписка" }, url.trim()) }
        withContext(Dispatchers.Main) { refresh(); message = "Подписка добавлена" }
    }

    fun updateSubs(silent: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val n = runCatching { NoxBridge.updateAllSubs() }.getOrElse { -1 }
        withContext(Dispatchers.Main) { refresh(); if (!silent) message = if (n >= 0) "Обновлено серверов: $n" else "Ошибка обновления" }
    }

    fun removeSub(id: String) { NoxBridge.removeSub(id); refresh() }
    fun removeServer(g: String) { NoxBridge.removeServer(g); refresh() }
}

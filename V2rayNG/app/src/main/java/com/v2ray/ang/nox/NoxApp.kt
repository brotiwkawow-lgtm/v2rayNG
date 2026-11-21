package com.v2ray.ang.nox

import android.content.ClipboardManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.alpha
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen(val title: String) { Home("Главная"), Servers("Серверы"), Subs("Подписки"), Settings("Настройки"), Sponsors("Спонсоры") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoxApp(vm: NoxVm, onToggle: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val colors = if (vm.night) NightColors else DayColors
    var splash by remember { mutableStateOf(true) }
    val sky = when { vm.pinging -> SkyMode.PING; vm.connected -> SkyMode.ON; else -> SkyMode.OFF }

    CompositionLocalProvider(LocalNox provides colors) {
        Box(Modifier.fillMaxSize()) {
            NoxSky(sky, vm.night, vm.starsOn)
            if (splash) MoonSplash(vm.night) { splash = false } else MainShell(vm, onToggle, ctx)
        }
    }
}

/** Заставка: луна/солнце серое → белое → загорается → исчезает, потом видно меню. */
@Composable
fun MoonSplash(night: Boolean, onDone: () -> Unit) {
    val c = LocalNox.current
    val fill = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        fill.animateTo(1f, tween(1300))
        glow.animateTo(1f, tween(800))
        delay(250)
        alpha.animateTo(0f, tween(500))
        onDone()
    }
    val gray = if (night) Color(0xFF3A3040) else Color(0xFFD5DCE2)
    val white = if (night) Color.White else Color(0xFFFFF4C2)
    val col = lerp(lerp(gray, white, fill.value), c.accent, glow.value)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NoxBody(night, col, glow.value, Modifier.size(300.dp).graphicsAlpha(alpha.value), spin = true)
    }
}
private fun Modifier.graphicsAlpha(a: Float) = this.alpha(a)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(vm: NoxVm, onToggle: () -> Unit, ctx: Context) {
    val c = LocalNox.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Home) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = if (vm.night) Color(0xFF1A0B18) else Color(0xFFFFF8EA)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.nox_logo), "Nox", Modifier.size(84.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(8.dp))
                    Text("NOX VPN", color = c.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Screen.values().forEach { s ->
                    NavigationDrawerItem(
                        label = { Text(s.title, color = if (s == screen) c.accent else c.text) },
                        selected = s == screen,
                        onClick = { screen = s; scope.launch { drawer.close() } },
                        colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = c.card, unselectedContainerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", color = c.accent, fontSize = 28.sp, modifier = Modifier.clickable { scope.launch { drawer.open() } }.padding(12.dp))
                    Text(screen.title, color = c.text, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    Text(if (vm.night) "☾" else "☀", color = c.accent, fontSize = 26.sp, modifier = Modifier.clickable { vm.toggleTheme() }.padding(10.dp))
                    Box {
                        Text("+", color = c.accent, fontSize = 30.sp, modifier = Modifier.clickable { menu = true }.padding(horizontal = 14.dp))
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Добавить подписку") }, { menu = false; dialog = "sub" })
                            DropdownMenuItem({ Text("Вставить из буфера обмена") }, {
                                menu = false
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val t = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (t.isBlank()) vm.message = "Буфер пуст" else vm.importText(t)
                            })
                            DropdownMenuItem({ Text("QR-код") }, {
                                menu = false
                                runCatching { ctx.startActivity(Intent().setClassName(ctx, "com.v2ray.ang.ui.ScannerActivity")) }
                                    .onFailure { vm.message = "Сканер недоступен" }
                            })
                            DropdownMenuItem({ Text("Ручной ввод") }, { menu = false; dialog = "manual" })
                            DropdownMenuItem({ Text("Вставить JSON") }, { menu = false; dialog = "json" })
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                Crossfade(screen, label = "scr") {
                    when (it) {
                        Screen.Home -> HomeScreen(vm, onToggle)
                        Screen.Servers -> ServersScreen(vm)
                        Screen.Subs -> SubsScreen(vm) { dialog = "sub" }
                        Screen.Settings -> SettingsScreen(vm, ctx)
                        Screen.Sponsors -> SponsorsScreen(ctx)
                    }
                }
            }
        }
    }

    dialog?.let { kind ->
        var name by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(when (kind) { "sub" -> "Новая подписка"; "json" -> "Вставить JSON"; else -> "Ручной ввод" }) },
            text = {
                Column {
                    if (kind == "sub") OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(text, { text = it }, label = { Text(if (kind == "sub") "Ссылка подписки" else "Ссылка или конфиг") }, minLines = if (kind == "sub") 1 else 5)
                }
            },
            confirmButton = { TextButton({
                if (text.isNotBlank()) { if (kind == "sub") vm.addSub(name, text) else vm.importText(text) }
                dialog = null
            }) { Text("Добавить") } },
            dismissButton = { TextButton({ dialog = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun Glass(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNox.current
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).border(1.dp, c.cardBorder, RoundedCornerShape(22.dp))
    Column((if (onClick != null) m.clickable { onClick() } else m).padding(16.dp), content = content)
}

private fun fmtSpeed(b: Long) = if (b > 1_048_576) "%.1f МБ/с".format(b / 1048576f) else "%d КБ/с".format(b / 1024)

@Composable
fun HomeScreen(vm: NoxVm, onToggle: () -> Unit) {
    val c = LocalNox.current
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pp")
    val col by animateColorAsState(if (vm.connected || vm.connecting) c.accent else c.off, tween(600), label = "col")
    val glow by animateFloatAsState(if (vm.connected) 1f else if (vm.connecting) pulse else 0f, label = "g")
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        NoxBody(vm.night, col, glow, Modifier.size(300.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onToggle() }, spin = vm.connected)
        Text(
            when { vm.connected -> "Подключено"; vm.connecting -> "Подключаюсь…"; else -> if (vm.night) "Нажми на луну" else "Нажми на солнце" },
            color = if (vm.connected) c.accent else c.sub, fontSize = 22.sp
        )
        if (vm.connected) Text("%02d:%02d:%02d".format(vm.seconds / 3600, vm.seconds / 60 % 60, vm.seconds % 60), color = c.text, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Glass {
            Text("↓ ${fmtSpeed(vm.down)}     ↑ ${fmtSpeed(vm.up)}", color = c.text, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        val cur = if (vm.selected == AUTO) "⚡ Автовыбор (самый быстрый)" else vm.servers.firstOrNull { it.guid == vm.selected }?.name ?: "—"
        Glass { Text("Сервер", color = c.sub, fontSize = 13.sp); Text(cur, color = c.text, fontSize = 17.sp) }
    }
}

@Composable
fun ServersScreen(vm: NoxVm) {
    val c = LocalNox.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ vm.pingAll() }) { Text(if (vm.pinging) "Проверяю…" else "Проверить пинг", color = c.accent) }
            Spacer(Modifier.weight(1f))
            Text("${vm.servers.size} шт.", color = c.sub)
        }
        OutlinedTextField(vm.query, { vm.query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Поиск") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ServerRow(vm, AUTO, "⚡ Автовыбор", "Самый быстрый по реальному пингу", null) }
            items(vm.shown, key = { it.guid }) { s -> ServerRow(vm, s.guid, s.name, s.info, vm.pings[s.guid]) }
        }
    }
}

@Composable
fun ServerRow(vm: NoxVm, guid: String, name: String, info: String, ping: Long?) {
    val c = LocalNox.current
    Glass(onClick = { vm.pick(guid) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(vm.selected == guid, { vm.pick(guid) }, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.sub))
            Column(Modifier.weight(1f)) {
                Text(name, color = c.text, fontSize = 16.sp)
                Text(info, color = c.sub, fontSize = 12.sp)
            }
            when {
                ping == -2L -> CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                ping != null && ping > 0 -> Text("$ping мс", color = if (ping < 300) Color(0xFF4CD964) else if (ping < 800) Color(0xFFFFC107) else Color(0xFFFF5252))
                ping == -1L -> Text("—", color = Color(0xFFFF5252))
            }
            if (guid != AUTO) {
                Text(if (guid in vm.favs) "★" else "☆", color = c.accent, fontSize = 22.sp, modifier = Modifier.clickable { vm.toggleFav(guid) }.padding(8.dp))
                Text("✕", color = c.sub, modifier = Modifier.clickable { vm.removeServer(guid) }.padding(4.dp))
            }
        }
    }
}

@Composable
fun SubsScreen(vm: NoxVm, onAdd: () -> Unit) {
    val c = LocalNox.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            TextButton({ vm.updateSubs() }) { Text("Обновить все", color = c.accent) }
            TextButton(onAdd) { Text("Добавить", color = c.accent) }
        }
        if (vm.subs.isEmpty()) Text("Подписок пока нет. Нажми «Добавить» или «+» сверху.", color = c.sub)
        vm.subs.forEach { s ->
            Glass {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.name} (${vm.servers.count { it.subId == s.id }})", color = c.text, fontSize = 17.sp)
                        Text(s.url.take(40) + "…", color = c.sub, fontSize = 12.sp)
                    }
                    TextButton({ vm.removeSub(s.id) }) { Text("Удалить", color = c.accent) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: NoxVm, ctx: Context) {
    val c = LocalNox.current
    var frag by remember { mutableStateOf(NoxBridge.getSetting("pref_fragment_enabled", false)) }
    var mux by remember { mutableStateOf(NoxBridge.getSetting("pref_mux_enabled", false)) }
    var lan by remember { mutableStateOf(NoxBridge.getSetting("pref_allow_insecure_lan", false)) }
    fun open(cls: String) = runCatching { ctx.startActivity(Intent().setClassName(ctx, "com.v2ray.ang.ui.$cls")) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SwitchRow("Тёмная тема (луна)", vm.night) { vm.toggleTheme() }
        SwitchRow("Звёздный фон / искры", vm.starsOn) { vm.starsOn = it; NoxPrefs.setStars(ctx, it) }
        SwitchRow("Сортировать серверы по пингу", vm.sortByPing) { vm.sortByPing = it; NoxPrefs.setSortByPing(ctx, it) }
        SwitchRow("Автообновление подписок при запуске", vm.autoUpdate) { vm.autoUpdate = it; NoxPrefs.setAutoUpdate(ctx, it) }
        SwitchRow("Фрагментирование TLS (обход блокировок)", frag) { frag = it; NoxBridge.setSetting("pref_fragment_enabled", it) }
        SwitchRow("Mux", mux) { mux = it; NoxBridge.setSetting("pref_mux_enabled", it) }
        SwitchRow("Разрешить подключения из LAN", lan) { lan = it; NoxBridge.setSetting("pref_allow_insecure_lan", it) }
        Glass {
            Text("Таймаут проверки пинга", color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 8).forEach { s ->
                    FilterChip(vm.timeout == s, { vm.timeout = s; NoxPrefs.setTimeoutSec(ctx, s) }, { Text("$s с") })
                }
            }
        }
        Glass(onClick = { open("RoutingSettingsActivity") }) { Text("Маршрутизация  ›", color = c.text) }
        Glass(onClick = { open("PerAppProxyActivity") }) { Text("Прокси для выбранных приложений  ›", color = c.text) }
        Glass(onClick = { open("SettingsActivity") }) { Text("Расширенные настройки VPN  ›", color = c.text) }
        Text("Изменения применяются при следующем подключении.", color = c.sub, fontSize = 12.sp)
    }
}

@Composable
fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalNox.current
    Glass { Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.text, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent.copy(alpha = 0.7f), checkedThumbColor = Color.White))
    } }
}

@Composable
fun SponsorsScreen(ctx: Context) {
    val c = LocalNox.current
    fun tg(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$u")))
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Glass(onClick = { tg("nyyaecho") }) { Text("Спонсор", color = c.sub, fontSize = 13.sp); Text("@nyyaecho", color = c.accent, fontSize = 20.sp) }
        Glass(onClick = { tg("Liiib1do") }) { Text("Основной разработчик", color = c.sub, fontSize = 13.sp); Text("@Liiib1do", color = c.accent, fontSize = 20.sp) }
        Glass { Text("Клан", color = c.sub, fontSize = 13.sp); Text("#Nox", color = c.accent, fontSize = 20.sp) }
    }
}

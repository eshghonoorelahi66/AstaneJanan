package com.astanjanan.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Navy = Color(0xFF101B30)
private val CardNavy = Color(0xFF1B2B45)
private val Gold = Color(0xFFD6B66F)
private val Ivory = Color(0xFFEDE5D5)
private val Muted = Color(0xFFB9A98A)
private val Emerald = Color(0xFF1F493F)
private const val PREFS = "astane_janan_data"
private const val DHIKR_KEY = "dhikr_items_v2"
private const val CHANNEL_ID = "dhikr_completion"

private data class DhikrRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val target: Int,
    val count: Int = 0,
    val alarm: Boolean = true,
    val archived: Boolean = false,
    val history: List<String> = emptyList()
)

private fun loadDhikrs(context: Context): List<DhikrRecord> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DHIKR_KEY, null) ?: return listOf(
        DhikrRecord(title = "سُبْحَانَ اللّٰهِ", description = "تسبیح و یاد خداوند", target = 100),
        DhikrRecord(title = "اَلْحَمْدُ لِلّٰهِ", description = "حمد و سپاس پروردگار", target = 100)
    )
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val h = o.optJSONArray("history") ?: JSONArray()
            DhikrRecord(
                id = o.optString("id"), title = o.optString("title"), description = o.optString("description"),
                target = o.optInt("target", 100).coerceAtLeast(1), count = o.optInt("count"),
                alarm = o.optBoolean("alarm", true), archived = o.optBoolean("archived", false),
                history = (0 until h.length()).map { h.optString(it) }
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveDhikrs(context: Context, records: List<DhikrRecord>) {
    val array = JSONArray()
    records.forEach { r ->
        val history = JSONArray().apply { r.history.forEach { put(it) } }
        array.put(JSONObject().apply {
            put("id", r.id); put("title", r.title); put("description", r.description)
            put("target", r.target); put("count", r.count); put("alarm", r.alarm)
            put("archived", r.archived); put("history", history)
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DHIKR_KEY, array.toString()).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 27, 48)
        window.navigationBarColor = android.graphics.Color.rgb(16, 27, 48)
        createNotificationChannel()
        setContent { AstaneJananApp() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "اتمام ذکر", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

private enum class Destination(val label: String) { HOME("خانه"), QURAN("قرآن"), DHIKR("ذکر"), PRAYER("اوقات"), MORE("بیشتر") }

@Composable
private fun AstaneJananApp() {
    var selected by remember { mutableStateOf(Destination.HOME) }
    var tradition by remember { mutableStateOf("هر دو") }
    var darkMode by remember { mutableStateOf(true) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Gold, secondary = Emerald, background = Navy, surface = CardNavy, onPrimary = Navy, onBackground = Ivory, onSurface = Ivory)) {
            Scaffold(containerColor = Navy, bottomBar = {
                NavigationBar(containerColor = Color(0xFF0C1628), tonalElevation = 0.dp) {
                    Destination.values().forEach { d ->
                        val icon = when (d) { Destination.HOME -> Icons.Default.Home; Destination.QURAN -> Icons.Default.MenuBook; Destination.DHIKR -> Icons.Default.Fingerprint; Destination.PRAYER -> Icons.Default.AccessTime; Destination.MORE -> Icons.Default.GridView }
                        NavigationBarItem(selected = selected == d, onClick = { selected = d }, icon = { Icon(icon, d.label) }, label = { Text(d.label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = CardNavy))
                    }
                }
            }) { padding ->
                when (selected) {
                    Destination.HOME -> HomeScreen(padding, tradition) { selected = it }
                    Destination.QURAN -> ModuleScreen(padding, "قرآن کریم", "مصحف، ترجمه، نشانک‌ها و ادامه تلاوت", listOf("فهرست سوره‌ها", "ادامه آخرین تلاوت", "ترجمه و تفسیر", "نشانک‌ها و یادداشت‌ها"))
                    Destination.DHIKR -> DhikrScreen(padding)
                    Destination.PRAYER -> ModuleScreen(padding, "اوقات شرعی", "اوقات نماز به‌صورت محلی و بدون نیاز به اینترنت", listOf("فجر", "طلوع آفتاب", "ظهر", "عصر", "غروب", "مغرب", "عشاء"))
                    Destination.MORE -> MoreScreen(padding, tradition, { tradition = it }, darkMode, { darkMode = it })
                }
            }
        }
    }
}

@Composable private fun Header(subtitle: String = "همراه معنوی و آسمانی شما") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("آستان جانان", color = Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) }
        Surface(shape = RoundedCornerShape(14.dp), color = CardNavy) { Icon(Icons.Default.AutoAwesome, "نشان آستان جانان", tint = Gold, modifier = Modifier.padding(12.dp).size(22.dp)) }
    }
}

@Composable private fun HomeScreen(padding: PaddingValues, tradition: String, navigate: (Destination) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header(); Text("امروز · تاریخ محلی دستگاه", color = Muted, fontSize = 12.sp)
        Surface(color = CardNavy, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نماز بعدی · نمایش نمونه", color = Muted, fontSize = 13.sp); Text("مغرب", color = Ivory, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("--:--", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("زمان واقعی پس از تکمیل موتور محاسبه اوقات فعال می‌شود.", color = Muted, fontSize = 11.sp)
            }
        }
        Text("دسترسی سریع", color = Ivory, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickCard("ادامه قرآن", "آخرین آیه", Icons.Default.MenuBook, Modifier.weight(1f)) { navigate(Destination.QURAN) }; QuickCard("ذکرهای من", "دفترچه ذکر", Icons.Default.Fingerprint, Modifier.weight(1f)) { navigate(Destination.DHIKR) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickCard("اوقات شرعی", "نماز و اذان", Icons.Default.AccessTime, Modifier.weight(1f)) { navigate(Destination.PRAYER) }; QuickCard("نجوم و آسمان", "ماه و سیارات", Icons.Default.DarkMode, Modifier.weight(1f)) { navigate(Destination.MORE) } }
        Surface(color = Color(0xFF182B38), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, tint = Gold, modifier = Modifier.size(28.dp), contentDescription = null); Spacer(Modifier.width(12.dp)); Column { Text("فضای معنوی شما", color = Ivory, fontWeight = FontWeight.SemiBold); Text("رویکرد محتوایی: $tradition", color = Muted, fontSize = 12.sp) } } }
        Text("آستان جانان · نسخه ۰.۲.۰", color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable private fun QuickCard(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = CardNavy, shape = RoundedCornerShape(18.dp), modifier = modifier.height(112.dp).clickable { onClick() }) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = Gold, modifier = Modifier.size(24.dp)); Column { Text(title, color = Ivory, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(detail, color = Muted, fontSize = 11.sp) } } }
}

@Composable private fun ModuleScreen(padding: PaddingValues, title: String, description: String, items: List<String>) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header(title); Text(description, color = Muted, fontSize = 13.sp)
        items.forEachIndexed { i, item -> Surface(color = CardNavy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Text("${i + 1}".padStart(2, '0'), color = Gold, fontWeight = FontWeight.Bold); Spacer(Modifier.width(14.dp)); Text(item, color = Ivory, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronLeft, tint = Muted, contentDescription = null) } } }
        Text("این بخش در حال توسعه است و محتوای نهایی آفلاین در نسخه‌های بعدی افزوده می‌شود.", color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun DhikrScreen(padding: PaddingValues) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var records by remember { mutableStateOf(loadDhikrs(context)) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DhikrRecord?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf<DhikrRecord?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun persist(next: List<DhikrRecord>) { records = next; saveDhikrs(context, next) }
    val active = records.filter { it.archived.not() }
    val archived = records.filter { it.archived }
    val selected = active.firstOrNull { it.id == selectedId } ?: active.firstOrNull()

    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("دفترچه ذکرهای شخصی")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ذکرهای من · ${active.size}", color = Ivory, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "ذکرهای فعال" else "آرشیو", color = Gold) }
            IconButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Default.AddCircle, "افزودن ذکر", tint = Gold) }
        }
        if (showArchived) {
            Text("آرشیو ذکرها", color = Gold, fontWeight = FontWeight.Bold)
            if (archived.isEmpty()) Text("هنوز ذکری به آرشیو منتقل نشده است.", color = Muted)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(archived, key = { it.id }) { item -> DhikrListCard(item, false, onSelect = { showHistory = item }, onEdit = { editing = item; showEditor = true }, onArchive = { persist(records.map { if (it.id == item.id) it.copy(archived = false) else it }) }) } }
        } else {
            if (active.isEmpty()) Text("هنوز ذکری تعریف نکرده‌اید. با دکمه + ذکر جدید بسازید.", color = Muted)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(active, key = { it.id }) { item -> DhikrListCard(item, selected?.id == item.id, onSelect = { selectedId = item.id }, onEdit = { editing = item; showEditor = true }, onArchive = { persist(records.map { if (it.id == item.id) it.copy(archived = true) else it }) }) }
            }
            if (selected != null) {
                Surface(color = CardNavy, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selected.title, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        if (selected.description.isNotBlank()) Text(selected.description, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Text("${selected.count} / ${selected.target}", color = Ivory, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = (selected.count.toFloat() / selected.target).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = Navy)
                        Surface(color = Navy, shape = RoundedCornerShape(100.dp), modifier = Modifier.size(148.dp).clickable { incrementDhikr(context, selected, records, ::persist) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.TouchApp, null, tint = Gold, modifier = Modifier.size(28.dp)); Text("ذکر", color = Ivory, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("برای شمارش لمس کنید", color = Muted, fontSize = 10.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { persist(records.map { if (it.id == selected.id) it.copy(count = 0) else it }) }) { Text("صفر کردن") }
                            OutlinedButton(onClick = { showHistory = selected }) { Text("تاریخچه") }
                            Button(onClick = { incrementDhikr(context, selected, records, ::persist) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)) { Text("+۱") }
                        }
                        Text(if (selected.alarm) "هشدار پایان فعال است" else "هشدار پایان غیرفعال است", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
    if (showEditor) DhikrEditorDialog(editing, onDismiss = { showEditor = false }, onSave = { value ->
        val next = if (editing == null) records + value else records.map { if (it.id == value.id) value else it }
        persist(next); selectedId = value.id; showEditor = false
    })
    showHistory?.let { item -> DhikrHistoryDialog(item, onDismiss = { showHistory = null }) }
}

private fun incrementDhikr(context: Context, item: DhikrRecord, all: List<DhikrRecord>, save: (List<DhikrRecord>) -> Unit) {
    val updated = item.copy(count = item.count + 1)
    val completedNow = item.count < item.target && updated.count >= item.target
    val history = if (completedNow) updated.history + timestampNow() else updated.history
    save(all.map { if (it.id == item.id) updated.copy(history = history) else it })
    if (completedNow && item.alarm) sendCompletionNotification(context, item.title)
}

private fun timestampNow(): String = SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.getDefault()).format(Date())
private fun sendCompletionNotification(context: Context, title: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("ذکر به پایان رسید")
        .setContentText("تعداد تعیین‌شده برای «$title» کامل شد.").setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
    if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) manager.notify(title.hashCode(), notification)
}

@Composable private fun DhikrListCard(item: DhikrRecord, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit) {
    Surface(color = if (selected) Color(0xFF263A53) else CardNavy, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().clickable { onSelect() }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.title, color = if (selected) Gold else Ivory, fontWeight = FontWeight.SemiBold); Text("${item.count} از ${item.target}  ·  ${item.history.size} بار تکمیل", color = Muted, fontSize = 10.sp) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "ویرایش", tint = Muted) }
            IconButton(onClick = onArchive) { Icon(if (item.archived) Icons.Default.Unarchive else Icons.Default.Archive, "انتقال به آرشیو", tint = Gold) }
        }
    }
}

@Composable private fun DhikrEditorDialog(existing: DhikrRecord?, onDismiss: () -> Unit, onSave: (DhikrRecord) -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var target by remember { mutableStateOf((existing?.target ?: 100).toString()) }
    var alarm by remember { mutableStateOf(existing?.alarm ?: true) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardNavy, title = { Text(if (existing == null) "تعریف ذکر جدید" else "ویرایش ذکر", color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("نام یا متن ذکر") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("توضیحات") }, minLines = 2)
                OutlinedTextField(value = target, onValueChange = { target = it.filter { ch -> ch.isDigit() }.take(8) }, label = { Text("تعداد هدف") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("اعلان هنگام اتمام تعداد", color = Ivory, modifier = Modifier.weight(1f)); Switch(checked = alarm, onCheckedChange = { alarm = it }) }
            }
        }, confirmButton = { TextButton(onClick = { val n = target.toIntOrNull() ?: 0; if (title.isNotBlank() && n > 0) onSave((existing ?: DhikrRecord(title = title, description = description, target = n)).copy(title = title.trim(), description = description.trim(), target = n, alarm = alarm)) }) { Text("ذخیره", color = Gold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = Muted) } })
}

@Composable private fun DhikrHistoryDialog(item: DhikrRecord, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = CardNavy, title = { Text("آرشیو و تاریخچه · ${item.title}", color = Gold) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تعداد هدف: ${item.target}  |  دفعات تکمیل: ${item.history.size}", color = Ivory)
            if (item.history.isEmpty()) Text("هنوز این ذکر به تعداد هدف نرسیده است.", color = Muted)
            item.history.asReversed().forEachIndexed { index, date -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${index + 1}.", color = Gold); Text(date, color = Ivory) } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("بستن", color = Gold) } })
}

@Composable private fun MoreScreen(padding: PaddingValues, tradition: String, setTradition: (String) -> Unit, darkMode: Boolean, setDarkMode: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("بخش‌های آستان جانان")
        listOf("نجوم و آسمان", "تقویم جامع", "ابجد و علوم حروف", "دعا، زیارات و احادیث", "کتابخانه و دانشنامه", "قبله‌نما", "پشتیبان‌گیری و بازیابی").forEach { name -> Surface(color = CardNavy, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, tint = Gold, contentDescription = null); Spacer(Modifier.width(12.dp)); Text(name, color = Ivory, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronLeft, tint = Muted, contentDescription = null) } } }
        Text("رویکرد مذهبی", color = Gold, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("شیعه", "اهل سنت", "هر دو").forEach { option -> FilterChip(selected = tradition == option, onClick = { setTradition(option) }, label = { Text(option) }) } }
        Text("ظاهر برنامه", color = Gold, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("حالت تیره", color = Ivory, modifier = Modifier.weight(1f)); Switch(checked = darkMode, onCheckedChange = setDarkMode) }
        Text("تنظیمات ظاهری در نسخه آزمایشی هستند.", color = Muted, fontSize = 11.sp)
    }
}

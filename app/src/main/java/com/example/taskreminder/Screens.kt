@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.taskreminder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// ---------- ثابت‌ها و کمکی‌ها ----------

private val WEEK = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

val CATEGORIES = listOf(
    "personal" to "شخصی", "work" to "کار", "home" to "خانه",
    "shopping" to "خرید", "finance" to "مالی", "health" to "سلامت"
)

private val PRIORITIES = listOf(0 to "عادی", 1 to "مهم", 2 to "فوری")

fun categoryName(key: String): String = CATEGORIES.firstOrNull { it.first == key }?.second ?: "شخصی"

fun kindColor(kind: String): Color = when (kind) {
    Kind.DAY -> Neon.cyan
    Kind.WEEKLY -> Neon.lime
    else -> Neon.amber
}

fun kindIcon(kind: String): ImageVector = when (kind) {
    Kind.DAY -> Icons.Rounded.WbSunny
    Kind.WEEKLY -> Icons.Rounded.CalendarMonth
    else -> Icons.Rounded.Alarm
}

fun kindTitle(kind: String): String = when (kind) {
    Kind.DAY -> "کار روزانه"
    Kind.WEEKLY -> "برنامهٔ هفتگی"
    else -> "سررسید با زنگ"
}

private fun latinDigits(s: String): String =
    s.map { c -> "۰۱۲۳۴۵۶۷۸۹".indexOf(c).let { if (it >= 0) "0123456789"[it] else c } }.joinToString("")

fun everyLabel(t: TemplateEntity): String {
    val n = t.repeatEvery.coerceAtLeast(1)
    val unit = when (t.unit) {
        Every.DAY -> "روز"
        Every.WEEK -> "هفته"
        Every.MONTH -> "ماه"
        Every.YEAR -> "سال"
        else -> return "یک‌بار"
    }
    return if (n == 1) "هر $unit" else "هر ${fa(n)} $unit"
}

private fun timeOf(min: Int) = timeText(min / 60, min % 60)

fun scheduleSummary(t: TemplateEntity): String = when (t.kind) {
    Kind.DAY -> fullDateText(Jalali.fromEpochDay(t.startDate))
    Kind.WEEKLY -> {
        val days = (0..6).filter { (t.weekMask and (1 shl it)) != 0 }.joinToString("، ") { WEEK[it] }
        days + if (t.timeMin >= 0) "، ساعت " + timeOf(t.timeMin) else ""
    }
    else -> {
        val first = "سررسید " + dateText(Jalali.fromEpochDay(t.startDate)) +
            if (t.timeMin >= 0) "، " + timeOf(t.timeMin) else ""
        if (t.unit == Every.NONE) first else first + " · " + everyLabel(t)
    }
}

private fun moneyText(v: Long): String = fa(String.format(Locale.US, "%,d", v))

@Composable
fun rememberNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private fun isOverdue(i: PlanItem, today: Long, now: Long): Boolean =
    i.inst.status == Status.PENDING &&
        (i.inst.dueDate < today || (i.tpl.kind == Kind.DUE && i.inst.timeMin >= 0 && i.inst.dueAt < now))

// ---------- قاب اصلی ----------

@Composable
fun TaskApp(vm: TaskViewModel) {
    val items by vm.items.collectAsState()
    val templates by vm.templates.collectAsState()
    val now = rememberNow()
    val today = remember(now) {
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }

    var tab by remember { mutableStateOf(0) }
    var chooser by remember { mutableStateOf(false) }
    var editorKind by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TemplateEntity?>(null) }
    var deleting by remember { mutableStateOf<TemplateEntity?>(null) }
    var details by remember { mutableStateOf(false) }

    val openTemplate: (TemplateEntity) -> Unit = { t ->
        editing = t
        editorKind = t.kind
    }
    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Neon.cyan,
        selectedTextColor = Neon.cyan,
        indicatorColor = Neon.cyan.copy(alpha = 0.16f),
        unselectedIconColor = Neon.muted,
        unselectedTextColor = Neon.muted
    )

    Scaffold(
        containerColor = Neon.bg,
        bottomBar = {
            NavigationBar(containerColor = Neon.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Rounded.WbSunny, null) },
                    label = { T("امروز", size = 12.sp) },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.TaskAlt, null) },
                    label = { T("وظایف", size = 12.sp) },
                    colors = navColors
                )
            }
        },
        floatingActionButton = {
            GradientButton("کار جدید", icon = Icons.Rounded.Add) {
                editing = null
                chooser = true
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) {
                Dashboard(
                    items = items,
                    today = today,
                    now = now,
                    onToggle = { vm.setDone(it.inst.id, it.inst.status != Status.DONE) },
                    onOpen = openTemplate,
                    onPostpone = { vm.postpone(it.inst.id) },
                    onDetails = { details = true }
                )
            } else {
                TasksTab(
                    templates = templates,
                    onOpen = openTemplate,
                    onDelete = { deleting = it },
                    onPause = { vm.togglePause(it.id) }
                )
            }
        }
    }

    if (chooser) {
        TypeChooser(
            onPick = {
                chooser = false
                editing = null
                editorKind = it
            },
            onDismiss = { chooser = false }
        )
    }

    editorKind?.let { k ->
        PlanEditor(
            kind = k,
            initial = editing,
            onDismiss = { editorKind = null; editing = null },
            onSave = {
                vm.save(it)
                editorKind = null
                editing = null
            }
        )
    }

    deleting?.let { t ->
        NeonDialog(
            title = "حذف کار",
            confirmText = "حذف",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteTemplate(t.id)
                deleting = null
            }
        ) {
            T(
                if (t.kind == Kind.DAY) "«${t.title}» حذف شود؟"
                else "«${t.title}» با تمام نوبت‌هایش حذف شود؟",
                align = TextAlign.Center, color = Neon.muted
            )
        }
    }

    if (details) {
        val p = Engine.progress(items, today)
        NeonDialog(
            title = "جزئیات محاسبهٔ پیشرفت",
            confirmText = "باشه",
            onDismiss = { details = false },
            onConfirm = { details = false }
        ) {
            T(
                "درصد = کارهای انجام‌شدهٔ امروز ÷ کل کارهای امروز\n" +
                    "${fa(p.done)} ÷ ${fa(p.total)} = ${fa(p.percent)}٪\n\n" +
                    "• هر نوبت فقط یک‌بار شمرده می‌شود.\n" +
                    "• کارهای روزهای آینده و لغوشده‌ها حساب نمی‌شوند.\n" +
                    "• عقب‌افتاده‌ها (${fa(p.overdue)} مورد) جدا نمایش داده می‌شوند و در این درصد نیستند.",
                color = Neon.muted, size = 13.sp
            )
        }
    }
}

// ---------- داشبورد امروز ----------

@Composable
fun Dashboard(
    items: List<PlanItem>,
    today: Long,
    now: Long,
    onToggle: (PlanItem) -> Unit,
    onOpen: (TemplateEntity) -> Unit,
    onPostpone: (PlanItem) -> Unit,
    onDetails: () -> Unit
) {
    val prog = remember(items, today) { Engine.progress(items, today) }
    val overdue = remember(items, today) {
        items.filter { it.inst.status == Status.PENDING && it.inst.dueDate < today }.sortedBy { it.inst.dueAt }
    }
    val todays = remember(items, today) {
        items.filter {
            it.inst.dueDate == today && (it.inst.status == Status.PENDING || it.inst.status == Status.DONE)
        }.sortedWith(
            compareBy<PlanItem>(
                { it.inst.status == Status.DONE },
                { if (it.inst.timeMin < 0) Int.MAX_VALUE else it.inst.timeMin },
                { -it.tpl.priority }
            )
        )
    }
    val upcoming = remember(items, today) {
        items.filter {
            it.inst.status == Status.PENDING && it.inst.dueDate > today && it.inst.dueDate <= today + 7
        }.sortedBy { it.inst.dueAt }.take(6)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DashHeader(now) }
        item { ProgressCard(prog, onDetails) }

        if (overdue.isNotEmpty()) {
            item { SectionTitle("عقب‌افتاده", Neon.red, overdue.size) }
            items(overdue, key = { "o${it.inst.id}" }) { it2 ->
                PlanCard(it2, today, now, { onToggle(it2) }, { onOpen(it2.tpl) }, { onPostpone(it2) })
            }
        }

        item { SectionTitle("امروز", Neon.cyan, todays.size) }
        if (todays.isEmpty()) {
            item { EmptyState() }
        } else {
            items(todays, key = { "t${it.inst.id}" }) { it2 ->
                PlanCard(it2, today, now, { onToggle(it2) }, { onOpen(it2.tpl) }, { onPostpone(it2) })
            }
        }

        if (upcoming.isNotEmpty()) {
            item { SectionTitle("روزهای آینده", Neon.muted, upcoming.size) }
            items(upcoming, key = { "u${it.inst.id}" }) { it2 ->
                PlanCard(it2, today, now, { onToggle(it2) }, { onOpen(it2.tpl) }, null)
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
fun DashHeader(now: Long) {
    val z = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val greeting = when (z.hour) {
        in 5..11 -> "صبح بخیر"
        in 12..16 -> "ظهر بخیر"
        in 17..20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text2(
                greeting,
                Brush.horizontalGradient(listOf(Neon.cyan, Neon.magenta))
            )
            Spacer(Modifier.height(2.dp))
            T(
                "${Jalali.weekdayName(z.dayOfWeek)} ${dateText(Jalali.fromLocalDate(z.toLocalDate()))}",
                size = 13.sp, color = Neon.muted
            )
            T("ساعت " + timeText(z.hour, z.minute), size = 13.sp, color = Neon.muted)
            val ver = appVersionText()
            if (ver.isNotEmpty()) T(ver, size = 11.sp, color = Neon.muted)
        }
        Box(
            Modifier
                .size(96.dp)
                .background(
                    Brush.radialGradient(listOf(Neon.cyan.copy(alpha = 0.28f), Color.Transparent)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.hero_clipboard), contentDescription = null, modifier = Modifier.size(78.dp))
        }
    }
}

/** عنوان گرادیانی */
@Composable
private fun Text2(text: String, brush: Brush) {
    androidx.compose.material3.Text(
        text,
        style = MaterialTheme.typography.headlineMedium.copy(brush = brush, fontWeight = FontWeight.Bold)
    )
}

@Composable
fun ProgressCard(p: DayProgress, onDetails: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .neonGlow(Neon.cyan.copy(alpha = 0.6f), 24.dp, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Neon.cyan.copy(alpha = 0.8f), Neon.magenta.copy(alpha = 0.3f))),
                shape
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(fa(p.percent) + "٪", size = 34.sp, color = Neon.cyan, bold = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(
                    if (p.total == 0) "برای امروز کاری ثبت نشده" else "${fa(p.done)} از ${fa(p.total)} کار انجام شد",
                    size = 14.sp, bold = true
                )
                if (p.overdue > 0) T("${fa(p.overdue)} کار عقب‌افتاده", size = 12.sp, color = Neon.red)
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { p.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = Neon.cyan,
            trackColor = Neon.cyan.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill(fa(p.total), "کل", Neon.cyan)
            StatPill(fa(p.done), "انجام‌شده", Neon.lime)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatPill(fa(p.remaining), "باقی‌مانده", Neon.amber)
            StatPill(fa(p.overdue), "عقب‌افتاده", Neon.red)
        }
        Spacer(Modifier.height(10.dp))
        T(
            "جزئیات محاسبه",
            size = 12.sp, color = Neon.muted,
            decoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onDetails)
        )
    }
}

@Composable
fun SectionTitle(text: String, color: Color, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        T(text, size = 16.sp, bold = true)
        Spacer(Modifier.width(8.dp))
        T(fa(count), size = 13.sp, color = Neon.muted)
    }
}

@Composable
fun PlanCard(
    item: PlanItem,
    today: Long,
    now: Long,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onPostpone: (() -> Unit)?
) {
    val t = item.tpl
    val i = item.inst
    val done = i.status == Status.DONE
    val overdue = isOverdue(item, today, now)
    val color = when {
        done -> Neon.muted
        overdue -> Neon.red
        else -> kindColor(t.kind)
    }
    val shape = RoundedCornerShape(20.dp)
    val whenText = buildString {
        append(if (i.dueDate == today) "امروز" else dateText(Jalali.fromEpochDay(i.dueDate)))
        if (i.timeMin >= 0) append("، " + timeOf(i.timeMin))
        if (t.amount > 0) append(" · " + moneyText(t.amount))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .neonGlow(color.copy(alpha = if (done) 0.35f else 0.9f), 20.dp, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.15f))), shape)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (done) Icons.Rounded.TaskAlt else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (done) "برگرداندن" else "انجام شد",
                tint = if (done) Neon.lime else Neon.muted,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            T(
                t.title, size = 15.sp, bold = true,
                color = if (done) Neon.muted else Neon.text,
                decoration = if (done) TextDecoration.LineThrough else null
            )
            T(
                (if (overdue) "عقب‌افتاده · " else "") + whenText,
                size = 12.sp, color = if (overdue) Neon.red else Neon.muted
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(kindTitle(t.kind) + " · " + categoryName(t.category), size = 11.sp, color = color)
                if (t.priority > 0 && !done) {
                    Spacer(Modifier.width(8.dp))
                    T(
                        if (t.priority >= 2) "فوری" else "مهم",
                        size = 11.sp, bold = true,
                        color = if (t.priority >= 2) Neon.red else Neon.amber
                    )
                }
            }
        }
        if (onPostpone != null && t.kind == Kind.DAY && i.status == Status.PENDING) {
            NeonChip("فردا", false, Neon.cyan, Modifier.width(56.dp), onPostpone)
        }
    }
}

// ---------- فهرست الگوها ----------

@Composable
fun TasksTab(
    templates: List<TemplateEntity>,
    onOpen: (TemplateEntity) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
    onPause: (TemplateEntity) -> Unit
) {
    var filter by remember { mutableStateOf<String?>(null) }
    val shown = templates.filter { filter == null || it.kind == filter }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { T("همهٔ کارها", size = 24.sp, bold = true, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip("همه", filter == null, Neon.cyan, Modifier.weight(1f)) { filter = null }
                NeonChip("روزانه", filter == Kind.DAY, Neon.cyan, Modifier.weight(1f)) {
                    filter = if (filter == Kind.DAY) null else Kind.DAY
                }
                NeonChip("هفتگی", filter == Kind.WEEKLY, Neon.lime, Modifier.weight(1f)) {
                    filter = if (filter == Kind.WEEKLY) null else Kind.WEEKLY
                }
                NeonChip("سررسید", filter == Kind.DUE, Neon.amber, Modifier.weight(1f)) {
                    filter = if (filter == Kind.DUE) null else Kind.DUE
                }
            }
        }
        if (shown.isEmpty()) {
            item { EmptyState() }
        } else {
            items(shown, key = { it.id }) { t -> TemplateCard(t, onOpen, onDelete, onPause) }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
fun TemplateCard(
    t: TemplateEntity,
    onOpen: (TemplateEntity) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
    onPause: (TemplateEntity) -> Unit
) {
    val color = if (t.paused) Neon.muted else kindColor(t.kind)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .neonGlow(color.copy(alpha = if (t.paused) 0.3f else 0.9f), 20.dp, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.15f))), shape)
            .clickable { onOpen(t) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonIcon(kindIcon(t.kind), color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(t.title, size = 15.sp, bold = true, color = if (t.paused) Neon.muted else Neon.text)
            T(scheduleSummary(t), size = 12.sp, color = Neon.muted)
            T(
                kindTitle(t.kind) + " · " + categoryName(t.category) + if (t.paused) " · متوقف" else "",
                size = 11.sp, color = color
            )
        }
        val repeating = t.kind == Kind.WEEKLY || (t.kind == Kind.DUE && t.unit != Every.NONE)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (repeating) {
                NeonChip(if (t.paused) "ادامه" else "توقف", false, color, Modifier.width(60.dp)) { onPause(t) }
            }
            IconButton(onClick = { onDelete(t) }) {
                Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ---------- انتخاب نوع کار ----------

@Composable
fun TypeChooser(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(shape)
                .background(Neon.surface)
                .border(1.dp, Brush.linearGradient(listOf(Neon.cyan, Neon.magenta)), shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            T("چه کاری ثبت کنیم؟", size = 18.sp, bold = true, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
            ChooserRow(Kind.DAY, "یک روز مشخص، بدون نیاز به ساعت", onPick)
            ChooserRow(Kind.WEEKLY, "تکرار در روزهای هفته، مثل باشگاه و کلاس", onPick)
            ChooserRow(Kind.DUE, "اجاره، قسط و قبض با زنگ هشدار", onPick)
            GhostButton("انصراف", Modifier.fillMaxWidth(), onDismiss)
        }
    }
}

@Composable
private fun ChooserRow(kind: String, desc: String, onPick: (String) -> Unit) {
    val c = kindColor(kind)
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.copy(alpha = 0.10f))
            .border(1.dp, c.copy(alpha = 0.7f), shape)
            .clickable { onPick(kind) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonIcon(kindIcon(kind), c)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(kindTitle(kind), size = 16.sp, bold = true, color = c)
            T(desc, size = 12.sp, color = Neon.muted)
        }
    }
}

// ---------- ویرایشگر (فیلدها بر اساس نوع) ----------

@Composable
private fun ChipGrid(
    options: List<Pair<String, String>>,
    isSelected: (String) -> Boolean,
    color: Color,
    perRow: Int,
    onClick: (String) -> Unit
) {
    options.chunked(perRow).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (k, label) ->
                NeonChip(label, isSelected(k), color, Modifier.weight(1f)) { onClick(k) }
            }
            for (x in 0 until (perRow - row.size)) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun PlanEditor(kind: String, initial: TemplateEntity?, onDismiss: () -> Unit, onSave: (TemplateEntity) -> Unit) {
    val todayJ = remember { Jalali.fromLocalDate(LocalDate.now()) }
    val color = kindColor(kind)

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "personal") }
    var priority by remember { mutableStateOf(initial?.priority ?: 0) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var jDate by remember { mutableStateOf(if (initial != null) Jalali.fromEpochDay(initial.startDate) else todayJ) }
    var hasEnd by remember { mutableStateOf((initial?.endDate ?: 0L) > 0L) }
    var jEnd by remember {
        mutableStateOf(if (initial != null && initial.endDate > 0L) Jalali.fromEpochDay(initial.endDate) else todayJ)
    }
    var hasTime by remember { mutableStateOf(kind == Kind.DUE || (initial?.timeMin ?: -1) >= 0) }
    var hour by remember { mutableStateOf(if ((initial?.timeMin ?: -1) >= 0) initial!!.timeMin / 60 else 9) }
    var minute by remember { mutableStateOf(if ((initial?.timeMin ?: -1) >= 0) initial!!.timeMin % 60 else 0) }
    var mask by remember { mutableStateOf(initial?.weekMask ?: 0) }
    var every by remember { mutableStateOf(initial?.unit ?: Every.NONE) }
    var interval by remember { mutableStateOf(initial?.repeatEvery ?: 1) }
    var amountText by remember { mutableStateOf(if ((initial?.amount ?: 0L) > 0L) initial!!.amount.toString() else "") }
    var sound by remember { mutableStateOf(initial?.alarmSound ?: true) }
    var vibrate by remember { mutableStateOf(initial?.alarmVibrate ?: true) }
    var notify by remember { mutableStateOf(initial?.alarmNotify ?: true) }
    var gap by remember { mutableStateOf(initial?.alertGapMin ?: 30) }
    var maxAlerts by remember { mutableStateOf(initial?.maxAlerts ?: 3) }
    var picker by remember { mutableStateOf(0) } // 1 تاریخ، 2 ساعت، 3 تاریخ پایان

    val gapPreset = gap == 0 || gap == 30 || gap == 60 || gap == 180
    val recurring = kind == Kind.WEEKLY || (kind == Kind.DUE && every != Every.NONE)
    val endActive = hasEnd && recurring
    val startG = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third)
    val endG = Jalali.toLocalDate(jEnd.first, jEnd.second, jEnd.third)
    val timeActive = kind == Kind.DUE || (kind == Kind.WEEKLY && hasTime)
    val canSave = title.isNotBlank() &&
        (kind != Kind.WEEKLY || mask != 0) &&
        (!endActive || !endG.isBefore(startG))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(
                    (if (initial == null) "ثبت " else "ویرایش ") + kindTitle(kind),
                    size = 20.sp, bold = true, color = color,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = Neon.muted) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { T("عنوان کار", size = 13.sp, color = Neon.muted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(color),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("دسته‌بندی")
                ChipGrid(CATEGORIES, { it == category }, color, 3) { category = it }

                SectionLabel("اولویت")
                ChipGrid(PRIORITIES.map { it.first.toString() to it.second }, { it == priority.toString() }, color, 3) {
                    priority = it.toInt()
                }

                SectionLabel(
                    when (kind) {
                        Kind.DAY -> "تاریخ"
                        Kind.WEEKLY -> "تاریخ شروع"
                        else -> "تاریخ سررسید"
                    }
                )
                NeonTile(Icons.Rounded.CalendarMonth, color, "برای تغییر لمس کن", fullDateText(jDate)) { picker = 1 }

                if (kind == Kind.WEEKLY) {
                    SectionLabel("روزهای هفته")
                    ChipGrid(WEEK.mapIndexed { i, n -> i.toString() to n }, { (mask and (1 shl it.toInt())) != 0 }, color, 4) {
                        mask = mask xor (1 shl it.toInt())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonChip("هر روز", mask == 127, color, Modifier.weight(1f)) { mask = 127 }
                        NeonChip("شنبه تا چهارشنبه", mask == 31, color, Modifier.weight(1f)) { mask = 31 }
                    }
                    SectionLabel("ساعت")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeOption("بدون ساعت", Icons.Rounded.WbSunny, !hasTime, color, Modifier.weight(1f)) { hasTime = false }
                        ModeOption("با ساعت", Icons.Rounded.Schedule, hasTime, color, Modifier.weight(1f)) { hasTime = true }
                    }
                }

                if (timeActive) {
                    if (kind == Kind.DUE) SectionLabel("ساعت سررسید")
                    NeonTile(Icons.Rounded.Schedule, color, "برای تغییر لمس کن", timeText(hour, minute)) { picker = 2 }
                }

                if (kind == Kind.DUE) {
                    SectionLabel("تکرار")
                    ChipGrid(
                        listOf(
                            Every.NONE to "یک‌بار", Every.DAY to "روزانه", Every.WEEK to "هفتگی",
                            Every.MONTH to "ماهانه", Every.YEAR to "سالانه"
                        ),
                        { it == every }, color, 3
                    ) { every = it }
                    if (every != Every.NONE) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                        ) {
                            StepButton(Icons.Rounded.Add, color) { interval = (interval + 1).coerceAtMost(99) }
                            T(
                                everyLabel(
                                    TemplateEntity(
                                        title = "", kind = kind, category = "", priority = 0, note = "", startDate = 0,
                                        endDate = 0, timeMin = 0, weekMask = 0, unit = every, repeatEvery = interval,
                                        monthDay = 0, amount = 0, paused = false, alarmSound = false, alarmVibrate = false,
                                        alarmNotify = false, alertGapMin = 0, maxAlerts = 1, createdAt = 0
                                    )
                                ),
                                size = 16.sp, bold = true, color = color
                            )
                            StepButton(Icons.Rounded.Remove, color) { interval = (interval - 1).coerceAtLeast(1) }
                        }
                    }
                }

                if (recurring) {
                    SectionLabel("پایان تکرار")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeOption("بدون پایان", Icons.Rounded.Remove, !hasEnd, color, Modifier.weight(1f)) { hasEnd = false }
                        ModeOption("تاریخ پایان", Icons.Rounded.Event, hasEnd, color, Modifier.weight(1f)) { hasEnd = true }
                    }
                    if (hasEnd) {
                        NeonTile(
                            Icons.Rounded.Event, color, "تاریخ پایان", fullDateText(jEnd),
                            hint = if (!endG.isBefore(startG)) null else "تاریخ پایان باید بعد از شروع باشد"
                        ) { picker = 3 }
                    }
                }

                if (kind == Kind.DUE) {
                    SectionLabel("مبلغ (اختیاری)")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { s -> amountText = latinDigits(s).filter { it.isDigit() }.take(12) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { T("بدون مبلغ", size = 13.sp, color = Neon.muted) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors(color),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (kind == Kind.DUE || (kind == Kind.WEEKLY && hasTime)) {
                    SectionLabel("هشدار")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonChip("اعلان", notify, color, Modifier.weight(1f)) { notify = !notify }
                        NeonChip("زنگ صوتی", sound, color, Modifier.weight(1f)) { sound = !sound }
                        NeonChip("لرزش", vibrate, color, Modifier.weight(1f)) { vibrate = !vibrate }
                    }
                }

                if (kind == Kind.DUE && notify) {
                    SectionLabel("تکرار هشدار اگر تأیید نشد")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NeonChip("۳۰ دقیقه", gap == 30, color, Modifier.weight(1f)) { gap = 30 }
                        NeonChip("۱ ساعت", gap == 60, color, Modifier.weight(1f)) { gap = 60 }
                        NeonChip("۳ ساعت", gap == 180, color, Modifier.weight(1f)) { gap = 180 }
                        NeonChip("دلخواه", !gapPreset, color, Modifier.weight(1f)) { if (gapPreset) gap = 45 }
                        NeonChip("بدون", gap == 0, color, Modifier.weight(1f)) { gap = 0 }
                    }
                    if (!gapPreset) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                        ) {
                            StepButton(Icons.Rounded.Add, color) { gap = (gap + 5).coerceAtMost(720) }
                            T("هر ${fa(gap)} دقیقه", size = 16.sp, bold = true, color = color)
                            StepButton(Icons.Rounded.Remove, color) { gap = (gap - 5).coerceAtLeast(5) }
                        }
                    }
                    if (gap > 0) {
                        SectionLabel("حداکثر تعداد هشدار")
                        ChipGrid(listOf("1" to "۱ بار", "2" to "۲ بار", "3" to "۳ بار"), { it == maxAlerts.toString() }, color, 3) {
                            maxAlerts = it.toInt()
                        }
                    }
                }

                SectionLabel("یادداشت (اختیاری)")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(color),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            GradientButton("ذخیره", Modifier.fillMaxWidth().padding(16.dp), enabled = canSave) {
                val amount = latinDigits(amountText).filter { it.isDigit() }.take(12).toLongOrNull() ?: 0L
                onSave(
                    TemplateEntity(
                        id = initial?.id ?: 0L,
                        title = title.trim(),
                        kind = kind,
                        category = category,
                        priority = priority,
                        note = note.trim(),
                        startDate = startG.toEpochDay(),
                        endDate = if (endActive) endG.toEpochDay() else 0L,
                        timeMin = if (timeActive) hour * 60 + minute else -1,
                        weekMask = if (kind == Kind.WEEKLY) mask else 0,
                        unit = if (kind == Kind.DUE) every else Every.NONE,
                        repeatEvery = if (kind == Kind.DUE && every != Every.NONE) interval else 1,
                        monthDay = jDate.third,
                        amount = if (kind == Kind.DUE) amount else 0L,
                        paused = initial?.paused ?: false,
                        alarmSound = timeActive && sound,
                        alarmVibrate = timeActive && vibrate,
                        alarmNotify = timeActive && notify,
                        alertGapMin = if (kind == Kind.DUE) gap else 0,
                        maxAlerts = if (kind == Kind.DUE && gap > 0) maxAlerts else 1,
                        createdAt = initial?.createdAt ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }

    if (picker == 1) {
        JalaliDateDialog(initial = jDate, todayYear = todayJ.first, onDismiss = { picker = 0 }) {
            jDate = it
            picker = 0
        }
    }
    if (picker == 3) {
        JalaliDateDialog(initial = jEnd, todayYear = todayJ.first, onDismiss = { picker = 0 }) {
            jEnd = it
            picker = 0
        }
    }
    if (picker == 2) {
        TimeDialog(h = hour, m = minute, onDismiss = { picker = 0 }) { h, m ->
            hour = h
            minute = m
            picker = 0
        }
    }
}

@Composable
private fun fieldColors(color: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = color,
    unfocusedBorderColor = Neon.muted.copy(alpha = 0.4f),
    cursorColor = color,
    focusedTextColor = Neon.text,
    unfocusedTextColor = Neon.text
)

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.taskreminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat as RepeatIcon
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------- کمکی‌های متن فارسی ----------

private const val EN = "0123456789"
private const val FA = "۰۱۲۳۴۵۶۷۸۹"

fun fa(s: String): String = s.map { c -> EN.indexOf(c).let { if (it >= 0) FA[it] else c } }.joinToString("")

fun fa(n: Int): String = fa(n.toString())

private fun toLatin(s: String): String = s.map { c -> FA.indexOf(c).let { if (it >= 0) EN[it] else c } }.joinToString("")

fun timeText(h: Int, m: Int): String = fa("%02d:%02d".format(h, m))

fun dateText(j: Triple<Int, Int, Int>): String =
    "${fa(j.third)} ${Jalali.monthNames[j.second - 1]} ${fa(j.first)}"

fun fullDateText(j: Triple<Int, Int, Int>): String {
    val wd = Jalali.weekdayName(Jalali.toLocalDate(j.first, j.second, j.third).dayOfWeek)
    return "$wd ${dateText(j)}"
}

fun dueLabel(ms: Long): String {
    val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val j = Jalali.fromGregorian(z.year, z.monthValue, z.dayOfMonth)
    return "${Jalali.weekdayName(z.dayOfWeek)} ${dateText(j)}، ${timeText(z.hour, z.minute)}"
}

fun Repeat.neonColor(): Color = when (this) {
    Repeat.ONCE -> Neon.cyan
    Repeat.DAILY -> Neon.lime
    Repeat.MONTHLY -> Neon.magenta
}

fun Repeat.neonIcon(): ImageVector = when (this) {
    Repeat.ONCE -> Icons.Rounded.Alarm
    Repeat.DAILY -> Icons.Rounded.WbSunny
    Repeat.MONTHLY -> Icons.Rounded.CalendarMonth
}

fun Repeat.title(): String = when (this) {
    Repeat.ONCE -> "یک‌بار"
    Repeat.DAILY -> "روزانه"
    Repeat.MONTHLY -> "ماهانه"
}

fun repeatLabel(t: Task): String = when (t.repeat) {
    Repeat.ONCE -> "یک‌بار"
    Repeat.DAILY -> "هر روز، ساعت ${timeText(t.hour, t.minute)}"
    Repeat.MONTHLY -> "هر ماه، روز ${fa(t.monthDay)}، ساعت ${timeText(t.hour, t.minute)}"
}

// ---------- مجوز نوتیفیکیشن ----------

@Composable
fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// ---------- صفحهٔ اصلی ----------

@Composable
fun TaskApp(
    tasks: List<Task>,
    onUpsert: (Task) -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    var editing by remember { mutableStateOf<Task?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    var filter by remember { mutableStateOf<Repeat?>(null) }

    val today = LocalDate.now()
    val todayCount = tasks.count {
        !it.done && Instant.ofEpochMilli(it.dueAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    val repeatingCount = tasks.count { it.repeat != Repeat.ONCE && !it.done }
    val shown = tasks
        .filter { filter == null || it.repeat == filter }
        .sortedWith(compareBy<Task>({ it.done }, { it.dueAt }))

    Scaffold(
        containerColor = Neon.bg,
        floatingActionButton = {
            GradientButton("کار جدید", icon = Icons.Rounded.Add) { editing = null; showEditor = true }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Header(total = tasks.size, today = todayCount, repeating = repeatingCount, date = today)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonChip("همه", filter == null, Neon.amber, Modifier.weight(1f)) { filter = null }
                    Repeat.values().forEach { r ->
                        NeonChip(r.title(), filter == r, r.neonColor(), Modifier.weight(1f)) {
                            filter = if (filter == r) null else r
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item { EmptyState() }
            } else {
                items(shown, key = { it.id }) { t ->
                    TaskCard(
                        t = t,
                        onEdit = { editing = t; showEditor = true },
                        onToggle = { onToggle(t) },
                        onDelete = { deleting = t }
                    )
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showEditor) {
        TaskEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { onUpsert(it); showEditor = false }
        )
    }

    deleting?.let { t ->
        NeonDialog(
            title = "حذف کار",
            confirmText = "حذف",
            onDismiss = { deleting = null },
            onConfirm = { onDelete(t); deleting = null }
        ) {
            T("«${t.title}» حذف شود؟", align = TextAlign.Center, color = Neon.muted)
        }
    }
}

@Composable
fun Header(total: Int, today: Int, repeating: Int, date: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "یادآور کارها",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.horizontalGradient(listOf(Neon.cyan, Neon.magenta)),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
            Spacer(Modifier.height(2.dp))
            T(
                "${Jalali.weekdayName(date.dayOfWeek)} ${dateText(Jalali.fromLocalDate(date))}",
                size = 13.sp, color = Neon.muted
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(fa(total), "کل", Neon.cyan)
                StatPill(fa(today), "امروز", Neon.amber)
                StatPill(fa(repeating), "تکراری", Neon.lime)
            }
        }
        Box(
            Modifier
                .size(132.dp)
                .background(
                    Brush.radialGradient(listOf(Neon.cyan.copy(alpha = 0.28f), Color.Transparent)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.hero_clipboard), contentDescription = null, modifier = Modifier.size(108.dp))
        }
    }
}

@Composable
fun StatPill(value: String, label: String, color: Color) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(value, size = 15.sp, color = color, bold = true)
        Spacer(Modifier.width(5.dp))
        T(label, size = 12.sp, color = Neon.muted)
    }
}

@Composable
fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.hero_clipboard), contentDescription = null, modifier = Modifier.size(150.dp))
        Spacer(Modifier.height(8.dp))
        T("هنوز کاری ثبت نشده", size = 17.sp, bold = true)
        T("با دکمهٔ «کار جدید» شروع کن", size = 13.sp, color = Neon.muted)
    }
}

@Composable
fun TaskCard(t: Task, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val base = t.repeat.neonColor()
    val color = if (t.done) Neon.muted else base
    val overdue = t.repeat == Repeat.ONCE && !t.done && System.currentTimeMillis() > t.dueAt
    val shape = RoundedCornerShape(22.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .neonGlow(color.copy(alpha = if (t.done) 0.4f else 1f), 22.dp, 7.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.15f))), shape)
            .clickable(onClick = onEdit)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonIcon(t.repeat.neonIcon(), color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(
                t.title, size = 16.sp, bold = true,
                color = if (t.done) Neon.muted else Neon.text,
                decoration = if (t.done) TextDecoration.LineThrough else null
            )
            T(repeatLabel(t), size = 12.sp, color = Neon.muted)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Schedule, null,
                    tint = if (overdue) Neon.red else color, modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                T(
                    when {
                        t.repeat != Repeat.ONCE && t.done -> "تکرارها به پایان رسید"
                        t.done -> "انجام شد"
                        overdue -> "عقب‌افتاده: " + dueLabel(t.dueAt)
                        t.repeat == Repeat.ONCE -> dueLabel(t.dueAt)
                        else -> "نوبت بعد: " + dueLabel(t.dueAt)
                    },
                    size = 12.sp, color = if (overdue) Neon.red else color
                )
            }
            if (t.repeat != Repeat.ONCE) {
                Spacer(Modifier.height(8.dp))
                RepeatProgress(t, color)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (t.repeat == Repeat.ONCE) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (t.done) Icons.Rounded.TaskAlt else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "انجام شد",
                        tint = if (t.done) Neon.lime else Neon.muted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun RepeatProgress(t: Task, color: Color) {
    if (t.maxCount == 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AllInclusive, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            T("تکرار نامحدود · ${fa(t.firedCount)} بار انجام شده", size = 11.sp, color = Neon.muted)
        }
    } else {
        val p = (t.firedCount.toFloat() / t.maxCount).coerceIn(0f, 1f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
            Spacer(Modifier.width(8.dp))
            T("${fa(t.firedCount)} از ${fa(t.maxCount)}", size = 11.sp, color = Neon.muted)
        }
    }
}

// ---------- ساخت / ویرایش کار ----------

private val PersianDigits = object : VisualTransformation {
    override fun filter(text: AnnotatedString) =
        TransformedText(AnnotatedString(fa(text.text)), OffsetMapping.Identity)
}

@Composable
fun SectionLabel(text: String) {
    T(text, size = 13.sp, color = Neon.muted, bold = true, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun NeonTile(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
    hint: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .neonGlow(color.copy(alpha = 0.7f), 18.dp, 5.dp)
            .clip(shape)
            .background(Neon.surface)
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonIcon(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(label, size = 12.sp, color = Neon.muted)
            T(value, size = 18.sp, bold = true)
            if (hint != null) T(hint, size = 11.sp, color = color)
        }
        Icon(Icons.Rounded.Edit, null, tint = Neon.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TypeOption(r: Repeat, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = r.neonColor()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.copy(alpha = 0.14f) else Neon.surface)
            .border(1.dp, if (selected) c else Neon.muted.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeonIcon(r.neonIcon(), if (selected) c else Neon.muted, size = 40.dp, iconSize = 22.dp)
        Spacer(Modifier.height(6.dp))
        T(r.title(), size = 13.sp, color = if (selected) c else Neon.muted, bold = selected)
    }
}

@Composable
fun ModeOption(text: String, icon: ImageVector, selected: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.14f) else Neon.surface)
            .border(1.dp, if (selected) color else Neon.muted.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) color else Neon.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        T(text, size = 14.sp, color = if (selected) color else Neon.muted, bold = selected)
    }
}

@Composable
fun StepButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color)
    }
}

@Composable
fun TaskEditor(initial: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val today = remember { Jalali.fromLocalDate(LocalDate.now()) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.ONCE) }
    var hour by remember { mutableStateOf(initial?.hour ?: 9) }
    var minute by remember { mutableStateOf(initial?.minute ?: 0) }
    var jDate by remember {
        mutableStateOf(if (initial != null) Jalali.fromEpochDay(initial.startDate) else today)
    }
    var unlimited by remember { mutableStateOf((initial?.maxCount ?: 0) == 0) }
    var countText by remember {
        mutableStateOf(if ((initial?.maxCount ?: 0) > 0) initial!!.maxCount.toString() else "10")
    }
    var picker by remember { mutableStateOf(0) } // 0 = هیچ، 1 = تاریخ، 2 = ساعت

    val color = repeat.neonColor()
    val count = (countText.toIntOrNull() ?: 1).coerceAtLeast(1)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(if (initial == null) "کار جدید" else "ویرایش کار", size = 20.sp, bold = true, modifier = Modifier.weight(1f).padding(start = 8.dp))
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        unfocusedBorderColor = Neon.muted.copy(alpha = 0.4f),
                        cursorColor = color,
                        focusedTextColor = Neon.text,
                        unfocusedTextColor = Neon.text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("نوع تکرار")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Repeat.values().forEach { r ->
                        TypeOption(r, repeat == r, Modifier.weight(1f)) { repeat = r }
                    }
                }

                SectionLabel(if (repeat == Repeat.ONCE) "تاریخ" else "تاریخ شروع")
                NeonTile(
                    icon = Icons.Rounded.CalendarMonth,
                    color = color,
                    label = "برای تغییر لمس کن",
                    value = fullDateText(jDate),
                    hint = if (repeat == Repeat.MONTHLY)
                        "هر ماه روز ${fa(jDate.third)} (در ماه‌های کوتاه‌تر، آخرین روز ماه)" else null
                ) { picker = 1 }

                SectionLabel("ساعت")
                NeonTile(
                    icon = Icons.Rounded.Schedule,
                    color = color,
                    label = "برای تغییر لمس کن",
                    value = timeText(hour, minute)
                ) { picker = 2 }

                if (repeat != Repeat.ONCE) {
                    SectionLabel("تعداد تکرار")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeOption("نامحدود", Icons.Rounded.AllInclusive, unlimited, color, Modifier.weight(1f)) { unlimited = true }
                        ModeOption("تعداد مشخص", Icons.Rounded.RepeatIcon, !unlimited, color, Modifier.weight(1f)) { unlimited = false }
                    }
                    if (!unlimited) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StepButton(Icons.Rounded.Add, color) { countText = (count + 1).coerceAtMost(9999).toString() }
                            OutlinedTextField(
                                value = countText,
                                onValueChange = { s -> countText = toLatin(s).filter { it.isDigit() }.take(4) },
                                singleLine = true,
                                visualTransformation = PersianDigits,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    color = Neon.text, textAlign = TextAlign.Center
                                ),
                                shape = RoundedCornerShape(16.dp),
                                suffix = { T("بار", size = 13.sp, color = Neon.muted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = color,
                                    unfocusedBorderColor = color.copy(alpha = 0.5f),
                                    cursorColor = color
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            StepButton(Icons.Rounded.Remove, color) { countText = (count - 1).coerceAtLeast(1).toString() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 30, 60, 100).forEach { n ->
                                NeonChip(fa(n), count == n, color, Modifier.weight(1f)) { countText = n.toString() }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            GradientButton(
                "ذخیره",
                Modifier.fillMaxWidth().padding(16.dp),
                enabled = title.isNotBlank()
            ) {
                val g = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third)
                val max = if (repeat == Repeat.ONCE || unlimited) 0 else count
                val fired = if (initial != null && initial.repeat == repeat) initial.firedCount else 0
                val base = Task(
                    id = initial?.id ?: (System.nanoTime() and 0x7fffffff).toInt(),
                    title = title.trim(),
                    repeat = repeat,
                    hour = hour,
                    minute = minute,
                    monthDay = jDate.third,
                    startDate = g.toEpochDay(),
                    dueAt = 0L,
                    done = max > 0 && repeat != Repeat.ONCE && fired >= max,
                    maxCount = max,
                    firedCount = fired
                )
                onSave(base.copy(dueAt = Recurrence.firstDue(base, System.currentTimeMillis())))
            }
        }
    }

    if (picker == 1) {
        JalaliDateDialog(initial = jDate, todayYear = today.first, onDismiss = { picker = 0 }) {
            jDate = it
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

// ---------- انتخاب تاریخ شمسی (چرخشی) ----------

@Composable
fun JalaliDateDialog(
    initial: Triple<Int, Int, Int>,
    todayYear: Int,
    onDismiss: () -> Unit,
    onPick: (Triple<Int, Int, Int>) -> Unit
) {
    val firstYear = minOf(todayYear - 1, initial.first)
    val lastYear = maxOf(todayYear + 15, initial.first)
    val years = (firstYear..lastYear).toList()

    var y by remember { mutableStateOf(initial.first) }
    var m by remember { mutableStateOf(initial.second) }
    var d by remember { mutableStateOf(initial.third) }
    val len = Jalali.monthLength(y, m)
    val day = minOf(d, len)

    NeonDialog(
        title = "انتخاب تاریخ",
        confirmText = "تأیید",
        onDismiss = onDismiss,
        onConfirm = { onPick(Triple(y, m, day)) }
    ) {
        T(fullDateText(Triple(y, m, day)), size = 15.sp, color = Neon.cyan, bold = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // در حالت راست‌به‌چپ: روز راست، ماه وسط، سال چپ
            key(len) {
                WheelPicker(
                    labels = (1..len).map { fa(it) },
                    selectedIndex = day - 1,
                    onSelected = { d = it + 1 },
                    modifier = Modifier.weight(0.8f)
                )
            }
            WheelPicker(
                labels = Jalali.monthNames,
                selectedIndex = m - 1,
                onSelected = { m = it + 1 },
                modifier = Modifier.weight(1.4f)
            )
            WheelPicker(
                labels = years.map { fa(it) },
                selectedIndex = years.indexOf(y).coerceAtLeast(0),
                onSelected = { y = years[it] },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------- انتخاب ساعت (چرخشی) ----------

@Composable
fun TimeDialog(h: Int, m: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    var hour by remember { mutableStateOf(h) }
    var minute by remember { mutableStateOf(m) }

    NeonDialog(
        title = "انتخاب ساعت",
        confirmText = "تأیید",
        onDismiss = onDismiss,
        onConfirm = { onPick(hour, minute) }
    ) {
        T(timeText(hour, minute), size = 26.sp, color = Neon.cyan, bold = true)
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelPicker(
                    labels = (0..23).map { fa("%02d".format(it)) },
                    selectedIndex = hour,
                    onSelected = { hour = it },
                    modifier = Modifier.weight(1f)
                )
                T(":", size = 26.sp, color = Neon.muted, bold = true)
                WheelPicker(
                    labels = (0..59).map { fa("%02d".format(it)) },
                    selectedIndex = minute,
                    onSelected = { minute = it },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

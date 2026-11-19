@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.taskreminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------- کمکی‌های متن فارسی ----------

private const val EN = "0123456789"
private const val FA = "۰۱۲۳۴۵۶۷۸۹"

fun fa(s: String): String = s.map { c -> EN.indexOf(c).let { if (it >= 0) FA[it] else c } }.joinToString("")

fun timeText(h: Int, m: Int): String = fa("%02d:%02d".format(h, m))

fun dateText(j: Triple<Int, Int, Int>): String =
    fa("${j.third} ${Jalali.monthNames[j.second - 1]} ${j.first}")

fun repeatLabel(t: Task): String = when (t.repeat) {
    Repeat.ONCE -> "یک‌بار"
    Repeat.DAILY -> "هر روز، ساعت ${timeText(t.hour, t.minute)}"
    Repeat.MONTHLY -> "هر ماه، روز ${fa(t.monthDay.toString())}، ساعت ${timeText(t.hour, t.minute)}"
}

fun dueLabel(ms: Long): String {
    val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val j = Jalali.fromGregorian(z.year, z.monthValue, z.dayOfMonth)
    return "${Jalali.weekdayName(z.dayOfWeek)} ${dateText(j)}، ساعت ${timeText(z.hour, z.minute)}"
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

// ---------- صفحه اصلی ----------

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("کارهای من") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "افزودن کار")
            }
        }
    ) { pad ->
        val sorted = tasks.sortedWith(compareBy<Task>({ it.done }, { it.dueAt }))
        if (sorted.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "هنوز کاری ثبت نشده.\nبا دکمه + شروع کن.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sorted, key = { it.id }) { t ->
                    TaskCard(
                        t = t,
                        onEdit = { editing = t; showEditor = true },
                        onToggle = { onToggle(t) },
                        onDelete = { deleting = t }
                    )
                }
            }
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
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف کار") },
            text = { Text("«${t.title}» حذف شود؟") },
            confirmButton = { TextButton(onClick = { onDelete(t); deleting = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }
        )
    }
}

@Composable
fun TaskCard(t: Task, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val overdue = !t.done && System.currentTimeMillis() > t.dueAt
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "انجام شد",
                    tint = if (t.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null
                )
                Text(
                    repeatLabel(t),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!t.done) {
                    Text(
                        (if (overdue) "عقب‌افتاده: " else "سررسید: ") + dueLabel(t.dueAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف")
            }
        }
    }
}

// ---------- ساخت / ویرایش کار ----------

@Composable
fun TaskEditor(initial: Task?, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    val today = remember { Jalali.fromLocalDate(LocalDate.now()) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.ONCE) }
    var hour by remember { mutableStateOf(initial?.hour ?: 9) }
    var minute by remember { mutableStateOf(initial?.minute ?: 0) }
    var jDate by remember {
        mutableStateOf(
            if (initial != null && initial.repeat == Repeat.ONCE) Jalali.fromEpochDay(initial.onceDate) else today
        )
    }
    var monthDay by remember { mutableStateOf(initial?.monthDay ?: today.third) }
    var picker by remember { mutableStateOf(0) } // 0 = هیچ، 1 = تاریخ، 2 = ساعت

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "کار جدید" else "ویرایش کار") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Repeat.ONCE to "یک‌بار", Repeat.DAILY to "روزانه", Repeat.MONTHLY to "ماهانه")
                        .forEach { (r, label) ->
                            FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(label) })
                        }
                }
                when (repeat) {
                    Repeat.ONCE -> OutlinedButton(
                        onClick = { picker = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تاریخ: ${dateText(jDate)}") }

                    Repeat.MONTHLY -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("روز ماه (شمسی):")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { if (monthDay > 1) monthDay-- }) { Text("−") }
                                Text(fa(monthDay.toString()), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { if (monthDay < 31) monthDay++ }) { Text("+") }
                            }
                        }
                        if (monthDay > 29) {
                            Text(
                                "در ماه‌هایی که این روز را ندارند، آخرین روز ماه در نظر گرفته می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Repeat.DAILY -> {}
                }
                OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("ساعت: ${timeText(hour, minute)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val g = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third)
                    val base = Task(
                        id = initial?.id ?: (System.nanoTime() and 0x7fffffff).toInt(),
                        title = title.trim(),
                        repeat = repeat,
                        hour = hour,
                        minute = minute,
                        monthDay = monthDay,
                        onceDate = g.toEpochDay(),
                        dueAt = 0L
                    )
                    onSave(base.copy(dueAt = Recurrence.firstDue(base, System.currentTimeMillis())))
                }
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )

    if (picker == 1) {
        JalaliDatePickerDialog(
            initial = jDate,
            onDismiss = { picker = 0 },
            onPick = { jDate = it; picker = 0 }
        )
    }
    if (picker == 2) {
        TimeDialog(
            h = hour,
            m = minute,
            onDismiss = { picker = 0 },
            onPick = { h, m -> hour = h; minute = m; picker = 0 }
        )
    }
}

// ---------- انتخاب تاریخ شمسی ----------

@Composable
fun JalaliDatePickerDialog(
    initial: Triple<Int, Int, Int>,
    onDismiss: () -> Unit,
    onPick: (Triple<Int, Int, Int>) -> Unit
) {
    var y by remember { mutableStateOf(initial.first) }
    var m by remember { mutableStateOf(initial.second) }
    var d by remember { mutableStateOf(initial.third) }
    val len = Jalali.monthLength(y, m)
    val day = minOf(d, len)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { y-- }) { Text("−") }
                    Text(fa(y.toString()), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { y++ }) { Text("+") }
                }
                Jalali.monthNames.chunked(3).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { c, name ->
                            val idx = r * 3 + c + 1
                            FilterChip(
                                selected = m == idx,
                                onClick = { m = idx },
                                label = { Text(name, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                (1..len).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { n ->
                            val sel = n == day
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { d = n },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    fa(n.toString()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Triple(y, m, day)) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ---------- انتخاب ساعت ----------

@Composable
fun TimeDialog(h: Int, m: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                TimePicker(state = state)
            }
        }
    )
}

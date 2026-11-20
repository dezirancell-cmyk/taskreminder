package com.example.taskreminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val HAB_WEEK = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
private val HAB_LETTERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
private val HAB_ICONS = listOf("fitness", "book", "walk", "water", "sleep", "heart", "star")

private fun habitIcon(key: String): ImageVector = when (key) {
    "fitness" -> Icons.Rounded.FitnessCenter
    "book" -> Icons.Rounded.MenuBook
    "walk" -> Icons.Rounded.DirectionsWalk
    "water" -> Icons.Rounded.LocalDrink
    "sleep" -> Icons.Rounded.Bedtime
    "heart" -> Icons.Rounded.Favorite
    else -> Icons.Rounded.Star
}

@Composable
private fun habitFieldColors(color: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = color,
    unfocusedBorderColor = Neon.muted.copy(alpha = 0.4f),
    cursorColor = color,
    focusedTextColor = Neon.text,
    unfocusedTextColor = Neon.text
)

@Composable
private fun NeonBar(fraction: Float, color: Color = Neon.cyan) {
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(shape).background(Neon.surface2)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(color, Neon.magenta)))
        )
    }
}

private fun daysSummary(h: HabitEntity): String {
    if (h.kind == HabitKind.WEEKLY) return "${fa(h.target)} بار در هفته"
    val mask = h.weekMask and HabitLogic.ALL_DAYS
    if (mask == HabitLogic.ALL_DAYS) return "هر روز"
    return (0..6).filter { mask and (1 shl it) != 0 }.joinToString("، ") { HAB_WEEK[it] }
}

// ---------- تب عادت‌ها و اهداف ----------

@Composable
fun HabitsTab(vm: TaskViewModel, today: Long) {
    val habits by vm.habits.collectAsState()
    val logs by vm.habitLogs.collectAsState()
    val goals by vm.goals.collectAsState()
    var seg by rememberSaveable { mutableStateOf(0) }
    var habitEditor by remember { mutableStateOf(false) }
    var habitEditing by remember { mutableStateOf<HabitEntity?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var goalEditor by remember { mutableStateOf(false) }
    var goalEditing by remember { mutableStateOf<GoalEntity?>(null) }

    val byHabit: Map<Long, Set<Long>> = remember(logs) {
        logs.groupBy({ it.habitId }, { it.day }).mapValues { e -> e.value.toSet() }
    }
    val week = remember(habits, byHabit, today) { HabitLogic.weekProgress(habits, byHabit, today) }
    val presets = HabitPresets.list.filter { p -> habits.none { it.title == p.title } }
    val sortedGoals = remember(goals) { goals.sortedWith(compareBy({ it.doneAt > 0L }, { -it.createdAt })) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { T("عادت‌ها و اهداف", size = 24.sp, bold = true, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip("عادت‌ها", seg == 0, Neon.cyan, Modifier.weight(1f)) { seg = 0 }
                NeonChip("اهداف", seg == 1, Neon.magenta, Modifier.weight(1f)) { seg = 1 }
            }
        }
        if (seg == 0) {
            if (habits.isNotEmpty()) {
                item {
                    val frac = if (week.second == 0) 0f else week.first.toFloat() / week.second
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Neon.surface)
                            .border(1.dp, Neon.cyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        T("گزارش این هفته", size = 14.sp, bold = true)
                        NeonBar(frac)
                        T(
                            "${fa(week.first)} از ${fa(week.second)} انجام شد (${fa((frac * 100).toInt())}٪)",
                            size = 12.sp, color = Neon.muted
                        )
                    }
                }
            }
            if (presets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("عادت‌های آماده (با یک لمس اضافه می‌شود)")
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { p ->
                                NeonChip("+ ${p.title}", false, Neon.lime, Modifier.width(112.dp)) {
                                    vm.saveHabit(
                                        HabitEntity(
                                            title = p.title, icon = p.icon, kind = p.kind,
                                            weekMask = HabitLogic.ALL_DAYS, target = p.target, timeMin = -1,
                                            createdAt = System.currentTimeMillis(), startDay = today
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                GradientButton("عادت اختصاصی", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) {
                    habitEditing = null
                    habitEditor = true
                }
            }
            if (habits.isEmpty()) {
                item { EmptyState("هنوز عادتی ثبت نشده", "یکی از عادت‌های آماده را لمس کن یا عادت اختصاصی بساز") }
            } else {
                items(habits, key = { it.id }) { h ->
                    HabitCard(
                        h = h, done = byHabit[h.id].orEmpty(), today = today,
                        onToggle = { vm.setHabitDay(h.id, today, today !in byHabit[h.id].orEmpty()) },
                        onOpen = { detailId = h.id }
                    )
                }
            }
        } else {
            item {
                GradientButton("هدف جدید", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) {
                    goalEditing = null
                    goalEditor = true
                }
            }
            if (sortedGoals.isEmpty()) {
                item { EmptyState("هنوز هدفی ثبت نشده", "برای هر هدف یک عدد مقصد بگذار و پیشرفتت را ثبت کن") }
            } else {
                items(sortedGoals, key = { it.id }) { g ->
                    GoalCard(
                        g = g, today = today,
                        onStep = { delta -> vm.saveGoal(g.copy(current = (g.current + delta).coerceAtLeast(0L))) },
                        onOpen = { goalEditing = g; goalEditor = true }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    if (habitEditor) {
        HabitEditor(
            initial = habitEditing, today = today,
            onDismiss = { habitEditor = false; habitEditing = null },
            onSave = { vm.saveHabit(it); habitEditor = false; habitEditing = null },
            onDelete = { id -> vm.deleteHabit(id); habitEditor = false; habitEditing = null; detailId = null }
        )
    }

    val detail = habits.firstOrNull { it.id == detailId }
    if (detail != null && !habitEditor) {
        HabitDetail(
            h = detail, done = byHabit[detail.id].orEmpty(), today = today,
            onToggleDay = { day, d -> vm.setHabitDay(detail.id, day, d) },
            onEdit = { habitEditing = detail; habitEditor = true },
            onClose = { detailId = null }
        )
    }

    if (goalEditor) {
        GoalEditor(
            initial = goalEditing, today = today,
            onDismiss = { goalEditor = false; goalEditing = null },
            onSave = { vm.saveGoal(it); goalEditor = false; goalEditing = null },
            onDelete = { id -> vm.deleteGoal(id); goalEditor = false; goalEditing = null }
        )
    }
}

// ---------- کارت عادت ----------

@Composable
private fun HabitCard(h: HabitEntity, done: Set<Long>, today: Long, onToggle: () -> Unit, onOpen: () -> Unit) {
    val streak = HabitLogic.streak(h, done, today)
    val doneToday = today in done
    val canToday = HabitLogic.scheduled(h, today)
    val weekly = h.kind == HabitKind.WEEKLY
    val color = if (doneToday) Neon.lime else Neon.cyan
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .neonGlow(color.copy(alpha = if (doneToday) 0.35f else 0.8f), 20.dp, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.15f))), shape)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonIcon(habitIcon(h.icon), color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(h.title, size = 15.sp, bold = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocalFireDepartment, null,
                    tint = if (streak > 0) Neon.amber else Neon.muted, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                T(
                    if (weekly) "رشته: ${fa(streak)} هفته" else "رشته: ${fa(streak)} روز",
                    size = 12.sp, color = if (streak > 0) Neon.amber else Neon.muted
                )
            }
            val cnt = HabitLogic.countInWeek(h, done, HabitLogic.weekStart(today))
            T(
                daysSummary(h) +
                    (if (weekly) " · این هفته ${fa(cnt)} از ${fa(h.target)}" else "") +
                    (if (h.timeMin >= 0) " · ${timeText(h.timeMin / 60, h.timeMin % 60)}" else ""),
                size = 11.sp, color = Neon.muted
            )
        }
        if (canToday) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (doneToday) Icons.Rounded.TaskAlt else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (doneToday) "برداشتن" else "انجام شد",
                    tint = if (doneToday) Neon.lime else Neon.muted,
                    modifier = Modifier.size(34.dp)
                )
            }
        } else {
            T("امروز نیست", size = 11.sp, color = Neon.muted)
        }
    }
}

// ---------- کارت هدف ----------

@Composable
private fun GoalCard(g: GoalEntity, today: Long, onStep: (Long) -> Unit, onOpen: () -> Unit) {
    val p = HabitLogic.goalProgress(g, today)
    val color = when {
        p.done -> Neon.lime
        p.daysLeft != null && p.daysLeft < 0 -> Neon.red
        else -> Neon.magenta
    }
    val shape = RoundedCornerShape(20.dp)
    val unit = if (g.unit.isBlank()) "" else " ${g.unit}"
    Column(
        Modifier
            .fillMaxWidth()
            .neonGlow(color.copy(alpha = if (p.done) 0.3f else 0.8f), 20.dp, 6.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.15f))), shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(
                if (g.kind == GoalKind.LONG) Icons.Rounded.EmojiEvents else Icons.Rounded.Flag,
                color, size = 40.dp, iconSize = 22.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(g.title, size = 15.sp, bold = true)
                T(if (g.kind == GoalKind.LONG) "بلندمدت" else "کوتاه‌مدت", size = 11.sp, color = Neon.muted)
            }
            if (!p.done) {
                if (g.current > 0) {
                    IconButton(onClick = { onStep(-g.step.coerceAtLeast(1L)) }) {
                        Icon(Icons.Rounded.Remove, "کم کردن", tint = Neon.muted)
                    }
                }
                IconButton(onClick = { onStep(g.step.coerceAtLeast(1L)) }) {
                    Icon(Icons.Rounded.Add, "افزودن پیشرفت", tint = color, modifier = Modifier.size(30.dp))
                }
            }
        }
        NeonBar(p.percent / 100f, color)
        Row(Modifier.fillMaxWidth()) {
            T("${fa(g.current.toString())} از ${fa(g.target.toString())}$unit (${fa(p.percent)}٪)", size = 12.sp, modifier = Modifier.weight(1f))
            val note = when {
                p.done -> "تکمیل شد"
                p.daysLeft == null -> ""
                p.daysLeft < 0 -> "مهلت گذشته"
                p.daysLeft == 0L -> "امروز آخرین روز است"
                else -> "${fa(p.daysLeft.toString())} روز مانده"
            }
            if (note.isNotEmpty()) T(note, size = 12.sp, color = color, bold = true)
        }
    }
}

// ---------- قالب فرم تمام‌صفحه ----------

@Composable
private fun FullForm(
    title: String,
    color: Color,
    saveEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(title, size = 20.sp, bold = true, color = color, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = Neon.muted) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
                Spacer(Modifier.height(8.dp))
            }
            GradientButton("ذخیره", Modifier.fillMaxWidth().padding(16.dp), enabled = saveEnabled, onClick = onSave)
        }
    }
}

@Composable
private fun DeleteLink(label: String, onConfirm: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    T(
        label, color = Neon.red, bold = true, size = 14.sp, align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable { ask = true }.padding(12.dp)
    )
    if (ask) {
        NeonDialog(
            title = label, confirmText = "حذف",
            onDismiss = { ask = false },
            onConfirm = { ask = false; onConfirm() }
        ) {
            T("این کار قابل بازگشت نیست.", color = Neon.muted, align = TextAlign.Center)
        }
    }
}

@Composable
private fun NumField(label: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value > 0) value.toString() else "") }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            val clean = s.map { c -> "۰۱۲۳۴۵۶۷۸۹".indexOf(c).let { if (it >= 0) "0123456789"[it] else c } }
                .joinToString("").filter { it.isDigit() }.take(9)
            text = clean
            onChange(clean.toLongOrNull() ?: 0L)
        },
        singleLine = true,
        label = { T(label, size = 12.sp, color = Neon.muted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
        shape = RoundedCornerShape(16.dp),
        colors = habitFieldColors(Neon.magenta),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- ویرایشگر عادت ----------

@Composable
private fun HabitEditor(
    initial: HabitEntity?,
    today: Long,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    val color = Neon.cyan
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "star") }
    var kind by remember { mutableStateOf(initial?.kind ?: HabitKind.DAILY) }
    var mask by remember { mutableStateOf(if (initial != null && initial.kind == HabitKind.DAILY) initial.weekMask else HabitLogic.ALL_DAYS) }
    var target by remember { mutableStateOf(initial?.target?.coerceAtLeast(1) ?: 3) }
    var reminder by remember { mutableStateOf((initial?.timeMin ?: -1) >= 0) }
    var timeMin by remember { mutableStateOf(if ((initial?.timeMin ?: -1) >= 0) initial!!.timeMin else 8 * 60) }
    var pickTime by remember { mutableStateOf(false) }

    FullForm(
        title = if (initial == null) "عادت جدید" else "ویرایش عادت",
        color = color,
        saveEnabled = title.isNotBlank() && (kind == HabitKind.WEEKLY || (mask and HabitLogic.ALL_DAYS) != 0),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                HabitEntity(
                    id = initial?.id ?: 0L,
                    title = title.trim(),
                    icon = icon,
                    kind = kind,
                    weekMask = if (kind == HabitKind.DAILY) mask and HabitLogic.ALL_DAYS else HabitLogic.ALL_DAYS,
                    target = if (kind == HabitKind.WEEKLY) target.coerceIn(1, 7) else 1,
                    timeMin = if (reminder) timeMin else -1,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                    startDay = initial?.startDay ?: today
                )
            )
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { T("نام عادت", size = 13.sp, color = Neon.muted) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
            shape = RoundedCornerShape(16.dp),
            colors = habitFieldColors(color),
            modifier = Modifier.fillMaxWidth()
        )
        SectionLabel("آیکون")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HAB_ICONS.forEach { k ->
                Box(Modifier.clickable { icon = k }) {
                    NeonIcon(habitIcon(k), if (icon == k) Neon.cyan else Neon.muted)
                }
            }
        }
        SectionLabel("نوع")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeOption("روزانه", Icons.Rounded.CalendarMonth, kind == HabitKind.DAILY, color, Modifier.weight(1f)) { kind = HabitKind.DAILY }
            ModeOption("هفتگی", Icons.Rounded.Schedule, kind == HabitKind.WEEKLY, color, Modifier.weight(1f)) { kind = HabitKind.WEEKLY }
        }
        if (kind == HabitKind.DAILY) {
            SectionLabel("روزهای هفته")
            ChipGrid(
                (0..6).map { it.toString() to HAB_WEEK[it] },
                { (mask and (1 shl it.toInt())) != 0 }, color, 4
            ) { mask = mask xor (1 shl it.toInt()) }
        } else {
            SectionLabel("چند بار در هفته؟")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
            ) {
                StepButton(Icons.Rounded.Add, color) { target = (target + 1).coerceAtMost(7) }
                T(fa(target), size = 22.sp, bold = true, color = color)
                StepButton(Icons.Rounded.Remove, color) { target = (target - 1).coerceAtLeast(1) }
            }
        }
        SectionLabel("یادآور")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeOption("بدون یادآور", Icons.Rounded.NotificationsOff, !reminder, color, Modifier.weight(1f)) { reminder = false }
            ModeOption("یادآور", Icons.Rounded.Notifications, reminder, color, Modifier.weight(1f)) { reminder = true }
        }
        if (reminder) {
            NeonTile(Icons.Rounded.Schedule, color, "ساعت یادآور", timeText(timeMin / 60, timeMin % 60)) { pickTime = true }
        }
        if (initial != null) DeleteLink("حذف این عادت") { onDelete(initial.id) }
    }

    if (pickTime) {
        TimeDialog(timeMin / 60, timeMin % 60, onDismiss = { pickTime = false }) { h, m ->
            timeMin = h * 60 + m
            pickTime = false
        }
    }
}

// ---------- جزئیات عادت: رشته، تقویم موفقیت، نمودار استمرار ----------

@Composable
private fun HabitDetail(
    h: HabitEntity,
    done: Set<Long>,
    today: Long,
    onToggleDay: (Long, Boolean) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit
) {
    val tj = Jalali.fromEpochDay(today)
    var monthIdx by remember { mutableStateOf(tj.first * 12 + tj.second - 1) }
    val jy = monthIdx / 12
    val jm = monthIdx % 12 + 1
    val streak = HabitLogic.streak(h, done, today)
    val best = HabitLogic.bestStreak(h, done, today)
    val rate = HabitLogic.rate(h, done, today)
    val unitName = if (h.kind == HabitKind.WEEKLY) "هفته" else "روز"
    val fractions = HabitLogic.weeklyFractions(h, done, today, 8)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonIcon(habitIcon(h.icon), Neon.cyan, size = 40.dp, iconSize = 22.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    T(h.title, size = 20.sp, bold = true)
                    T(daysSummary(h), size = 12.sp, color = Neon.muted)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "ویرایش", tint = Neon.amber) }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "بستن", tint = Neon.muted) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(fa(streak), "رشتهٔ فعلی ($unitName)", Neon.amber)
                    StatPill(fa(best), "بهترین رشته", Neon.lime)
                    StatPill(if (rate == null) "—" else fa(rate) + "٪", "نرخ موفقیت", Neon.cyan)
                }
                T(
                    if (h.kind == HabitKind.WEEKLY) "نرخ: درصد هفته‌های رسیده به هدف در ۴ هفتهٔ اخیر"
                    else "نرخ: روزهای انجام‌شده ÷ روزهای برنامه‌دار در ۳۰ روز اخیر",
                    size = 11.sp, color = Neon.muted
                )

                // تقویم موفقیت
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthIdx-- }) {
                        Icon(Icons.Rounded.ChevronRight, "ماه قبل", tint = Neon.cyan, modifier = Modifier.size(30.dp))
                    }
                    T(
                        "${Jalali.monthNames[jm - 1]} ${fa(jy)}", size = 16.sp, bold = true,
                        align = TextAlign.Center, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { monthIdx++ }) {
                        Icon(Icons.Rounded.ChevronLeft, "ماه بعد", tint = Neon.cyan, modifier = Modifier.size(30.dp))
                    }
                }
                HabitMonth(h, done, today, jy, jm, onToggleDay)
                T("برای ثبت یا برداشتن انجامِ روزهای گذشته، همان روز را لمس کن.", size = 11.sp, color = Neon.muted)

                // نمودار استمرار
                SectionLabel("نمودار استمرار (۸ هفتهٔ اخیر، جدیدترین سمت چپ)")
                Row(
                    Modifier.fillMaxWidth().height(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    fractions.forEach { f ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(maxOf(f, 0.04f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (f >= 1f) Neon.lime else Neon.cyan.copy(alpha = 0.75f))
                        )
                    }
                }
                T("ارتفاع هر ستون = درصد رسیدن به هدف همان هفته", size = 11.sp, color = Neon.muted)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HabitMonth(
    h: HabitEntity,
    done: Set<Long>,
    today: Long,
    jy: Int,
    jm: Int,
    onToggleDay: (Long, Boolean) -> Unit
) {
    val first = Jalali.toLocalDate(jy, jm, 1).toEpochDay()
    val len = Jalali.monthLength(jy, jm)
    val lead = HabitLogic.dayBit(first)
    val rows = (lead + len + 6) / 7
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            HAB_LETTERS.forEachIndexed { i, l ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    T(l, size = 12.sp, color = if (i == 6) Neon.red else Neon.muted, bold = true)
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val day = first - lead + r * 7 + c
                    val inMonth = day >= first && day < first + len
                    if (!inMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val isDone = day in done
                        val sched = HabitLogic.scheduled(h, day)
                        val missed = !isDone && sched && day < today && h.kind == HabitKind.DAILY && day >= h.startDay
                        val bg = when {
                            isDone -> Neon.lime.copy(alpha = 0.85f)
                            missed -> Neon.red.copy(alpha = 0.18f)
                            else -> Neon.surface
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(bg)
                                .then(if (day == today) Modifier.border(1.5.dp, Neon.cyan, CircleShape) else Modifier)
                                .then(if (day <= today) Modifier.clickable { onToggleDay(day, !isDone) } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            T(
                                fa(Jalali.fromEpochDay(day).third), size = 13.sp, bold = isDone,
                                color = when {
                                    isDone -> Neon.bg
                                    !sched || day > today -> Neon.muted.copy(alpha = 0.6f)
                                    else -> Neon.text
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- ویرایشگر هدف ----------

@Composable
private fun GoalEditor(
    initial: GoalEntity?,
    today: Long,
    onDismiss: () -> Unit,
    onSave: (GoalEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    val color = Neon.magenta
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: GoalKind.SHORT) }
    var target by remember { mutableStateOf(initial?.target ?: 0L) }
    var current by remember { mutableStateOf(initial?.current ?: 0L) }
    var step by remember { mutableStateOf(initial?.step ?: 1L) }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var hasDeadline by remember { mutableStateOf((initial?.deadline ?: 0L) > 0L) }
    var jDeadline by remember {
        mutableStateOf(Jalali.fromEpochDay(if ((initial?.deadline ?: 0L) > 0L) initial!!.deadline else today + 30))
    }
    var picker by remember { mutableStateOf(false) }

    FullForm(
        title = if (initial == null) "هدف جدید" else "ویرایش هدف",
        color = color,
        saveEnabled = title.isNotBlank() && target > 0L,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                GoalEntity(
                    id = initial?.id ?: 0L,
                    title = title.trim(),
                    kind = kind,
                    target = target,
                    current = current,
                    step = step.coerceAtLeast(1L),
                    unit = unit.trim(),
                    deadline = if (hasDeadline) Jalali.toLocalDate(jDeadline.first, jDeadline.second, jDeadline.third).toEpochDay() else 0L,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                    doneAt = initial?.doneAt ?: 0L
                )
            )
        }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { T("عنوان هدف", size = 13.sp, color = Neon.muted) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
            shape = RoundedCornerShape(16.dp),
            colors = habitFieldColors(color),
            modifier = Modifier.fillMaxWidth()
        )
        SectionLabel("نوع")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeOption("کوتاه‌مدت", Icons.Rounded.Flag, kind == GoalKind.SHORT, color, Modifier.weight(1f)) { kind = GoalKind.SHORT }
            ModeOption("بلندمدت", Icons.Rounded.EmojiEvents, kind == GoalKind.LONG, color, Modifier.weight(1f)) { kind = GoalKind.LONG }
        }
        NumField("مقدار هدف (عدد)", target) { target = it }
        OutlinedTextField(
            value = unit,
            onValueChange = { unit = it.take(16) },
            label = { T("واحد، مثلاً کتاب یا کیلومتر (اختیاری)", size = 12.sp, color = Neon.muted) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
            shape = RoundedCornerShape(16.dp),
            colors = habitFieldColors(color),
            modifier = Modifier.fillMaxWidth()
        )
        NumField("پیشرفت فعلی", current) { current = it }
        NumField("هر بار با دکمهٔ + چقدر اضافه شود؟", step) { step = it }
        SectionLabel("مهلت")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeOption("بدون مهلت", Icons.Rounded.RadioButtonUnchecked, !hasDeadline, color, Modifier.weight(1f)) { hasDeadline = false }
            ModeOption("مهلت دارد", Icons.Rounded.CalendarMonth, hasDeadline, color, Modifier.weight(1f)) { hasDeadline = true }
        }
        if (hasDeadline) {
            NeonTile(Icons.Rounded.CalendarMonth, color, "برای تغییر لمس کن", fullDateText(jDeadline)) { picker = true }
        }
        if (initial != null) DeleteLink("حذف این هدف") { onDelete(initial.id) }
    }

    if (picker) {
        JalaliDateDialog(
            initial = jDeadline, todayYear = Jalali.fromEpochDay(today).first,
            onDismiss = { picker = false }
        ) {
            jDeadline = it
            picker = false
        }
    }
}

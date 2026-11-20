package com.example.taskreminder

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val DAY_LETTERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

@Composable
fun CalendarTab(
    items: List<PlanItem>,
    templates: List<TemplateEntity>,
    shopping: List<ShoppingEntity>,
    today: Long,
    now: Long,
    onToggle: (PlanItem) -> Unit,
    onOpen: (TemplateEntity) -> Unit,
    onPostpone: (PlanItem) -> Unit,
    onAddForDay: (LocalDate) -> Unit,
    onOpenShopping: () -> Unit
) {
    val todayD = LocalDate.ofEpochDay(today)
    var selected by remember { mutableStateOf(today) }
    var weekMode by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf<String?>(null) }

    val sel = LocalDate.ofEpochDay(selected)
    val jSel = Jalali.fromLocalDate(sel)
    val monthStart = Jalali.toLocalDate(jSel.first, jSel.second, 1)
    val monthEnd = Jalali.toLocalDate(jSel.first, jSel.second, Jalali.monthLength(jSel.first, jSel.second))
    val weekStart = sel.minusDays(Engine.dayIndex(sel).toLong())
    val weekEnd = weekStart.plusDays(6)
    val from = if (weekStart.isBefore(monthStart)) weekStart else monthStart
    val to = if (weekEnd.isAfter(monthEnd)) weekEnd else monthEnd

    val allEntries = remember(items, templates, from, to, today) {
        Engine.calendarEntries(items, templates, from, to, todayD)
    }
    val entries = remember(allEntries, cat) {
        val c = cat
        if (c == null) allEntries else allEntries.mapValues { e -> e.value.filter { it.tpl.category == c } }
    }
    val shopDays = remember(shopping) { shopping.groupBy { it.date } }

    fun shiftMonth(delta: Int) {
        val total = jSel.first * 12 + (jSel.second - 1) + delta
        val ny = total / 12
        val nm = total % 12 + 1
        val day = minOf(jSel.third, Jalali.monthLength(ny, nm))
        selected = Jalali.toLocalDate(ny, nm, day).toEpochDay()
    }

    val title = if (weekMode) {
        dateText(Jalali.fromLocalDate(weekStart)) + " تا " + dateText(Jalali.fromLocalDate(weekEnd))
    } else {
        Jalali.monthNames[jSel.second - 1] + " " + fa(jSel.first)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { T("تقویم", size = 24.sp, bold = true, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NeonChip(if (weekMode) "هفتهٔ قبل" else "ماه قبل", false, Neon.cyan, Modifier.width(86.dp)) {
                    if (weekMode) selected -= 7 else shiftMonth(-1)
                }
                T(title, size = 15.sp, bold = true, align = TextAlign.Center, modifier = Modifier.weight(1f))
                NeonChip(if (weekMode) "هفتهٔ بعد" else "ماه بعد", false, Neon.cyan, Modifier.width(86.dp)) {
                    if (weekMode) selected += 7 else shiftMonth(1)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip("ماهانه", !weekMode, Neon.cyan, Modifier.weight(1f)) { weekMode = false }
                NeonChip("هفتگی", weekMode, Neon.cyan, Modifier.weight(1f)) { weekMode = true }
                NeonChip("امروز", selected == today, Neon.magenta, Modifier.weight(1f)) { selected = today }
            }
        }
        item {
            ChipGrid(
                listOf("" to "همه") + CATEGORIES,
                { (cat ?: "") == it }, Neon.lime, 4
            ) { cat = if (it.isEmpty()) null else it }
        }

        if (!weekMode) {
            item {
                MonthGrid(jSel.first, jSel.second, selected, today, entries, shopDays.keys) { selected = it }
            }
            item {
                T(
                    "نقطه‌ها: قرمز عقب‌افتاده · زرد در انتظار · سبز انجام‌شده · خاکستری برنامهٔ آینده · صورتی خرید",
                    size = 10.sp, color = Neon.muted
                )
            }
            daySection(
                sel, entries[selected].orEmpty(), shopDays[selected].orEmpty(),
                today, now, onToggle, onOpen, onPostpone, onAddForDay, onOpenShopping
            )
        } else {
            for (k in 0 until 7) {
                val d = weekStart.plusDays(k.toLong())
                daySection(
                    d, entries[d.toEpochDay()].orEmpty(), shopDays[d.toEpochDay()].orEmpty(),
                    today, now, onToggle, onOpen, onPostpone, onAddForDay, onOpenShopping
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.daySection(
    d: LocalDate,
    list: List<PlanItem>,
    shop: List<ShoppingEntity>,
    today: Long,
    now: Long,
    onToggle: (PlanItem) -> Unit,
    onOpen: (TemplateEntity) -> Unit,
    onPostpone: (PlanItem) -> Unit,
    onAddForDay: (LocalDate) -> Unit,
    onOpenShopping: () -> Unit
) {
    val dayKey = d.toEpochDay()
    val sorted = list.sortedBy { if (it.inst.timeMin < 0) -1 else it.inst.timeMin }
    item(key = "h$dayKey") {
        DayHeader(d, sorted.size, dayKey == today) { onAddForDay(d) }
    }
    if (sorted.isEmpty() && shop.isEmpty()) {
        item(key = "e$dayKey") { T("کاری برای این روز نیست", size = 12.sp, color = Neon.muted) }
    }
    items(sorted, key = { "i${dayKey}_${it.inst.id}_${it.tpl.id}" }) { it2 ->
        val real = it2.inst.id >= 0
        PlanCard(
            it2, today, now,
            onToggle = { onToggle(it2) },
            onOpen = { onOpen(it2.tpl) },
            onPostpone = if (real) ({ onPostpone(it2) }) else null,
            readOnly = !real
        )
    }
    if (shop.isNotEmpty()) {
        item(key = "s$dayKey") {
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Neon.magenta.copy(alpha = 0.10f))
                    .border(1.dp, Neon.magenta.copy(alpha = 0.6f), shape)
                    .clickable(onClick = onOpenShopping)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(
                    "خرید: ${fa(shop.size)} مورد (${fa(shop.count { !it.bought })} باقی‌مانده)",
                    size = 13.sp, color = Neon.magenta, bold = true
                )
            }
        }
    }
}

@Composable
private fun DayHeader(d: LocalDate, count: Int, isToday: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(
                fullDateText(Jalali.fromLocalDate(d)),
                size = 15.sp, bold = true, color = if (isToday) Neon.cyan else Neon.text
            )
            T(if (count == 0) "کاری نیست" else "${fa(count)} کار", size = 11.sp, color = Neon.muted)
        }
        NeonChip("افزودن", false, Neon.cyan, Modifier.width(72.dp), onAdd)
    }
}

@Composable
private fun MonthGrid(
    jy: Int,
    jm: Int,
    selected: Long,
    today: Long,
    entries: Map<Long, List<PlanItem>>,
    shopDays: Set<Long>,
    onSelect: (Long) -> Unit
) {
    val first = Jalali.toLocalDate(jy, jm, 1)
    val len = Jalali.monthLength(jy, jm)
    val offset = Engine.dayIndex(first)
    val rows = (offset + len + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DAY_LETTERS.forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    T(it, size = 12.sp, color = Neon.muted, bold = true)
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - offset + 1
                    if (day in 1..len) {
                        val d = first.plusDays((day - 1).toLong()).toEpochDay()
                        DayCell(
                            Modifier.weight(1f), day, d == selected, d == today,
                            entries[d].orEmpty(), d in shopDays, today
                        ) { onSelect(d) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    day: Int,
    selected: Boolean,
    isToday: Boolean,
    list: List<PlanItem>,
    hasShop: Boolean,
    today: Long,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val real = list.filter { it.inst.id >= 0 }
    val overdue = real.any { it.inst.status == Status.PENDING && it.inst.dueDate < today }
    val pending = real.any { it.inst.status == Status.PENDING && it.inst.dueDate >= today }
    val done = real.any { it.inst.status == Status.DONE }
    val projected = list.any { it.inst.id < 0 }
    val borderColor = if (selected) Neon.cyan else Neon.magenta
    Column(
        modifier
            .padding(2.dp)
            .height(50.dp)
            .clip(shape)
            .background(if (selected) Neon.cyan.copy(alpha = 0.22f) else Color.Transparent)
            .then(if (isToday || selected) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        T(fa(day), size = 14.sp, bold = isToday || selected, color = if (isToday) Neon.magenta else Neon.text)
        Spacer(Modifier.height(3.dp))
        Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (overdue) Dot(Neon.red)
            if (pending) Dot(Neon.amber)
            if (done) Dot(Neon.lime)
            if (projected) Dot(Neon.muted)
            if (hasShop) Dot(Neon.magenta)
        }
    }
}

@Composable
private fun Dot(c: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(c))
}

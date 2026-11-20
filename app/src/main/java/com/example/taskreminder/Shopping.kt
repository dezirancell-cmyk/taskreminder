package com.example.taskreminder

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

private val RANGES = listOf(
    Shop.RANGE_TODAY to "امروز", Shop.RANGE_WEEK to "هفته",
    Shop.RANGE_MONTH to "ماه", Shop.RANGE_ALL to "همه"
)

private val SORTS = listOf(
    Shop.SORT_DATE to "ترتیب: تاریخ", Shop.SORT_NAME to "ترتیب: نام", Shop.SORT_PRICE to "ترتیب: قیمت"
)

@Composable
fun ShoppingTab(
    list: List<ShoppingEntity>,
    names: List<NameCat>,
    today: Long,
    onQuickAdd: (String, String) -> Unit,
    onToggle: (ShoppingEntity) -> Unit,
    onQty: (ShoppingEntity, Int) -> Unit,
    onOpen: (ShoppingEntity) -> Unit,
    onDelete: (ShoppingEntity) -> Unit,
    onConvert: (ShoppingEntity) -> Unit,
    onNew: () -> Unit,
    onRepeat: (List<ShoppingEntity>, Long) -> Unit
) {
    val ctx = LocalContext.current
    val todayD = LocalDate.ofEpochDay(today)
    var range by remember { mutableStateOf(Shop.RANGE_TODAY) }
    var cat by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Shop.SORT_DATE) }

    val shown = remember(list, range, cat, query, sort, today) {
        val q = query.trim()
        Shop.sorted(
            list.filter {
                Shop.inRange(it, range, todayD) &&
                    (cat == null || it.category == cat) &&
                    (q.isEmpty() || it.name.contains(q))
            },
            sort
        )
    }
    val totals = remember(shown) { Shop.totals(shown) }

    val quick = remember(cat, names) {
        val c = cat
        val hist = names.filter { c == null || it.category == c }.map { it.name to it.category }
        val base = if (c == null) hist + Shop.presets.getValue("food").map { it to "food" }
        else hist + Shop.presets[c].orEmpty().map { it to c }
        base.distinctBy { it.first }.take(24)
    }

    val repeatShift = when (range) {
        Shop.RANGE_TODAY -> 1L
        Shop.RANGE_WEEK -> 7L
        Shop.RANGE_MONTH -> 30L
        else -> 0L
    }
    val repeatLabel = when (range) {
        Shop.RANGE_TODAY -> "تکرار برای فردا"
        Shop.RANGE_WEEK -> "تکرار برای هفتهٔ بعد"
        else -> "تکرار برای ماه بعد"
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("خریدها", size = 24.sp, bold = true, modifier = Modifier.weight(1f))
                GhostButton("اشتراک") {
                    val text = Shop.shareText(shown.filter { !it.bought })
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }
                Spacer(Modifier.width(8.dp))
                GradientButton("کالای جدید", icon = Icons.Rounded.Add, onClick = onNew)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { (k, label) ->
                    NeonChip(label, range == k, Neon.magenta, Modifier.weight(1f)) { range = k }
                }
            }
        }
        item { TotalsCard(totals) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { T("جست‌وجوی کالا", size = 13.sp, color = Neon.muted) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
                shape = RoundedCornerShape(16.dp),
                colors = shopFieldColors(Neon.magenta),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            ChipGrid(
                listOf("" to "همه") + Shop.categories,
                { (cat ?: "") == it }, Neon.magenta, 4
            ) { cat = if (it.isEmpty()) null else it }
        }
        item {
            SectionLabel("افزودن سریع (با یک لمس)")
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quick, key = { it.first }) { (n, c) ->
                    QuickChip(n) { onQuickAdd(n, c) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip(
                    SORTS.first { it.first == sort }.second, false, Neon.cyan, Modifier.weight(1f)
                ) {
                    val i = SORTS.indexOfFirst { it.first == sort }
                    sort = SORTS[(i + 1) % SORTS.size].first
                }
                if (repeatShift > 0 && shown.isNotEmpty()) {
                    NeonChip(repeatLabel, false, Neon.lime, Modifier.weight(1f)) { onRepeat(shown, repeatShift) }
                }
            }
        }
        if (shown.isEmpty()) {
            item { EmptyState("کالایی در این بازه نیست", "از «افزودن سریع» یا «کالای جدید» شروع کن") }
        } else {
            items(shown, key = { it.id }) { e ->
                ShopCard(e, todayD, { onToggle(e) }, { onQty(e, it) }, { onOpen(e) }, { onDelete(e) }, { onConvert(e) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .background(Neon.surface)
            .border(1.dp, Neon.magenta.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        T("+ $text", size = 13.sp, color = Neon.text)
    }
}

@Composable
private fun TotalsCard(t: ShopTotals) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Neon.magenta.copy(alpha = 0.5f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        T("${fa(t.bought)} از ${fa(t.total)} قلم خریداری شده · ${fa(t.remaining)} باقی‌مانده", size = 14.sp, bold = true)
        T("جمع تخمینی: " + moneyText(t.estimated), size = 13.sp, color = Neon.muted)
        T("باقی‌ماندهٔ تخمینی: " + moneyText(t.remainingEst), size = 13.sp, color = Neon.amber)
        T("جمع واقعی خریدشده‌ها: " + moneyText(t.actual), size = 13.sp, color = Neon.lime)
        if (t.missingActual > 0) {
            T("برای ${fa(t.missingActual)} قلم خریدشده قیمت واقعی ثبت نشده", size = 11.sp, color = Neon.muted)
        }
    }
}

@Composable
private fun ShopCard(
    e: ShoppingEntity,
    today: LocalDate,
    onToggle: () -> Unit,
    onQty: (Int) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onConvert: () -> Unit
) {
    val color = if (e.bought) Neon.muted else Neon.magenta
    val shape = RoundedCornerShape(18.dp)
    val dateLabel = if (e.date == today.toEpochDay()) "امروز" else dateText(Jalali.fromEpochDay(e.date))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.15f))), shape)
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                if (e.bought) Icons.Rounded.TaskAlt else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (e.bought) "برگرداندن" else "خریداری شد",
                tint = if (e.bought) Neon.lime else Neon.muted,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            T(
                e.name, size = 15.sp, bold = true,
                color = if (e.bought) Neon.muted else Neon.text,
                decoration = if (e.bought) TextDecoration.LineThrough else null
            )
            T(Shop.categoryName(e.category) + " · " + dateLabel, size = 11.sp, color = Neon.muted)
            if (e.estPrice > 0 || e.actualPrice > 0) {
                val parts = mutableListOf<String>()
                if (e.estPrice > 0) parts.add("تخمینی " + moneyText(e.estPrice))
                if (e.actualPrice > 0) parts.add("واقعی " + moneyText(e.actualPrice))
                T(parts.joinToString(" · "), size = 11.sp, color = if (e.actualPrice > 0) Neon.lime else Neon.muted)
            }
            if (e.bought && e.actualPrice == 0L) T("قیمت واقعی ثبت نشده", size = 11.sp, color = Neon.amber)
            if (e.bought) {
                val amt = (if (e.actualPrice > 0) e.actualPrice else e.estPrice) * e.qty
                if (e.expenseId != 0L) {
                    T("هزینه ثبت شد", size = 11.sp, color = Neon.lime)
                } else if (amt > 0L) {
                    T(
                        "ثبت به‌عنوان هزینه", size = 12.sp, color = Neon.cyan, bold = true,
                        decoration = TextDecoration.Underline,
                        modifier = Modifier.clickable(onClick = onConvert).padding(vertical = 4.dp)
                    )
                }
            }
        }
        IconButton(onClick = { onQty(-1) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Remove, "کمتر", tint = color, modifier = Modifier.size(20.dp))
        }
        T(fa(e.qty), size = 15.sp, bold = true, color = color)
        IconButton(onClick = { onQty(1) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Add, "بیشتر", tint = color, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun shopFieldColors(color: Color) = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = color,
    unfocusedBorderColor = Neon.muted.copy(alpha = 0.4f),
    cursorColor = color,
    focusedTextColor = Neon.text,
    unfocusedTextColor = Neon.text
)

@Composable
private fun MoneyField(label: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value > 0) value.toString() else "") }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            val clean = s.map { c -> "۰۱۲۳۴۵۶۷۸۹".indexOf(c).let { if (it >= 0) "0123456789"[it] else c } }
                .joinToString("").filter { it.isDigit() }.take(12)
            text = clean
            onChange(clean.toLongOrNull() ?: 0L)
        },
        singleLine = true,
        label = { T(label, size = 12.sp, color = Neon.muted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
        shape = RoundedCornerShape(16.dp),
        colors = shopFieldColors(Neon.magenta),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- ویرایشگر کالا ----------

@Composable
fun ShopEditor(
    initial: ShoppingEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ShoppingEntity) -> Unit
) {
    val todayJ = remember { Jalali.fromLocalDate(LocalDate.now()) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "food") }
    var qty by remember { mutableStateOf(initial?.qty ?: 1) }
    var est by remember { mutableStateOf(initial?.estPrice ?: 0L) }
    var actual by remember { mutableStateOf(initial?.actualPrice ?: 0L) }
    var jDate by remember {
        mutableStateOf(Jalali.fromEpochDay(initial?.date ?: defaultDate.toEpochDay()))
    }
    var bought by remember { mutableStateOf(initial?.bought ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var picker by remember { mutableStateOf(false) }
    val color = Neon.magenta

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Neon.bg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T(
                    if (initial == null) "کالای جدید" else "ویرایش کالا",
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
                    value = name,
                    onValueChange = { name = it },
                    label = { T("نام کالا", size = 13.sp, color = Neon.muted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
                    shape = RoundedCornerShape(16.dp),
                    colors = shopFieldColors(color),
                    modifier = Modifier.fillMaxWidth()
                )
                SectionLabel("دسته‌بندی")
                ChipGrid(Shop.categories, { it == category }, color, 3) { category = it }

                SectionLabel("تعداد")
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                ) {
                    StepButton(Icons.Rounded.Add, color) { qty = (qty + 1).coerceAtMost(99) }
                    T(fa(qty), size = 22.sp, bold = true, color = color)
                    StepButton(Icons.Rounded.Remove, color) { qty = (qty - 1).coerceAtLeast(1) }
                }

                SectionLabel("قیمت هر واحد (اختیاری)")
                MoneyField("قیمت تخمینی", est) { est = it }
                MoneyField("قیمت واقعی", actual) { actual = it }

                SectionLabel("تاریخ")
                NeonTile(Icons.Rounded.CalendarMonth, color, "برای تغییر لمس کن", fullDateText(jDate)) { picker = true }

                SectionLabel("وضعیت")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeOption("خریداری نشده", Icons.Rounded.RadioButtonUnchecked, !bought, color, Modifier.weight(1f)) { bought = false }
                    ModeOption("خریداری شد", Icons.Rounded.TaskAlt, bought, color, Modifier.weight(1f)) { bought = true }
                }

                SectionLabel("یادداشت (اختیاری)")
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
                    shape = RoundedCornerShape(16.dp),
                    colors = shopFieldColors(color),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            GradientButton("ذخیره", Modifier.fillMaxWidth().padding(16.dp), enabled = name.isNotBlank()) {
                val wasBought = initial?.bought ?: false
                onSave(
                    ShoppingEntity(
                        id = initial?.id ?: 0L,
                        name = name.trim(),
                        category = category,
                        qty = qty,
                        estPrice = est,
                        actualPrice = actual,
                        date = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third).toEpochDay(),
                        bought = bought,
                        boughtAt = when {
                            !bought -> 0L
                            wasBought && initial != null -> initial.boughtAt
                            else -> System.currentTimeMillis()
                        },
                        note = note.trim(),
                        createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                        expenseId = initial?.expenseId ?: 0L
                    )
                )
            }
        }
    }

    if (picker) {
        JalaliDateDialog(initial = jDate, todayYear = todayJ.first, onDismiss = { picker = false }) {
            jDate = it
            picker = false
        }
    }
}

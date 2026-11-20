@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.taskreminder

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

// ---------- کمکی‌های مشترک ----------

fun money(v: Long, unit: String): String = moneyText(v) + " " + unit

private fun latin(s: String): String =
    s.map { c -> "۰۱۲۳۴۵۶۷۸۹".indexOf(c).let { if (it >= 0) "0123456789"[it] else c } }.joinToString("")

@Composable
private fun finField(color: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = color,
    unfocusedBorderColor = Neon.muted.copy(alpha = 0.4f),
    cursorColor = color,
    focusedTextColor = Neon.text,
    unfocusedTextColor = Neon.text
)

@Composable
fun TextInput(
    label: String,
    value: String,
    color: Color,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { T(label, size = 12.sp, color = Neon.muted) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Neon.text),
        shape = RoundedCornerShape(16.dp),
        colors = finField(color),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun AmountField(label: String, value: Long, color: Color, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value > 0) value.toString() else "") }
    TextInput(label, fa(text), color, KeyboardType.Number) { s ->
        val clean = latin(s).filter { it.isDigit() }.take(13)
        text = clean
        onChange(clean.toLongOrNull() ?: 0L)
    }
}

@Composable
fun PillChip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Neon.surface)
            .border(1.dp, if (selected) color else Neon.muted.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, size = 13.sp, color = if (selected) color else Neon.text, bold = selected)
    }
}

@Composable
fun FormScreen(
    title: String,
    color: Color,
    canSave: Boolean,
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
            GradientButton("ذخیره", Modifier.fillMaxWidth().padding(16.dp), enabled = canSave, onClick = onSave)
        }
    }
}

@Composable
fun AccountPicker(accounts: List<AccountEntity>, selected: Long, color: Color, onSelect: (Long) -> Unit) {
    ChipGrid(accounts.map { it.id.toString() to it.name }, { it == selected.toString() }, color, 2) {
        onSelect(it.toLong())
    }
}

@Composable
private fun DateTile(j: Triple<Int, Int, Int>, color: Color, label: String = "تاریخ", onClick: () -> Unit) {
    NeonTile(Icons.Rounded.CalendarMonth, color, label, fullDateText(j), onClick = onClick)
}

private val INCOME_COLOR get() = Neon.lime
private val EXPENSE_COLOR get() = Neon.red

// ---------- ثبت درآمد / هزینه ----------

@Composable
fun TxEditor(
    type: String,
    initial: TxEntity?,
    accounts: List<AccountEntity>,
    defaultAccount: Long,
    defaultCategory: String?,
    onDismiss: () -> Unit,
    onSave: (TxEntity) -> Unit
) {
    val isIncome = type == TxType.INCOME
    val color = if (isIncome) INCOME_COLOR else EXPENSE_COLOR
    val cats = if (isIncome) Fin.incomeCats else Fin.expenseCats
    val todayJ = remember { Jalali.fromLocalDate(LocalDate.now()) }

    var amount by remember { mutableStateOf(initial?.amount ?: 0L) }
    var category by remember {
        mutableStateOf(initial?.category ?: defaultCategory?.takeIf { k -> cats.any { it.first == k } } ?: cats.first().first)
    }
    var accountId by remember {
        mutableStateOf(
            initial?.accountId
                ?: defaultAccount.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id ?: 0L
        )
    }
    var jDate by remember { mutableStateOf(Jalali.fromEpochDay(initial?.date ?: LocalDate.now().toEpochDay())) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var picker by remember { mutableStateOf(false) }

    FormScreen(
        title = (if (initial == null) "ثبت " else "ویرایش ") + (if (isIncome) "درآمد" else "هزینه"),
        color = color,
        canSave = amount > 0 && accountId != 0L,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                TxEntity(
                    id = initial?.id ?: 0L, type = type, amount = amount, category = category,
                    accountId = accountId, toAccountId = 0L,
                    date = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third).toEpochDay(),
                    note = note.trim(), partyId = initial?.partyId ?: 0L, invoiceId = initial?.invoiceId ?: 0L,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    ) {
        AmountField("مبلغ", amount, color) { amount = it }
        SectionLabel("دسته‌بندی")
        ChipGrid(cats, { it == category }, color, 3) { category = it }
        SectionLabel("حساب")
        AccountPicker(accounts, accountId, color) { accountId = it }
        DateTile(jDate, color) { picker = true }
        TextInput("توضیحات (اختیاری)", note, color) { note = it }
    }
    if (picker) {
        JalaliDateDialog(initial = jDate, todayYear = todayJ.first, onDismiss = { picker = false }) {
            jDate = it
            picker = false
        }
    }
}

@Composable
fun TransferEditor(
    initial: TxEntity?,
    accounts: List<AccountEntity>,
    defaultAccount: Long,
    onDismiss: () -> Unit,
    onSave: (TxEntity) -> Unit
) {
    val color = Neon.cyan
    val todayJ = remember { Jalali.fromLocalDate(LocalDate.now()) }
    var amount by remember { mutableStateOf(initial?.amount ?: 0L) }
    var from by remember {
        mutableStateOf(initial?.accountId ?: defaultAccount.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var to by remember {
        mutableStateOf(initial?.toAccountId ?: accounts.firstOrNull { it.id != from }?.id ?: 0L)
    }
    var jDate by remember { mutableStateOf(Jalali.fromEpochDay(initial?.date ?: LocalDate.now().toEpochDay())) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var picker by remember { mutableStateOf(false) }

    FormScreen(
        title = if (initial == null) "انتقال بین حساب‌ها" else "ویرایش انتقال",
        color = color,
        canSave = amount > 0 && from != 0L && to != 0L && from != to,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                TxEntity(
                    id = initial?.id ?: 0L, type = TxType.TRANSFER, amount = amount, category = "transfer",
                    accountId = from, toAccountId = to,
                    date = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third).toEpochDay(),
                    note = note.trim(), partyId = 0L, invoiceId = 0L,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    ) {
        if (accounts.size < 2) T("برای انتقال حداقل دو حساب لازم است.", size = 13.sp, color = Neon.amber)
        AmountField("مبلغ", amount, color) { amount = it }
        SectionLabel("از حساب")
        AccountPicker(accounts, from, color) { from = it }
        SectionLabel("به حساب")
        AccountPicker(accounts, to, color) { to = it }
        if (from == to && from != 0L) T("حساب مبدأ و مقصد باید فرق کنند.", size = 12.sp, color = Neon.red)
        DateTile(jDate, color) { picker = true }
        TextInput("توضیحات (اختیاری)", note, color) { note = it }
    }
    if (picker) {
        JalaliDateDialog(initial = jDate, todayYear = todayJ.first, onDismiss = { picker = false }) {
            jDate = it
            picker = false
        }
    }
}

@Composable
fun AccountEditor(initial: AccountEntity?, currency: String, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit) {
    val color = Neon.cyan
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "BANK") }
    var balance by remember { mutableStateOf(initial?.initial ?: 0L) }
    FormScreen(
        title = if (initial == null) "حساب جدید" else "ویرایش حساب",
        color = color,
        canSave = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                AccountEntity(
                    id = initial?.id ?: 0L, name = name.trim(), type = type, initial = balance,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    ) {
        TextInput("نام حساب", name, color) { name = it }
        SectionLabel("نوع")
        ChipGrid(listOf("BANK" to "حساب بانکی", "CASH" to "کیف پول نقدی"), { it == type }, color, 2) { type = it }
        AmountField("موجودی اولیه ($currency)", balance, color) { balance = it }
    }
}

// ---------- حساب دیگران / محصول ----------

@Composable
fun PartyEditor(initial: PartyEntity?, onDismiss: () -> Unit, onSave: (PartyEntity) -> Unit) {
    val color = Neon.cyan
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    FormScreen(
        title = if (initial == null) "حساب جدید برای دیگران" else "ویرایش حساب",
        color = color,
        canSave = name.isNotBlank(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                PartyEntity(
                    id = initial?.id ?: 0L, name = name.trim(), phone = latin(phone).trim(), note = note.trim(),
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    ) {
        TextInput("نام (مشتری یا طرف حساب)", name, color) { name = it }
        TextInput("تلفن (اختیاری)", phone, color, KeyboardType.Phone) { phone = it }
        TextInput("یادداشت (اختیاری)", note, color) { note = it }
    }
}

@Composable
fun ProductEditor(initial: ProductEntity?, currency: String, onDismiss: () -> Unit, onSave: (ProductEntity) -> Unit) {
    val color = Neon.magenta
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "عدد") }
    var price by remember { mutableStateOf(initial?.price ?: 0L) }
    FormScreen(
        title = if (initial == null) "محصول جدید" else "ویرایش محصول",
        color = color,
        canSave = name.isNotBlank() && price > 0,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                ProductEntity(
                    id = initial?.id ?: 0L, name = name.trim(), unit = unit, price = price,
                    createdAt = initial?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    ) {
        TextInput("نام محصول یا خدمت", name, color) { name = it }
        SectionLabel("واحد")
        ChipGrid(Fin.units.map { it to it }, { it == unit }, color, 3) { unit = it }
        AmountField("قیمت هر واحد ($currency)", price, color) { price = it }
    }
}

// ---------- فاکتور ----------

@Composable
fun InvoiceEditor(
    initial: InvoiceEntity?,
    initialLines: List<InvoiceLineEntity>,
    parties: List<PartyEntity>,
    products: List<ProductEntity>,
    accounts: List<AccountEntity>,
    defaultParty: Long,
    defaultAccount: Long,
    currency: String,
    onSaveParty: (PartyEntity) -> Unit,
    onDismiss: () -> Unit,
    onSave: (InvoiceEntity, List<InvoiceLineEntity>, Long, Long) -> Unit
) {
    val color = Neon.amber
    val todayJ = remember { Jalali.fromLocalDate(LocalDate.now()) }
    var partyId by remember { mutableStateOf(initial?.partyId ?: defaultParty) }
    var jDate by remember { mutableStateOf(Jalali.fromEpochDay(initial?.date ?: LocalDate.now().toEpochDay())) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val lines = remember { mutableStateListOf<InvoiceLineEntity>().apply { addAll(initialLines) } }
    var customName by remember { mutableStateOf("") }
    var customPrice by remember { mutableStateOf(0L) }
    var customKey by remember { mutableStateOf(0) }
    var receive by remember { mutableStateOf(0L) }
    var account by remember {
        mutableStateOf(defaultAccount.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var picker by remember { mutableStateOf(false) }
    var newParty by remember { mutableStateOf(false) }
    val total = Fin.invoiceTotal(lines)

    FormScreen(
        title = if (initial == null) "فاکتور جدید" else "ویرایش فاکتور ${fa(initial.number)}",
        color = color,
        canSave = lines.isNotEmpty(),
        onDismiss = onDismiss,
        onSave = {
            onSave(
                InvoiceEntity(
                    id = initial?.id ?: 0L, number = initial?.number ?: 0, partyId = partyId,
                    date = Jalali.toLocalDate(jDate.first, jDate.second, jDate.third).toEpochDay(),
                    note = note.trim(), createdAt = initial?.createdAt ?: System.currentTimeMillis()
                ),
                lines.toList(),
                if (initial == null) receive.coerceAtMost(total) else 0L,
                account
            )
        }
    ) {
        SectionLabel("مشتری (حساب دیگران)")
        if (parties.isNotEmpty()) {
            ChipGrid(
                listOf("0" to "بدون حساب") + parties.map { it.id.toString() to it.name },
                { it == partyId.toString() }, color, 2
            ) { partyId = it.toLong() }
        }
        GhostButton("+ حساب جدید", Modifier.fillMaxWidth()) { newParty = true }

        DateTile(jDate, color) { picker = true }

        SectionLabel("ردیف‌ها")
        if (products.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { p ->
                    PillChip("+ ${p.name}", false, Neon.magenta) {
                        val i = lines.indexOfFirst { it.name == p.name && it.unitPrice == p.price }
                        if (i >= 0) lines[i] = lines[i].copy(qty = (lines[i].qty + 1).coerceAtMost(9999))
                        else lines.add(InvoiceLineEntity(invoiceId = 0L, name = p.name, unit = p.unit, qty = 1, unitPrice = p.price))
                    }
                }
            }
        } else {
            T("هنوز محصولی تعریف نشده؛ می‌توانی ردیف دلخواه اضافه کنی.", size = 12.sp, color = Neon.muted)
        }
        lines.forEachIndexed { idx, l ->
            LineRow(l, currency, { d -> lines[idx] = l.copy(qty = (l.qty + d).coerceIn(1, 9999)) }) { lines.removeAt(idx) }
        }
        // ردیف دلخواه (key برای خالی‌شدن فیلدها بعد از افزودن)
        androidx.compose.runtime.key(customKey) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("ردیف دلخواه: نام", customName, color) { customName = it }
                AmountField("قیمت واحد ($currency)", customPrice, color) { customPrice = it }
            }
        }
        GhostButton("افزودن ردیف دلخواه", Modifier.fillMaxWidth()) {
            if (customName.isNotBlank() && customPrice > 0) {
                lines.add(InvoiceLineEntity(invoiceId = 0L, name = customName.trim(), unit = "عدد", qty = 1, unitPrice = customPrice))
                customName = ""
                customPrice = 0L
                customKey++
            }
        }
        T("جمع فاکتور: " + money(total, currency), size = 17.sp, bold = true, color = color)

        if (initial == null) {
            SectionLabel("دریافت هم‌زمان (اختیاری)")
            AmountField("مبلغ دریافتی ($currency)", receive, Neon.lime) { receive = it }
            if (receive > 0) AccountPicker(accounts, account, Neon.lime) { account = it }
        }
        TextInput("توضیحات (اختیاری)", note, color) { note = it }
    }
    if (picker) {
        JalaliDateDialog(initial = jDate, todayYear = todayJ.first, onDismiss = { picker = false }) {
            jDate = it
            picker = false
        }
    }
    if (newParty) {
        PartyEditor(null, { newParty = false }) {
            onSaveParty(it)
            newParty = false
        }
    }
}

@Composable
private fun LineRow(l: InvoiceLineEntity, currency: String, onQty: (Int) -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Neon.surface)
            .border(1.dp, Neon.muted.copy(alpha = 0.25f), shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            T(l.name, size = 14.sp, bold = true)
            T("${moneyText(l.unitPrice)} · جمع ${money(Fin.lineTotal(l), currency)}", size = 11.sp, color = Neon.muted)
        }
        IconButton(onClick = { onQty(-1) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Remove, null, tint = Neon.amber, modifier = Modifier.size(20.dp))
        }
        T(fa(l.qty), size = 15.sp, bold = true, color = Neon.amber)
        IconButton(onClick = { onQty(1) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.Add, null, tint = Neon.amber, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.DeleteOutline, "حذف ردیف", tint = Neon.red.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun PaymentDialog(
    party: PartyEntity?,
    fixedInvoice: InvoiceEntity?,
    openInvoices: List<Pair<InvoiceEntity, Long>>,
    accounts: List<AccountEntity>,
    defaultAccount: Long,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (partyId: Long, invoiceId: Long, amount: Long, accountId: Long, date: Long) -> Unit
) {
    val todayJ = remember { Jalali.fromLocalDate(LocalDate.now()) }
    var invoiceId by remember { mutableStateOf(fixedInvoice?.id ?: 0L) }
    val remainingOf = { id: Long -> openInvoices.firstOrNull { it.first.id == id }?.second ?: 0L }
    var amount by remember { mutableStateOf(if (fixedInvoice != null) remainingOf(fixedInvoice.id) else 0L) }
    var amountKey by remember { mutableStateOf(0) }
    var account by remember {
        mutableStateOf(defaultAccount.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var jDate by remember { mutableStateOf(todayJ) }
    var picker by remember { mutableStateOf(false) }
    val partyId = party?.id ?: fixedInvoice?.partyId ?: 0L

    NeonDialog(
        title = "ثبت دریافت" + if (party != null) " از ${party.name}" else "",
        confirmText = "ثبت",
        onDismiss = onDismiss,
        onConfirm = {
            if (amount > 0 && account != 0L) {
                onConfirm(partyId, invoiceId, amount, account, Jalali.toLocalDate(jDate.first, jDate.second, jDate.third).toEpochDay())
            }
        }
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.runtime.key(amountKey) { AmountField("مبلغ ($currency)", amount, Neon.lime) { amount = it } }
            if (fixedInvoice == null && openInvoices.isNotEmpty()) {
                SectionLabel("اختصاص به فاکتور (اختیاری)")
                ChipGrid(
                    listOf("0" to "بدون فاکتور") + openInvoices.map { it.first.id.toString() to "فاکتور ${fa(it.first.number)}" },
                    { it == invoiceId.toString() }, Neon.amber, 2
                ) {
                    invoiceId = it.toLong()
                    if (invoiceId != 0L) {
                        amount = remainingOf(invoiceId)
                        amountKey++
                    }
                }
            }
            SectionLabel("واریز به حساب")
            AccountPicker(accounts, account, Neon.lime) { account = it }
            DateTile(jDate, Neon.lime) { picker = true }
        }
    }
    if (picker) {
        JalaliDateDialog(initial = jDate, todayYear = todayJ.first, onDismiss = { picker = false }) {
            jDate = it
            picker = false
        }
    }
}

@Composable
fun InvoiceDetail(
    inv: InvoiceEntity,
    partyName: String,
    lines: List<InvoiceLineEntity>,
    payments: List<TxEntity>,
    accounts: List<AccountEntity>,
    currency: String,
    onPay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val total = Fin.invoiceTotal(lines)
    val paid = payments.sumOf { it.amount }
    val left = total - paid
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .clip(shape)
                .background(Neon.surface)
                .border(1.dp, Brush.linearGradient(listOf(Neon.amber, Neon.magenta)), shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("فاکتور شمارهٔ ${fa(inv.number)}", size = 18.sp, bold = true, color = Neon.amber)
                T(
                    (if (partyName.isNotBlank()) "$partyName · " else "") + dateText(Jalali.fromEpochDay(inv.date)),
                    size = 12.sp, color = Neon.muted
                )
                lines.forEach {
                    T("• ${it.name} — ${fa(it.qty)} × ${moneyText(it.unitPrice)} = ${moneyText(Fin.lineTotal(it))}", size = 13.sp)
                }
                T("جمع: " + money(total, currency), size = 15.sp, bold = true)
                T("دریافتی: " + money(paid, currency), size = 13.sp, color = Neon.lime)
                T(
                    if (left <= 0) "تسویه شده" else "مانده: " + money(left, currency),
                    size = 14.sp, bold = true, color = if (left <= 0) Neon.lime else Neon.amber
                )
                if (payments.isNotEmpty()) {
                    SectionLabel("دریافتی‌ها")
                    payments.forEach { p ->
                        val acc = accounts.firstOrNull { it.id == p.accountId }?.name ?: ""
                        T("${dateText(Jalali.fromEpochDay(p.date))} · ${moneyText(p.amount)} · $acc", size = 12.sp, color = Neon.muted)
                    }
                    T("برای حذف یک دریافتی، آن را از تب «تراکنش‌ها» پاک کن.", size = 11.sp, color = Neon.muted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (left > 0) NeonChip("ثبت دریافت", false, Neon.lime, Modifier.weight(1f), onPay)
                NeonChip("ویرایش", false, Neon.cyan, Modifier.weight(1f), onEdit)
                NeonChip("اشتراک", false, Neon.magenta, Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, Fin.invoiceShareText(inv, partyName, lines, paid, currency))
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip("حذف فاکتور", false, Neon.red, Modifier.weight(1f), onDelete)
                GhostButton("بستن", Modifier.weight(1f), onDismiss)
            }
        }
    }
}

// ---------- تب مالی ----------

private class Confirm(val title: String, val text: String, val action: () -> Unit)

private val SECTIONS = listOf(
    "خلاصه", "تراکنش‌ها", "حساب‌ها", "بودجه", "حساب دیگران", "محصولات", "فاکتورها"
)

@Composable
fun FinanceTab(vm: TaskViewModel, today: Long) {
    val accounts by vm.accounts.collectAsState()
    val txs by vm.txs.collectAsState()
    val parties by vm.parties.collectAsState()
    val products by vm.products.collectAsState()
    val invoices by vm.invoices.collectAsState()
    val lines by vm.invoiceLines.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val currency by vm.currency.collectAsState()
    val todayD = LocalDate.ofEpochDay(today)

    var section by remember { mutableStateOf(0) }
    var txType by remember { mutableStateOf<String?>(null) }
    var txEditing by remember { mutableStateOf<TxEntity?>(null) }
    var transferOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var accountEditing by remember { mutableStateOf<AccountEntity?>(null) }
    var partyOpen by remember { mutableStateOf(false) }
    var partyEditing by remember { mutableStateOf<PartyEntity?>(null) }
    var productOpen by remember { mutableStateOf(false) }
    var productEditing by remember { mutableStateOf<ProductEntity?>(null) }
    var invoiceOpen by remember { mutableStateOf(false) }
    var invoiceEditing by remember { mutableStateOf<InvoiceEntity?>(null) }
    var invoiceParty by remember { mutableStateOf(0L) }
    var detail by remember { mutableStateOf<InvoiceEntity?>(null) }
    var payOpen by remember { mutableStateOf(false) }
    var payParty by remember { mutableStateOf<PartyEntity?>(null) }
    var payInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var budgetCat by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val accName = { id: Long -> accounts.firstOrNull { it.id == id }?.name ?: "—" }
    val partyName = { id: Long -> parties.firstOrNull { it.id == id }?.name ?: "" }
    val openInvoicesOf = { pid: Long ->
        invoices.filter { it.partyId == pid }.map { inv ->
            inv to (Fin.invoiceTotal(lines.filter { it.invoiceId == inv.id }) - Fin.invoicePaid(inv.id, txs))
        }.filter { it.second > 0 }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { T("مالی", size = 24.sp, bold = true, modifier = Modifier.padding(top = 8.dp)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SECTIONS.size) { i -> PillChip(SECTIONS[i], section == i, Neon.cyan) { section = i } }
            }
        }

        when (section) {
            0 -> {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeOption("درآمد", Icons.Rounded.ArrowDownward, true, INCOME_COLOR, Modifier.weight(1f)) {
                            txEditing = null; txType = TxType.INCOME
                        }
                        ModeOption("هزینه", Icons.Rounded.ArrowUpward, true, EXPENSE_COLOR, Modifier.weight(1f)) {
                            txEditing = null; txType = TxType.EXPENSE
                        }
                        ModeOption("انتقال", Icons.Rounded.SwapHoriz, true, Neon.cyan, Modifier.weight(1f)) {
                            txEditing = null; transferOpen = true
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("واحد پول:", size = 13.sp, color = Neon.muted, modifier = Modifier.padding(top = 9.dp))
                        listOf("تومان", "ریال").forEach { u ->
                            NeonChip(u, currency == u, Neon.cyan, Modifier.width(72.dp)) { vm.setCurrency(u) }
                        }
                    }
                }
                val (ms, me) = Fin.monthBounds(todayD)
                val (ws, we) = Fin.weekBounds(todayD)
                item { SummaryCard("امروز", Fin.summary(txs, today, today), currency) }
                item { SummaryCard("این هفته", Fin.summary(txs, ws, we), currency) }
                item { SummaryCard("این ماه", Fin.summary(txs, ms, me), currency) }
                val receivable = parties.sumOf { maxOf(0L, Fin.partyBalance(it.id, invoices, lines, txs)) }
                if (receivable > 0) {
                    item {
                        T("طلب از دیگران (فاکتورهای باز): " + money(receivable, currency), size = 14.sp, bold = true, color = Neon.amber)
                    }
                }
                val spent = Fin.spentByCategory(txs, ms, me)
                val alerts = budgets.filter { Fin.budgetLevel(spent[it.category] ?: 0L, it.amount) > 0 }
                if (alerts.isNotEmpty()) {
                    item { SectionTitle("هشدار بودجه", Neon.amber, alerts.size) }
                    items(alerts, key = { "al${it.category}" }) { b ->
                        val s = spent[b.category] ?: 0L
                        val lvl = Fin.budgetLevel(s, b.amount)
                        T(
                            Fin.catName(b.category) + ": " + (if (lvl == 2) "از بودجه گذشته" else "نزدیک سقف") +
                                " (${moneyText(s)} از ${moneyText(b.amount)})",
                            size = 13.sp, color = if (lvl == 2) Neon.red else Neon.amber
                        )
                    }
                }
                item { SectionTitle("حساب‌ها", Neon.cyan, accounts.size) }
                items(accounts, key = { "ac${it.id}" }) { a ->
                    val b = Fin.accountBalance(a, txs)
                    Row(Modifier.fillMaxWidth()) {
                        T(a.name, size = 14.sp, modifier = Modifier.weight(1f))
                        T(money(b, currency), size = 14.sp, bold = true, color = if (b < 0) Neon.red else Neon.text)
                    }
                }
                item {
                    T(
                        "جمع موجودی: " + money(accounts.sumOf { Fin.accountBalance(it, txs) }, currency),
                        size = 14.sp, bold = true, color = Neon.cyan
                    )
                }
            }

            1 -> {
                item { TxList(txs, accounts, today, currency, { t ->
                    txEditing = t
                    if (t.type == TxType.TRANSFER) transferOpen = true else txType = t.type
                }, { t ->
                    confirm = Confirm("حذف تراکنش", "این تراکنش حذف شود؟") { vm.deleteTx(t.id) }
                }) }
            }

            2 -> {
                item { GradientButton("حساب جدید", icon = Icons.Rounded.Add) { accountEditing = null; accountOpen = true } }
                items(accounts, key = { "a${it.id}" }) { a ->
                    val b = Fin.accountBalance(a, txs)
                    CardRow(Neon.cyan, onClick = { accountEditing = a; accountOpen = true }) {
                        Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Neon.cyan, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(a.name, size = 15.sp, bold = true)
                            T(if (a.type == "CASH") "کیف پول نقدی" else "حساب بانکی", size = 11.sp, color = Neon.muted)
                            T(money(b, currency), size = 13.sp, bold = true, color = if (b < 0) Neon.red else Neon.lime)
                        }
                        IconButton(onClick = {
                            if (txs.any { it.accountId == a.id || it.toAccountId == a.id }) {
                                notice = "این حساب تراکنش دارد و حذف نمی‌شود. اول تراکنش‌هایش را پاک یا ویرایش کن."
                            } else {
                                confirm = Confirm("حذف حساب", "«${a.name}» حذف شود؟") { vm.deleteAccount(a.id) }
                            }
                        }) { Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f)) }
                    }
                }
            }

            3 -> {
                val (ms, me) = Fin.monthBounds(todayD)
                val spent = Fin.spentByCategory(txs, ms, me)
                item {
                    val j = Jalali.fromLocalDate(todayD)
                    T("بودجهٔ ${Jalali.monthNames[j.second - 1]} ${fa(j.first)} — برای تنظیم روی دسته بزن", size = 13.sp, color = Neon.muted)
                }
                items(Fin.expenseCats, key = { "b${it.first}" }) { (key, label) ->
                    val b = budgets.firstOrNull { it.category == key }?.amount ?: 0L
                    val s = spent[key] ?: 0L
                    val lvl = Fin.budgetLevel(s, b)
                    val c = when (lvl) { 2 -> Neon.red; 1 -> Neon.amber; else -> Neon.lime }
                    CardRow(c, onClick = { budgetCat = key }) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            T(label, size = 15.sp, bold = true)
                            T(
                                if (b > 0) "خرج ${moneyText(s)} از ${moneyText(b)}" else "بدون بودجه · خرج ماه: ${moneyText(s)}",
                                size = 12.sp, color = Neon.muted
                            )
                            if (b > 0) {
                                LinearProgressIndicator(
                                    progress = { (s.toFloat() / b.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = c, trackColor = c.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }

            4 -> {
                item { GradientButton("حساب جدید برای دیگران", icon = Icons.Rounded.Add) { partyEditing = null; partyOpen = true } }
                if (parties.isEmpty()) item { EmptyState("هنوز حسابی برای دیگران نیست", "مشتری‌ها و طرف‌حساب‌ها را اینجا اضافه کن") }
                items(parties, key = { "p${it.id}" }) { p ->
                    val bal = Fin.partyBalance(p.id, invoices, lines, txs)
                    val count = invoices.count { it.partyId == p.id }
                    CardRow(Neon.cyan, onClick = { partyEditing = p; partyOpen = true }) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            T(p.name, size = 15.sp, bold = true)
                            if (p.phone.isNotBlank()) T(fa(p.phone), size = 11.sp, color = Neon.muted)
                            T(
                                when {
                                    bal > 0 -> "طلب ما: " + money(bal, currency)
                                    bal < 0 -> "پیش‌دریافت: " + money(-bal, currency)
                                    else -> "تسویه"
                                },
                                size = 13.sp, bold = true,
                                color = when { bal > 0 -> Neon.amber; bal < 0 -> Neon.cyan; else -> Neon.lime }
                            )
                            T("${fa(count)} فاکتور", size = 11.sp, color = Neon.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NeonChip("فاکتور جدید", false, Neon.amber, Modifier.width(92.dp)) {
                                    invoiceEditing = null; invoiceParty = p.id; invoiceOpen = true
                                }
                                NeonChip("دریافت وجه", false, Neon.lime, Modifier.width(92.dp)) {
                                    payParty = p; payInvoice = null; payOpen = true
                                }
                            }
                        }
                        IconButton(onClick = {
                            if (invoices.any { it.partyId == p.id } || txs.any { it.partyId == p.id }) {
                                notice = "این حساب فاکتور یا دریافتی دارد و حذف نمی‌شود."
                            } else {
                                confirm = Confirm("حذف حساب", "«${p.name}» حذف شود؟") { vm.deleteParty(p.id) }
                            }
                        }) { Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f)) }
                    }
                }
            }

            5 -> {
                item { GradientButton("محصول جدید", icon = Icons.Rounded.Add) { productEditing = null; productOpen = true } }
                if (products.isEmpty()) item { EmptyState("هنوز محصولی تعریف نشده", "محصول یا خدمتی که می‌فروشی را اضافه کن") }
                items(products, key = { "pr${it.id}" }) { p ->
                    CardRow(Neon.magenta, onClick = { productEditing = p; productOpen = true }) {
                        Column(Modifier.weight(1f)) {
                            T(p.name, size = 15.sp, bold = true)
                            T("${money(p.price, currency)} / ${p.unit}", size = 12.sp, color = Neon.muted)
                        }
                        IconButton(onClick = {
                            confirm = Confirm("حذف محصول", "«${p.name}» حذف شود؟ فاکتورهای قبلی تغییر نمی‌کنند.") { vm.deleteProduct(p.id) }
                        }) { Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f)) }
                    }
                }
            }

            else -> {
                item { GradientButton("فاکتور جدید", icon = Icons.Rounded.Add) { invoiceEditing = null; invoiceParty = 0L; invoiceOpen = true } }
                if (invoices.isEmpty()) item { EmptyState("هنوز فاکتوری صادر نشده", "با «فاکتور جدید» شروع کن") }
                items(invoices, key = { "i${it.id}" }) { inv ->
                    val total = Fin.invoiceTotal(lines.filter { it.invoiceId == inv.id })
                    val paid = Fin.invoicePaid(inv.id, txs)
                    val left = total - paid
                    CardRow(if (left <= 0) Neon.lime else Neon.amber, onClick = { detail = inv }) {
                        Column(Modifier.weight(1f)) {
                            T("فاکتور ${fa(inv.number)}" + partyName(inv.partyId).let { if (it.isBlank()) "" else " · $it" }, size = 15.sp, bold = true)
                            T(dateText(Jalali.fromEpochDay(inv.date)) + " · جمع " + money(total, currency), size = 12.sp, color = Neon.muted)
                            T(
                                if (left <= 0) "تسویه شده" else "مانده: " + money(left, currency),
                                size = 12.sp, bold = true, color = if (left <= 0) Neon.lime else Neon.amber
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    // ---------- دیالوگ‌ها ----------
    txType?.let { ty ->
        TxEditor(
            type = ty, initial = txEditing, accounts = accounts,
            defaultAccount = vm.lastAccount(), defaultCategory = vm.lastCategory(ty),
            onDismiss = { txType = null; txEditing = null },
            onSave = { vm.saveTx(it); txType = null; txEditing = null }
        )
    }
    if (transferOpen) {
        TransferEditor(
            initial = txEditing, accounts = accounts, defaultAccount = vm.lastAccount(),
            onDismiss = { transferOpen = false; txEditing = null },
            onSave = { vm.saveTx(it); transferOpen = false; txEditing = null }
        )
    }
    if (accountOpen) {
        AccountEditor(accountEditing, currency, { accountOpen = false; accountEditing = null }) {
            vm.saveAccount(it); accountOpen = false; accountEditing = null
        }
    }
    if (partyOpen) {
        PartyEditor(partyEditing, { partyOpen = false; partyEditing = null }) {
            vm.saveParty(it); partyOpen = false; partyEditing = null
        }
    }
    if (productOpen) {
        ProductEditor(productEditing, currency, { productOpen = false; productEditing = null }) {
            vm.saveProduct(it); productOpen = false; productEditing = null
        }
    }
    if (invoiceOpen) {
        InvoiceEditor(
            initial = invoiceEditing,
            initialLines = invoiceEditing?.let { e -> lines.filter { it.invoiceId == e.id } } ?: emptyList(),
            parties = parties, products = products, accounts = accounts,
            defaultParty = invoiceParty, defaultAccount = vm.lastAccount(), currency = currency,
            onSaveParty = { vm.saveParty(it) },
            onDismiss = { invoiceOpen = false; invoiceEditing = null },
            onSave = { inv, ls, rec, acc ->
                vm.saveInvoice(inv, ls, rec, acc)
                invoiceOpen = false
                invoiceEditing = null
            }
        )
    }
    detail?.let { inv0 ->
        // نسخهٔ تازه از دیتابیس (بعد از ثبت دریافت)
        val inv = invoices.firstOrNull { it.id == inv0.id }
        if (inv == null) {
            detail = null
        } else {
            val ls = lines.filter { it.invoiceId == inv.id }
            val pays = txs.filter { it.type == TxType.INCOME && it.invoiceId == inv.id }
            InvoiceDetail(
                inv = inv, partyName = partyName(inv.partyId), lines = ls, payments = pays,
                accounts = accounts, currency = currency,
                onPay = { payParty = null; payInvoice = inv; payOpen = true },
                onEdit = { invoiceEditing = inv; invoiceParty = inv.partyId; invoiceOpen = true; detail = null },
                onDelete = {
                    if (pays.isNotEmpty()) notice = "این فاکتور دریافتی دارد. اول دریافتی‌ها را از تب «تراکنش‌ها» حذف کن."
                    else confirm = Confirm("حذف فاکتور", "فاکتور ${fa(inv.number)} حذف شود؟") { vm.deleteInvoice(inv.id); detail = null }
                },
                onDismiss = { detail = null }
            )
        }
    }
    if (payOpen) {
        val pid = payParty?.id ?: payInvoice?.partyId ?: 0L
        PaymentDialog(
            party = payParty, fixedInvoice = payInvoice,
            openInvoices = if (pid != 0L) openInvoicesOf(pid) else
                payInvoice?.let { i -> listOf(i to (Fin.invoiceTotal(lines.filter { it.invoiceId == i.id }) - Fin.invoicePaid(i.id, txs))) } ?: emptyList(),
            accounts = accounts, defaultAccount = vm.lastAccount(), currency = currency,
            onDismiss = { payOpen = false; payParty = null; payInvoice = null },
            onConfirm = { p, i, a, acc, d ->
                vm.addPayment(p, i, a, acc, d, if (i != 0L) "دریافت فاکتور" else "دریافت وجه")
                payOpen = false; payParty = null; payInvoice = null
            }
        )
    }
    budgetCat?.let { key ->
        var amt by remember(key) { mutableStateOf(budgets.firstOrNull { it.category == key }?.amount ?: 0L) }
        NeonDialog(
            title = "بودجهٔ ماهانه — " + Fin.catName(key),
            confirmText = "ذخیره",
            onDismiss = { budgetCat = null },
            onConfirm = { vm.setBudget(key, amt); budgetCat = null }
        ) {
            AmountField("سقف بودجه ($currency)", amt, Neon.lime) { amt = it }
            Spacer(Modifier.height(6.dp))
            T("خالی یا صفر = حذف بودجه", size = 11.sp, color = Neon.muted)
        }
    }
    confirm?.let { c ->
        NeonDialog(
            title = c.title, confirmText = "تأیید",
            onDismiss = { confirm = null },
            onConfirm = { c.action(); confirm = null }
        ) { T(c.text, align = TextAlign.Center, color = Neon.muted) }
    }
    notice?.let { n ->
        NeonDialog(title = "توجه", confirmText = "باشه", onDismiss = { notice = null }, onConfirm = { notice = null }) {
            T(n, align = TextAlign.Center, color = Neon.muted)
        }
    }
}

@Composable
private fun SummaryCard(title: String, s: Summary, currency: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Neon.cyan.copy(alpha = 0.4f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        T(title, size = 14.sp, bold = true, color = Neon.cyan)
        T("درآمد: " + money(s.income, currency), size = 13.sp, color = INCOME_COLOR)
        T("هزینه: " + money(s.expense, currency), size = 13.sp, color = EXPENSE_COLOR)
        T("مانده خالص: " + money(s.net, currency), size = 13.sp, bold = true, color = if (s.net < 0) Neon.red else Neon.text)
    }
}

@Composable
private fun CardRow(color: Color, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Neon.surface2, Neon.surface)))
            .border(1.dp, Brush.linearGradient(listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.15f))), shape)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun TxList(
    txs: List<TxEntity>,
    accounts: List<AccountEntity>,
    today: Long,
    currency: String,
    onEdit: (TxEntity) -> Unit,
    onDelete: (TxEntity) -> Unit
) {
    var range by remember { mutableStateOf("month") }
    var kind by remember { mutableStateOf("ALL") }
    val todayD = LocalDate.ofEpochDay(today)
    val (from, to) = when (range) {
        "today" -> today to today
        "week" -> Fin.weekBounds(todayD)
        "month" -> Fin.monthBounds(todayD)
        else -> Long.MIN_VALUE to Long.MAX_VALUE
    }
    val shown = txs.filter { it.date in from..to && (kind == "ALL" || it.type == kind) }
    val sum = Fin.summary(shown, Long.MIN_VALUE, Long.MAX_VALUE)
    val accName = { id: Long -> accounts.firstOrNull { it.id == id }?.name ?: "—" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("today" to "امروز", "week" to "هفته", "month" to "ماه", "all" to "همه").forEach { (k, l) ->
                NeonChip(l, range == k, Neon.cyan, Modifier.weight(1f)) { range = k }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "همه", TxType.INCOME to "درآمد", TxType.EXPENSE to "هزینه", TxType.TRANSFER to "انتقال").forEach { (k, l) ->
                NeonChip(l, kind == k, Neon.lime, Modifier.weight(1f)) { kind = k }
            }
        }
        T(
            "درآمد ${money(sum.income, currency)} · هزینه ${money(sum.expense, currency)}",
            size = 12.sp, color = Neon.muted
        )
        if (shown.isEmpty()) EmptyState("تراکنشی نیست", "از تب «خلاصه» درآمد یا هزینه ثبت کن")
        shown.forEach { t ->
            val c = when (t.type) { TxType.INCOME -> INCOME_COLOR; TxType.EXPENSE -> EXPENSE_COLOR; else -> Neon.cyan }
            CardRow(c, onClick = { onEdit(t) }) {
                Column(Modifier.weight(1f)) {
                    T(if (t.type == TxType.TRANSFER) "انتقال" else Fin.catName(t.category), size = 15.sp, bold = true)
                    T(
                        (if (t.type == TxType.TRANSFER) "${accName(t.accountId)} ← ${accName(t.toAccountId)}" else accName(t.accountId)) +
                            " · " + dateText(Jalali.fromEpochDay(t.date)),
                        size = 11.sp, color = Neon.muted
                    )
                    if (t.note.isNotBlank()) T(t.note, size = 11.sp, color = Neon.muted)
                }
                T(
                    (when (t.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; else -> "" }) + moneyText(t.amount),
                    size = 14.sp, bold = true, color = c
                )
                IconButton(onClick = { onDelete(t) }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Neon.red.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

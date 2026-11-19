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
import androidx.compose.material3.Text
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


/** نسخهٔ نصب‌شدهٔ اپ (از versionName در Gradle) */
@Composable
fun appVersionText(): String {
    val ctx = LocalContext.current
    return remember {
        try {
            val v = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            if (v.isBlank()) "" else "نسخه " + fa(v)
        } catch (e: Exception) {
            ""
        }
    }
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

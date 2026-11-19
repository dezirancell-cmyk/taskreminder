@file:OptIn(ExperimentalFoundationApi::class)

package com.example.taskreminder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------- متن ----------

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Neon.text,
    bold: Boolean = false,
    align: TextAlign? = null,
    decoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        lineHeight = size * 1.55f,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        textDecoration = decoration,
        maxLines = maxLines
    )
}

// ---------- درخشش نئونی ----------

/** درخشش نئونی دور یک مستطیل گرد؛ باید قبل از clip/background در زنجیرهٔ modifier بیاید */
fun Modifier.neonGlow(color: Color, corner: Dp, glow: Dp = 10.dp): Modifier = drawBehind {
    val r = corner.toPx()
    val g = glow.toPx()
    val layers = 4
    for (i in layers downTo 1) {
        val w = g * 2f * i / layers
        drawRoundRect(
            color = color.copy(alpha = 0.04f + 0.035f * (layers - i)),
            cornerRadius = CornerRadius(r + w / 2f),
            style = Stroke(width = w)
        )
    }
}

@Composable
fun NeonIcon(icon: ImageVector, color: Color, size: Dp = 44.dp, iconSize: Dp = 24.dp) {
    Box(
        Modifier
            .size(size)
            .neonGlow(color, size / 2, 6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.2.dp, color.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .neonGlow(Neon.cyan, 16.dp, 6.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Neon.cyan, Neon.magenta)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Neon.bg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        T(text, size = 16.sp, color = Neon.bg, bold = true)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, Neon.muted.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, size = 15.sp, color = Neon.muted)
    }
}

@Composable
fun NeonChip(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.18f) else Neon.surface)
            .border(1.dp, if (selected) color else Neon.muted.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        T(text, size = 13.sp, color = if (selected) color else Neon.muted, bold = selected)
    }
}

// ---------- دیالوگ نئونی ----------

@Composable
fun NeonDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(shape)
                .background(Neon.surface)
                .border(1.dp, Brush.linearGradient(listOf(Neon.cyan, Neon.magenta)), shape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            T(title, size = 18.sp, bold = true)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(confirmText, Modifier.weight(1f), onClick = onConfirm)
                GhostButton("انصراف", onClick = onDismiss)
            }
        }
    }
}

// ---------- انتخابگر چرخشی (مثل تصویر مرجع) ----------

@Composable
fun WheelPicker(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 46.dp,
    visibleCount: Int = 5,
    accent: Color = Neon.cyan
) {
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, labels.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val live by remember {
        derivedStateOf { state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx }
    }
    val report by rememberUpdatedState(onSelected)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect {
                val extra = if (state.firstVisibleItemScrollOffset > itemPx / 2f) 1 else 0
                report((state.firstVisibleItemIndex + extra).coerceIn(0, labels.lastIndex))
            }
    }

    Box(modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        // نوار انتخاب وسط
        val hl = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(hl)
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.6f), hl)
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.fillMaxWidth().height(itemHeight * visibleCount)
        ) {
            items(labels.size) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val dist = abs(i - live)
                            alpha = (1f - 0.42f * dist).coerceIn(0.14f, 1f)
                            val s = (1f - 0.1f * dist).coerceIn(0.72f, 1f)
                            scaleX = s
                            scaleY = s
                        }
                        .clickable { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    T(labels[i], size = 21.sp, color = Neon.text, bold = true, maxLines = 1)
                }
            }
        }
    }
}

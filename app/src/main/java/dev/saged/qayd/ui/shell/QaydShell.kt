package dev.saged.qayd.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.saged.qayd.data.Account
import dev.saged.qayd.data.Shop
import dev.saged.qayd.data.SyncState
import dev.saged.qayd.ui.components.Ambient
import dev.saged.qayd.ui.home.HomeSummary
import dev.saged.qayd.ui.home.PsSlot
import dev.saged.qayd.ui.components.QIcon
import dev.saged.qayd.ui.components.Silk
import dev.saged.qayd.ui.components.T
import dev.saged.qayd.ui.components.pressScale
import dev.saged.qayd.ui.home.HomeScreen
import dev.saged.qayd.ui.icons.QIcons
import dev.saged.qayd.ui.theme.LocalPalette

enum class Tab(val label: String, val icon: ImageVector) {
    Home("الرئيسية", QIcons.Home),
    Customers("الزبائن", QIcons.Users),
    Messages("الرسائل", QIcons.Msg),
    More("المزيد", QIcons.More),
}

@Composable
fun QaydShell(
    shop: Shop,
    account: Account,
    sync: SyncState,
    summary: HomeSummary,
    slots: List<PsSlot>,
    more: @Composable (PaddingValues) -> Unit,
) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val top = WindowInsets.statusBars.asPaddingValues()

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Ambient()
        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn(tween(220, easing = Silk)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                val pad = PaddingValues(top = top.calculateTopPadding())
                when (t) {
                    Tab.Home -> HomeScreen(pad, shop, account, sync, summary, slots)
                    Tab.Customers -> EmptyTab(pad, QIcons.Users, "لا يوجد زبائن بعد", "أضف أول زبون من زر الإضافة، وسيظهر رصيده وسجله هنا.")
                    Tab.Messages -> EmptyTab(pad, QIcons.Msg, "طابور الرسائل فارغ", "كل رسالة تُرسل لمدين أو مشترك تظهر هنا بحالتها: بالانتظار، أُرسلت، وصلت.")
                    Tab.More -> more(pad)
                }
            }
            BottomBar(tab) { tab = it }
        }
        Fab(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().offset(y = (-34).dp))
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.surface)
            .drawWithContent {
                drawContent()
                drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        val items = Tab.entries
        items.forEachIndexed { i, t ->
            if (i == 2) Spacer(Modifier.weight(1f))
            val selected = t == current
            val c = if (selected) p.pri else p.mute
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Tab) {
                        if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(t)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QIcon(t.icon, c, Modifier.size(22.dp), t.label)
                T(t.label, Modifier.padding(top = 4.dp), 11.sp, if (selected) FontWeight.Medium else FontWeight.Normal, c)
            }
        }
    }
}

@Composable
private fun Fab(modifier: Modifier) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .pressScale(source, .9f)
            .shadow(18.dp, shape, ambientColor = p.pri, spotColor = p.pri)
            .size(60.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(p.pri, lerp(p.pri, Color.Black, .4f))))
            .clickable(source, indication = null, role = Role.Button, onClickLabel = "حركة جديدة") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        contentAlignment = Alignment.Center,
    ) {
        QIcon(QIcons.Plus, p.onPri, Modifier.size(26.dp), "حركة جديدة")
    }
}

@Composable
private fun EmptyTab(padding: PaddingValues, icon: ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(p.surface2),
            contentAlignment = Alignment.Center,
        ) { QIcon(icon, p.pri, Modifier.size(32.dp)) }
        Spacer(Modifier.height(18.dp))
        T(title, size = 18.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        T(body, size = 13.5.sp, color = p.mute, maxLines = 3)
    }
}

package com.ntplayone.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Bg = Color(0xFF070A18)
val BgDeep = Color(0xFF04050F)
val CardBg = Color(0xE6111733)
val CardBg2 = Color(0xFF1A2049)
val Line = Color(0xFF2C3468)
val Cyan = Color(0xFF00E5FF)
val Blue = Color(0xFF3B82F6)
val Violet = Color(0xFF7C4DFF)
val Pink = Color(0xFFFF2D9B)
val PinkLight = Color(0xFFFF7AC2)
val Amber = Color(0xFFFFB020)
val TextHi = Color(0xFFF8FAFF)
val TextLo = Color(0xFF9AA3C7)
val Accent = Brush.horizontalGradient(listOf(Cyan, Blue, Violet, Pink))
val Glass = Color(0xB30E1330)

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1130), BgDeep, Color(0xFF120A26))))
            val w = size.width
            val h = size.height
            val r1 = size.minDimension * 0.85f
            drawCircle(
                Brush.radialGradient(listOf(Cyan.copy(alpha = 0.18f), Color.Transparent), center = Offset(w * 0.1f, 0f), radius = r1),
                radius = r1, center = Offset(w * 0.1f, 0f),
            )
            val r2 = size.minDimension * 0.7f
            drawCircle(
                Brush.radialGradient(listOf(Pink.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * 0.92f, h * 0.08f), radius = r2),
                radius = r2, center = Offset(w * 0.92f, h * 0.08f),
            )
            val r3 = size.minDimension * 0.9f
            drawCircle(
                Brush.radialGradient(listOf(Violet.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * 0.5f, h * 1.05f), radius = r3),
                radius = r3, center = Offset(w * 0.5f, h * 1.05f),
            )
        },
    ) { content() }
}

@Composable
fun FocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    bg: Brush = SolidColor(CardBg2),
    contentAlignment: Alignment = Alignment.Center,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focus")
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .clip(shape)
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Accent, shape)
                else Modifier.border(1.dp, Line.copy(alpha = 0.7f), shape)
            ),
        contentAlignment = contentAlignment,
    ) { content() }
}

@Composable
fun NpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    FocusBox(
        onClick,
        modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        bg = if (primary) Accent else SolidColor(Glass),
        focusRequester = focusRequester,
    ) {
        Text(
            text,
            color = if (primary) Color(0xFF060A1A) else TextHi,
            fontSize = 15.sp,
            fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

@Composable
fun NpHeader(trailing: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "NTPlayone",
                style = TextStyle(brush = Accent, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
            )
            Text("SEU PLAYER IPTV", color = TextLo, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.width(28.dp))
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            FocusBox(onAction, Modifier.height(34.dp), shape = RoundedCornerShape(10.dp), bg = SolidColor(Glass)) {
                Text(action, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}

@Composable
fun PosterCard(
    item: Item,
    width: Dp,
    onClick: () -> Unit,
    cover: String = item.icon,
    onCoverError: (() -> Unit)? = null,
) {
    val imgH = if (item.type == "live") width * 0.62f else width * 1.42f
    FocusBox(onClick, Modifier.width(width), shape = RoundedCornerShape(14.dp), bg = SolidColor(CardBg), contentAlignment = Alignment.Center) {
        Box(Modifier.width(width).height(imgH)) {
            if (cover.isNotEmpty()) {
                AsyncImage(
                    model = cover,
                    contentDescription = item.name,
                    modifier = if (item.type == "live") Modifier.fillMaxSize().padding(10.dp) else Modifier.fillMaxSize(),
                    contentScale = if (item.type == "live") ContentScale.Fit else ContentScale.Crop,
                    onError = { onCoverError?.invoke() },
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(CardBg2, Color(0xFF241B4D), CardBg2))
                    )
                ) {
                    Text(
                        item.name.take(1).uppercase(),
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x99040712), Color(0xF2040712)))
                )
            )
            if (item.type == "live") {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Pink)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("AO VIVO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    seriesDisplayName(item),
                    color = TextHi,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (item.type) {
                        "movie" -> "FILME"
                        "series" -> "SÉRIE"
                        else -> "CANAL"
                    },
                    color = Cyan,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

@Composable
fun SeriesPoster(app: AppModel, item: Item, width: Dp, onClick: () -> Unit) {
    val cover = if (item.type == "series") app.seriesCover(item.name).ifBlank { item.icon } else item.icon
    LaunchedEffect(item.key, cover) {
        if (item.type == "series" && cover.isBlank()) app.ensureSeriesCover(item.name)
    }
    PosterCard(
        item, width, onClick,
        cover = cover,
        onCoverError = { if (item.type == "series") app.ensureSeriesCover(item.name) },
    )
}

@Composable
fun QrCard(title: String, subtitle: String, detail: String, url: String) {
    val bmp = remember(url) { if (url.isNotEmpty()) Qr.bitmap(url) else null }
    val shape = RoundedCornerShape(24.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(300.dp)
            .clip(shape)
            .background(Glass)
            .border(1.dp, Brush.linearGradient(listOf(Cyan.copy(alpha = 0.7f), Violet.copy(alpha = 0.5f), Pink.copy(alpha = 0.7f))), shape)
            .padding(20.dp),
    ) {
        Text(title, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        if (bmp != null) {
            Box(Modifier.size(190.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp)) {
                Image(bmp.asImageBitmap(), contentDescription = title, modifier = Modifier.fillMaxSize())
            }
        } else {
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                Text("Servidor indisponível", color = TextLo, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(subtitle, color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(detail, color = TextLo, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
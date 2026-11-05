package com.stupidtree.hita.ui.anime.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stupidtree.hita.ui.anime.data.CoverLoader
import com.stupidtree.hita.ui.anime.data.ImageSet
import com.stupidtree.hita.ui.anime.data.SubjectSummary

@Composable
fun AsyncCover(
    url: String?,
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = Brush.verticalGradient(
        listOf(
            MaterialTheme.colors.primary.copy(alpha = 0.22f),
            MaterialTheme.colors.secondary.copy(alpha = 0.18f),
        ),
    )
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(placeholder),
    ) {
        // 按实际显示尺寸取图，避免「400px 封面拉到 540px」的糊图。
        val density = LocalDensity.current
        val targetPx = with(density) { maxWidth.toPx().toInt() }.coerceIn(160, 1200)
        var bitmap by remember(url, targetPx) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(url, targetPx) {
            bitmap = CoverLoader.load(url, maxWidth = targetPx)
        }
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = remember(loaded) { loaded.asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}

@Composable
fun ScorePill(score: Double?, modifier: Modifier = Modifier) {
    if (score == null || score <= 0) return
    Surface(
        color = MaterialTheme.colors.secondary.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = "★ %.1f".format(score),
            color = MaterialTheme.colors.secondaryVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun TagPill(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        color = if (selected) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.onSurface.copy(alpha = 0.07f)
        },
        shape = RoundedCornerShape(50),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun HorizontalChips(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (onBack == null) 16.dp else 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "返回",
                        tint = MaterialTheme.colors.onBackground,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colors.secondary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 12.sp,
                color = MaterialTheme.colors.primary,
                modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier,
            )
        }
    }
}

@Composable
fun LoadingState(message: String = "加载中…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun EmptyState(message: String, detail: String? = null) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("(´･_･`)", fontSize = 28.sp, color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f))
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.body1)
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.body1, color = MaterialTheme.colors.error)
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colors.primary,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable(onClick = onRetry),
                ) {
                    Text(
                        "重试",
                        color = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectRow(
    subject: SubjectSummary,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncCover(subject.images.common ?: subject.images.cover, Modifier.size(width = 62.dp, height = 84.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                subject.displayName,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subject.nameCn != null && subject.nameCn.isNotBlank()) {
                Text(
                    subject.name,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScorePill(subject.score)
                val extra = subtitle ?: subject.airDate
                if (!extra.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        extra,
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectGridCard(subject: SubjectSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        AsyncCover(
            subject.images.hero ?: subject.images.cover,
            Modifier
                .fillMaxWidth()
                .height(196.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subject.displayName,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScorePill(subject.score)
            if (subject.airDate != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    subject.airDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        color = if (enabled) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                fontSize = 13.sp,
                color = if (enabled) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
            )
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(
            key,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(84.dp),
        )
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

fun coverOf(images: ImageSet): String? = images.cover

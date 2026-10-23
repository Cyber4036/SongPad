package com.songpad.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.songpad.core.ui.components.RecordButton
import com.songpad.core.ui.icons.SongPadIcons
import com.songpad.core.ui.theme.Dimens
import com.songpad.core.ui.theme.Radii
import com.songpad.core.ui.theme.SongPadTheme

/** Phase 0 §3: Songs · Record (centered, protruding 70.dp) · Nudges (with unread badge). */
@Composable
fun SongPadBottomBar(
    current: NavKey,
    onSongs: () -> Unit,
    onRecord: () -> Unit,
    onNudges: () -> Unit,
    unreadNudgeCount: Int = 0,
) {
    val c = SongPadTheme.colors
    Box(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        HorizontalDivider(color = c.divider, thickness = 1.dp, modifier = Modifier.align(Alignment.TopCenter))
        Row(
            modifier = Modifier.fillMaxWidth().height(66.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab(
                label = "Songs",
                icon = SongPadIcons.MusicNote,
                selected = current is LibraryKey,
                badgeCount = 0,
                onClick = onSongs,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.weight(1f))
            Tab(
                label = "Nudges",
                icon = SongPadIcons.Bell,
                selected = current is NudgesKey,
                badgeCount = unreadNudgeCount,
                onClick = onNudges,
                modifier = Modifier.weight(1f),
            )
        }
        RecordButton(
            isRecording = false,
            onClick = onRecord,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-18).dp),
            size = Dimens.recordFab,
            haloWidth = 5.dp,
        )
    }
}

@Composable
private fun Tab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = SongPadTheme.colors
    val tint = if (selected) c.accent else c.textTertiary
    Column(
        modifier = modifier
            .sizeIn(minHeight = Dimens.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(c.accent)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 12.sp,
                        ),
                        color = c.onAccent,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = tint,
        )
    }
}

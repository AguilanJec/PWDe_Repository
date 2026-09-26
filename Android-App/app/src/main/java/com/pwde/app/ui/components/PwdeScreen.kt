package com.pwde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pwde.app.ui.theme.MinTouchTarget
import com.pwde.app.ui.theme.PwdeShapes
import com.pwde.app.ui.theme.PwdeTheme

/**
 * Standard PWDe screen: header, scrollable content, docked voice bar, footer actions, optional
 * bottom nav. In Easy reach layout mode the content is pushed to the lower half of the screen.
 */
@Composable
fun PwdeScreen(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    voiceHint: String? = null,
    footer: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PwdeTheme.colors
    val spacing = PwdeTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(screenInsets(hasBottomBar = bottomBar != null)),
    ) {
        if (title != null) ScreenHeader(title, subtitle, onBack)
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = if (spacing.easyReach) Alignment.BottomCenter else Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = spacing.screenMargin, vertical = spacing.itemGap),
                verticalArrangement = Arrangement.spacedBy(spacing.itemGap),
                content = content,
            )
        }
        if (voiceHint != null || footer != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenMargin, vertical = spacing.itemGap),
                verticalArrangement = Arrangement.spacedBy(spacing.itemGap),
            ) {
                if (voiceHint != null) VoiceHintBar(voiceHint)
                footer?.invoke()
            }
        }
        if (bottomBar != null) bottomBar()
    }
}

/** All safe-drawing insets, except the bottom when a nav bar pads for it itself. */
@Composable
private fun screenInsets(hasBottomBar: Boolean): WindowInsets =
    if (hasBottomBar) WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    else WindowInsets.safeDrawing

@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: (() -> Unit)?) {
    val colors = PwdeTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else PwdeTheme.spacing.screenMargin, end = PwdeTheme.spacing.screenMargin, top = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.primary)
            }
        }
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
}

enum class MainTab(val label: String, val icon: ImageVector) {
    PLAY("Play", Icons.Outlined.SportsEsports),
    GAMES("Games", Icons.Outlined.GridView),
    GABAI("GabAI", Icons.Outlined.AutoAwesome),
    PROFILE("Profile", Icons.Outlined.Person),
}

/** Labelled bottom navigation (Figma "P3 / Nav Bar"). */
@Composable
fun PwdeBottomNav(current: MainTab, onSelect: (MainTab) -> Unit) {
    val colors = PwdeTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.navBrush)
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .selectableGroup(),
    ) {
        MainTab.entries.forEach { tab ->
            val selected = tab == current
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(PwdeShapes.button)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .clip(PwdeShapes.pill)
                        .background(if (selected) colors.primary else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (selected) colors.onAccent else colors.text)
                }
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.primary else colors.text,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Section title inside a screen. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = PwdeTheme.colors.text,
        modifier = modifier.padding(top = 4.dp).semantics { heading() },
    )
}

/** Tappable text link with a 48dp touch target. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = MinTouchTarget).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = PwdeTheme.colors.primary)
    }
}

package com.shohan.bokeya.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shohan.bokeya.BuildConfig
import com.shohan.bokeya.R
import com.shohan.bokeya.ui.components.BokeyaCard
import com.shohan.bokeya.ui.components.BokeyaTopBar
import com.shohan.bokeya.ui.navigation.Routes

/** The "আরও" tab: an index of everything that isn't a daily-use screen. */
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Scaffold(
        topBar = { BokeyaTopBar(title = stringResource(R.string.more_title)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MoreItem(
                icon = Icons.Outlined.Search,
                label = stringResource(R.string.more_search),
                onClick = { onNavigate(Routes.SEARCH) },
            )
            MoreItem(
                icon = Icons.Outlined.History,
                label = stringResource(R.string.more_history),
                onClick = { onNavigate(Routes.HISTORY) },
            )
            MoreItem(
                icon = Icons.Outlined.Insights,
                label = stringResource(R.string.more_insights),
                onClick = { onNavigate(Routes.INSIGHTS) },
            )
            MoreItem(
                icon = Icons.Outlined.Assessment,
                label = stringResource(R.string.more_reports),
                onClick = { onNavigate(Routes.REPORTS) },
            )
            MoreItem(
                icon = Icons.Outlined.NotificationsActive,
                label = stringResource(R.string.more_reminders),
                onClick = { onNavigate(Routes.REMINDERS) },
            )
            MoreItem(
                icon = Icons.Outlined.Backup,
                label = stringResource(R.string.more_backup),
                onClick = { onNavigate(Routes.BACKUP) },
            )
            MoreItem(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.more_settings),
                onClick = { onNavigate(Routes.SETTINGS) },
            )
            MoreItem(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.more_about),
                onClick = { onNavigate(Routes.ABOUT) },
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    BokeyaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

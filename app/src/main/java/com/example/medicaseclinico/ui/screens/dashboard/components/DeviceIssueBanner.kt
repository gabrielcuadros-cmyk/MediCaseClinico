package com.example.medicaseclinico.ui.screens.dashboard.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.DeviceIssue
import com.example.medicaseclinico.ui.theme.MediCaseStatusColors
import com.example.medicaseclinico.ui.theme.MediCaseTheme

@Composable
fun DeviceIssueBanner(
    issue: DeviceIssue,
    permissionsPermanentlyDenied: Boolean,
    onAction: (DeviceIssue) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = issue.bannerStyle(MediCaseTheme.statusColors, permissionsPermanentlyDenied)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(14.dp),
        color = style.containerColor,
        border = BorderStroke(1.dp, style.accentColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(style.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(style.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            style.actionRes?.let { actionRes ->
                Spacer(modifier = Modifier.padding(top = 10.dp))
                Button(
                    onClick = { onAction(issue) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(actionRes), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class IssueBannerStyle(
    val icon: ImageVector,
    @get:StringRes val titleRes: Int,
    @get:StringRes val messageRes: Int,
    @get:StringRes val actionRes: Int?,
    val accentColor: Color,
    val containerColor: Color
)

private fun DeviceIssue.bannerStyle(
    colors: MediCaseStatusColors,
    permissionsPermanentlyDenied: Boolean
): IssueBannerStyle = when (this) {
    DeviceIssue.PERMISSIONS_MISSING -> IssueBannerStyle(
        icon = Icons.Outlined.PrivacyTip,
        titleRes = R.string.issue_permissions_title,
        messageRes = if (permissionsPermanentlyDenied) {
            R.string.issue_permissions_denied_message
        } else {
            R.string.issue_permissions_message
        },
        actionRes = if (permissionsPermanentlyDenied) {
            R.string.issue_action_open_settings
        } else {
            R.string.issue_action_grant_permissions
        },
        accentColor = colors.warning,
        containerColor = colors.warningContainer
    )
    DeviceIssue.BLUETOOTH_DISABLED -> IssueBannerStyle(
        icon = Icons.Filled.BluetoothDisabled,
        titleRes = R.string.issue_bluetooth_off_title,
        messageRes = R.string.issue_bluetooth_off_message,
        actionRes = R.string.issue_action_enable_bluetooth,
        accentColor = colors.warning,
        containerColor = colors.warningContainer
    )
    DeviceIssue.DEVICE_NOT_FOUND -> IssueBannerStyle(
        icon = Icons.Outlined.SearchOff,
        titleRes = R.string.issue_not_found_title,
        messageRes = R.string.issue_not_found_message,
        actionRes = R.string.issue_action_search_again,
        accentColor = colors.warning,
        containerColor = colors.warningContainer
    )
    DeviceIssue.CONNECTION_LOST -> IssueBannerStyle(
        icon = Icons.Outlined.LinkOff,
        titleRes = R.string.issue_connection_lost_title,
        messageRes = R.string.issue_connection_lost_message,
        actionRes = R.string.issue_action_reconnect,
        accentColor = colors.critical,
        containerColor = colors.criticalContainer
    )
    DeviceIssue.BLE_NOT_SUPPORTED -> IssueBannerStyle(
        icon = Icons.Outlined.ErrorOutline,
        titleRes = R.string.issue_not_supported_title,
        messageRes = R.string.issue_not_supported_message,
        actionRes = null,
        accentColor = colors.critical,
        containerColor = colors.criticalContainer
    )
}

@Preview(showBackground = true, name = "Aviso - Bluetooth apagado")
@Composable
private fun DeviceIssueBannerPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DeviceIssueBanner(
                issue = DeviceIssue.BLUETOOTH_DISABLED,
                permissionsPermanentlyDenied = false,
                onAction = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
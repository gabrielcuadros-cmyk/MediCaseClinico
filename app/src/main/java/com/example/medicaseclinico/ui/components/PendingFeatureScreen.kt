package com.example.medicaseclinico.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R

@Composable
fun PendingFeatureScreen(
    @StringRes titleRes: Int,
    modifier: Modifier = Modifier,
    @StringRes actionLabelRes: Int? = null,
    onAction: (() -> Unit)? = null,
    @StringRes secondaryActionLabelRes: Int? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.pending_feature_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (actionLabelRes != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(text = stringResource(actionLabelRes))
            }
        }
        if (secondaryActionLabelRes != null && onSecondaryAction != null) {
            OutlinedButton(
                onClick = onSecondaryAction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(text = stringResource(secondaryActionLabelRes))
            }
        }
    }
}
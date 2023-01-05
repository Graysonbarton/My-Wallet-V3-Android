package com.blockchain.presentation.backup.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.blockchain.componentlib.basic.Image
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.presentation.R
import com.blockchain.presentation.backup.BackUpStatus

@Composable
fun BackupStatus(backupStatus: BackUpStatus) {
    Row(
        modifier = Modifier
            .background(
                color = backupStatus.bgColor,
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.tiny_spacing))
            )
            .padding(
                horizontal = dimensionResource(id = R.dimen.very_small_spacing),
                vertical = dimensionResource(id = R.dimen.tiny_spacing)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageResource = ImageResource.Local(
                id = backupStatus.icon,
                size = dimensionResource(R.dimen.size_standard)
            )
        )

        Spacer(modifier = Modifier.size(dimensionResource(R.dimen.tiny_spacing)))

        Text(
            text = stringResource(backupStatus.text),
            style = AppTheme.typography.paragraph2,
            color = backupStatus.textColor,
        )
    }
}

// ///////////////
// PREVIEWS
// ///////////////

@Preview(name = "Backup Status No Backup")
@Composable
fun PreviewBackupStatusNoBackup() {
    BackupStatus(BackUpStatus.NO_BACKUP)
}

@Preview(name = "Backup Status Backed up")
@Composable
fun PreviewBackupStatusBackup() {
    BackupStatus(BackUpStatus.BACKED_UP)
}

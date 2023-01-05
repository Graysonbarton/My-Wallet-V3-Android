package com.blockchain.presentation.backup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.blockchain.commonarch.presentation.mvi_v2.ViewState
import com.blockchain.componentlib.theme.Green300
import com.blockchain.componentlib.theme.Grey900
import com.blockchain.componentlib.theme.Orange400
import com.blockchain.presentation.R

const val TOTAL_STEP_COUNT = 2

data class BackupPhraseViewState(
    val showSkipBackup: Boolean,
    val showLoading: Boolean,
    val showError: Boolean,
    val mnemonic: List<String>,
    val backUpStatus: BackUpStatus,
    val copyState: CopyState,
    val mnemonicVerificationStatus: UserMnemonicVerificationStatus,
    val flowState: FlowState
) : ViewState

enum class BackUpStatus(
    @DrawableRes val icon: Int,
    val bgColor: Color,
    val textColor: Color,
    @StringRes val text: Int
) {
    NO_BACKUP(
        icon = R.drawable.alert_on,
        bgColor = Orange400,
        textColor = Grey900,
        text = R.string.back_up_status_negative
    ),

    BACKED_UP(
        icon = R.drawable.check_on,
        bgColor = Green300,
        textColor = Grey900,
        text = R.string.back_up_status_positive
    )
}

sealed interface CopyState {
    data class Idle(val resetClipboard: Boolean) : CopyState
    object Copied : CopyState
}

enum class UserMnemonicVerificationStatus {
    IDLE, INCORRECT
}

sealed interface FlowState {
    object InProgress : FlowState
    data class Ended(val isSuccessful: Boolean) : FlowState
}

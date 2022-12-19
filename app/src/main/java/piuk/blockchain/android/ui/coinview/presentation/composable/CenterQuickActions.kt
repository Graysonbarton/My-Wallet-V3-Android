package piuk.blockchain.android.ui.coinview.presentation.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.button.ButtonState
import com.blockchain.componentlib.button.PrimaryButton
import com.blockchain.componentlib.button.TertiaryButton
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.utils.value
import piuk.blockchain.android.ui.coinview.presentation.CoinviewCenterQuickActionsState
import piuk.blockchain.android.ui.coinview.presentation.CoinviewQuickActionState

@Composable
fun CenterQuickActions(
    data: CoinviewCenterQuickActionsState,
    onQuickActionClick: (CoinviewQuickActionState) -> Unit
) {
    when (data) {
        CoinviewCenterQuickActionsState.NotSupported -> {
            Empty()
        }

        CoinviewCenterQuickActionsState.Loading -> {
            Empty()
        }

        is CoinviewCenterQuickActionsState.Data -> {
            CenterQuickActionsData(
                data = data,
                onQuickActionClick = onQuickActionClick
            )
        }
    }
}

@Composable
fun CenterQuickActionsData(
    data: CoinviewCenterQuickActionsState.Data,
    onQuickActionClick: (CoinviewQuickActionState) -> Unit
) {
    if (data.center !is CoinviewQuickActionState.None) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.dimensions.standardSpacing)
        ) {

            TertiaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = data.center.name.value(),
                textColor = AppTheme.colors.title,
                icon = ImageResource.Local(
                    data.center.logo.value,
                    colorFilter = ColorFilter.tint(AppTheme.colors.title),
                    size = AppTheme.dimensions.standardSpacing
                ),
                state = if (data.center.enabled) ButtonState.Enabled else ButtonState.Disabled,
                onClick = { onQuickActionClick(data.center) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewCenterQuickActions_Data_Enabled() {
    CenterQuickActions(
        CoinviewCenterQuickActionsState.Data(
            center = CoinviewQuickActionState.Swap(true)
        ),
        {}
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewCenterQuickActions_Data_Disabled() {
    CenterQuickActions(
        CoinviewCenterQuickActionsState.Data(
            center = CoinviewQuickActionState.Swap(false)
        ),
        {}
    )
}

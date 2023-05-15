package piuk.blockchain.android.ui.interest.presentation.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.blockchain.presentation.customviews.EmptyStateView
import piuk.blockchain.android.R
import piuk.blockchain.android.support.SupportCentreActivity

@Composable
fun InterestDashboardError(action: () -> Unit) {
    AndroidView(
        factory = { context ->
            EmptyStateView(context).apply {
                setDetails(
                    title = com.blockchain.stringResources.R.string.rewards_error_title,
                    description = com.blockchain.stringResources.R.string.rewards_error_desc,
                    contactSupportEnabled = true,
                    action = action,
                    onContactSupport = { context.startActivity(SupportCentreActivity.newIntent(context)) }
                )
            }
        }
    )
}

@Preview
@Composable
private fun PreviewInterestDashboardError() {
    InterestDashboardError {}
}

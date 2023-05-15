package piuk.blockchain.android.ui.interest.presentation.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.customviews.ButtonOptions
import piuk.blockchain.android.ui.customviews.VerifyIdentityBenefitsView
import piuk.blockchain.android.ui.customviews.VerifyIdentityNumericBenefitItem

@Composable
fun InterestDashboardVerificationItem(verificationClicked: () -> Unit) {
    AndroidView(
        factory = { context ->
            VerifyIdentityBenefitsView(context).apply {
                initWithBenefits(
                    benefits = listOf(
                        VerifyIdentityNumericBenefitItem(
                            context.getString(
                                com.blockchain.stringResources.R.string.rewards_dashboard_verify_point_one_title
                            ),
                            context.getString(
                                com.blockchain.stringResources.R.string.rewards_dashboard_verify_point_one_label
                            )
                        ),
                        VerifyIdentityNumericBenefitItem(
                            context.getString(
                                com.blockchain.stringResources.R.string.rewards_dashboard_verify_point_two_title
                            ),
                            context.getString(
                                com.blockchain.stringResources.R.string.rewards_dashboard_verify_point_two_label
                            )
                        ),
                        VerifyIdentityNumericBenefitItem(
                            context.getString(
                                com.blockchain.stringResources.R.string.rewards_dashboard_verify_point_three_title
                            ),
                            context.getString(
                                com.blockchain.stringResources.R.string.rewards_dashboard_verify_point_three_label
                            )
                        )
                    ),
                    title = context.getString(com.blockchain.stringResources.R.string.rewards_dashboard_verify_title),
                    description = context.getString(
                        com.blockchain.stringResources.R.string.rewards_dashboard_verify_label
                    ),
                    icon = com.blockchain.earn.R.drawable.ic_interest_blue_circle,
                    primaryButton = ButtonOptions(true, cta = verificationClicked),
                    secondaryButton = ButtonOptions(false),
                    showSheetIndicator = false
                )
            }
        }
    )
}

@Preview
@Composable
fun PreviewInterestDashboardVerificationItem() {
    InterestDashboardVerificationItem {}
}

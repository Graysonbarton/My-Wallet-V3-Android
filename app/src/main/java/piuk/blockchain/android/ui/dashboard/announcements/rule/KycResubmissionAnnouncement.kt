package piuk.blockchain.android.ui.dashboard.announcements.rule

import androidx.annotation.VisibleForTesting
import com.blockchain.core.kyc.domain.KycService
import com.blockchain.walletmode.WalletMode
import io.reactivex.rxjava3.core.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.campaign.CampaignType
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementHost
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementRule
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.dashboard.announcements.DismissRule
import piuk.blockchain.android.ui.dashboard.announcements.StandardAnnouncementCard

internal class KycResubmissionAnnouncement(
    private val kycService: KycService,
    dismissRecorder: DismissRecorder
) : AnnouncementRule(dismissRecorder) {

    override val dismissKey =
        DISMISS_KEY

    override fun shouldShow(): Single<Boolean> {
        if (dismissEntry.isDismissed) {
            return Single.just(false)
        }

        return kycService.isResubmissionRequired()
    }

    override val associatedWalletModes: List<WalletMode>
        get() = listOf(WalletMode.CUSTODIAL)

    override fun show(host: AnnouncementHost) {

        val card = StandardAnnouncementCard(
            name = name,
            titleText = R.string.kyc_resubmission_card_title,
            bodyText = R.string.kyc_resubmission_card_description,
            ctaText = R.string.kyc_resubmission_card_button,
            iconImage = R.drawable.ic_announce_kyc,
            dismissFunction = {
                host.dismissAnnouncementCard()
            },
            ctaFunction = {
                host.dismissAnnouncementCard()
                host.startKyc(CampaignType.Resubmission)
            },
            dismissEntry = dismissEntry,
            dismissRule = DismissRule.CardOneTime
        )
        host.showAnnouncementCard(card)
    }

    override val name = "kyc_resubmit"

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val DISMISS_KEY = "KYC_RESUBMISSION_DISMISSED"
    }
}

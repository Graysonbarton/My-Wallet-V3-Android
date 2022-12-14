package piuk.blockchain.android.ui.recurringbuy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.blockchain.analytics.events.LaunchOrigin
import com.blockchain.commonarch.presentation.mvi.MviFragment
import com.blockchain.core.recurringbuy.domain.RecurringBuyFrequency
import com.blockchain.core.recurringbuy.domain.RecurringBuyState
import com.blockchain.presentation.koin.scopedInject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentRecurringbuyFirstTimeBinding
import piuk.blockchain.android.simplebuy.SimpleBuyIntent
import piuk.blockchain.android.simplebuy.SimpleBuyModel
import piuk.blockchain.android.simplebuy.SimpleBuyNavigator
import piuk.blockchain.android.simplebuy.SimpleBuyScreen
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.ui.recurringbuy.onboarding.RecurringBuyOnboardingActivity

class RecurringBuyFirstTimeBuyerFragment :
    MviFragment<SimpleBuyModel, SimpleBuyIntent, SimpleBuyState, FragmentRecurringbuyFirstTimeBinding>(),
    RecurringBuySelectionBottomSheet.Host,
    SimpleBuyScreen {

    override val model: SimpleBuyModel by scopedInject()

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentRecurringbuyFirstTimeBinding =
        FragmentRecurringbuyFirstTimeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.updateToolbar(getString(R.string.recurring_buy_first_time_toolbar))

        binding.apply {
            skipBtn.setOnClickListener {
                analytics.logEvent(RecurringBuyAnalytics.RecurringBuySuggestionSkipped(LaunchOrigin.BUY_CONFIRMATION))
                getActivity()?.finish()
            }
            learnMoreBtn.setOnClickListener {
                startActivity(
                    RecurringBuyOnboardingActivity.newInstance(requireContext(), false)
                )
            }
        }
    }

    override fun render(state: SimpleBuyState) {
        binding.apply {
            with(state) {
                subtitleSetupRbs.text = getString(
                    R.string.recurring_buy_first_time_info,
                    order.amount?.formatOrSymbolForZero(),
                    orderValue?.currency?.name
                )
                getStartedBtn.setOnClickListener {
                    analytics.logEvent(RecurringBuyAnalytics.RecurringBuyClicked(LaunchOrigin.BUY_CONFIRMATION))

                    showBottomSheet(
                        RecurringBuySelectionBottomSheet.newInstance(
                            order.amount,
                            orderValue?.currencyCode
                        )
                    )
                }
            }
        }
        if (state.recurringBuyState == RecurringBuyState.ACTIVE) {
            navigator().goToFirstRecurringBuyCreated()
        }
    }

    override fun onIntervalSelected(interval: RecurringBuyFrequency) {
        model.process(SimpleBuyIntent.CreateRecurringBuy(interval))
    }

    override fun navigator(): SimpleBuyNavigator =
        (activity as? SimpleBuyNavigator) ?: throw IllegalStateException(
            "Parent must implement SimpleBuyNavigator"
        )

    override fun onSheetClosed() {}
}

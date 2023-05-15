package piuk.blockchain.android.ui.kyc.moreinfo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.blockchain.analytics.data.logEvent
import com.blockchain.analytics.events.AnalyticsEvents
import com.blockchain.componentlib.viewextensions.inflate
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.kyc.ParentActivityDelegate
import piuk.blockchain.android.ui.kyc.navhost.KycProgressListener
import piuk.blockchain.android.ui.kyc.navigate
import piuk.blockchain.android.util.throttledClicks
import timber.log.Timber

class KycMoreInfoSplashFragment : Fragment() {

    private val progressListener: KycProgressListener by ParentActivityDelegate(
        this
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = container?.inflate(R.layout.fragment_kyc_more_info_splash)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logEvent(AnalyticsEvents.KycMoreInfo)

        progressListener.setupHostToolbar(com.blockchain.stringResources.R.string.kyc_more_info_splash_title)
    }

    private val disposable = CompositeDisposable()

    override fun onResume() {
        super.onResume()
        disposable += requireView().findViewById<View>(R.id.button_kyc_more_info_splash_next)
            .throttledClicks()
            .subscribeBy(
                onNext = {
                    navigate(
                        KycMoreInfoSplashFragmentDirections.actionKycMoreInfoSplashFragmentToMobileVerification(
                            KycMoreInfoSplashFragmentArgs.fromBundle(arguments ?: Bundle()).countryCode
                        )
                    )
                },
                onError = { Timber.e(it) }
            )
    }

    override fun onPause() {
        disposable.clear()
        super.onPause()
    }
}

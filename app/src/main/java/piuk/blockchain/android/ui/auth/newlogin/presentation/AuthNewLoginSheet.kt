package piuk.blockchain.android.ui.auth.newlogin.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.commonarch.presentation.base.SlidingModalBottomDialog
import com.blockchain.commonarch.presentation.mvi.MviBottomSheet
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.presentation.customviews.BlockchainListDividerDecor
import com.blockchain.presentation.koin.scopedInject
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import piuk.blockchain.android.databinding.AuthNewLoginSheetBinding
import piuk.blockchain.android.ui.auth.newlogin.AuthNewLoginBrowserInfo
import piuk.blockchain.android.ui.auth.newlogin.AuthNewLoginIpAddress
import piuk.blockchain.android.ui.auth.newlogin.AuthNewLoginLocation
import piuk.blockchain.android.ui.login.LoginAnalytics

class AuthNewLoginSheet :
    MviBottomSheet<AuthNewLoginModel, AuthNewLoginIntents, AuthNewLoginState, AuthNewLoginSheetBinding>() {

    interface Host : SlidingModalBottomDialog.Host {
        fun navigateToBottomSheet(bottomSheet: BottomSheetDialogFragment)
    }

    override val host: Host by lazy {
        super.host as? Host
            ?: throw IllegalStateException("Host fragment is not a AuthNewLoginSheet.Host")
    }

    private val listAdapter: AuthNewLoginInfoDelegateAdapter by lazy {
        AuthNewLoginInfoDelegateAdapter()
    }

    override val model: AuthNewLoginModel by scopedInject()

    override fun render(newState: AuthNewLoginState) {
        if (newState.items.size != listAdapter.itemCount) {
            listAdapter.items = newState.items
            listAdapter.notifyDataSetChanged()
            if (newState.errorState == NewLoginState.IP_MISMATCH) {
                analytics.logEvent(LoginAnalytics.LoginFailedIPMismatch)
            }
        }
        binding.approveButton.isEnabled = newState.enableApproval
        binding.secureLoginIpNotice.visibleIf { !newState.enableApproval }
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): AuthNewLoginSheetBinding =
        AuthNewLoginSheetBinding.inflate(inflater, container, false)

    override fun initControls(binding: AuthNewLoginSheetBinding) {
        initItems()
        with(binding) {
            newLoginInfoList.apply {
                layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
                addItemDecoration(BlockchainListDividerDecor(requireContext()))
                adapter = listAdapter
            }
            approveButton.setOnClickListener { onApproveClicked() }
            denyButton.setOnClickListener {
                model.process(AuthNewLoginIntents.LoginDenied)
                host.navigateToBottomSheet(AuthConfirmationSheet(isApproved = false))
            }
        }
    }

    private fun initItems() {
        arguments?.let { bundle ->
            val items = listOf(
                AuthNewLoginLocation(bundle.originLocation),
                AuthNewLoginIpAddress(bundle.originIp),
                AuthNewLoginBrowserInfo(bundle.originBrowser)
            )

            model.process(
                AuthNewLoginIntents.InitAuthInfo(
                    pubKeyHash = bundle.pubKeyHash,
                    message = bundle.message,
                    items = items,
                    forcePin = bundle.getBoolean(FORCE_PIN),
                    originIp = bundle.originIp
                )
            )
        }
    }

    private fun onApproveClicked() {
        model.process(AuthNewLoginIntents.LoginApproved)
        host.navigateToBottomSheet(AuthConfirmationSheet(isApproved = true))
    }

    private val Bundle?.pubKeyHash
        get() = this?.getString(PUB_KEY_HASH) ?: throw IllegalArgumentException(
            "PubKeyHash should not be null"
        )

    private val Bundle?.message
        get() = this?.getParcelable<SecureChannelBrowserMessageArg>(MESSAGE) ?: throw IllegalArgumentException(
            "Message should not be null"
        )

    private val Bundle?.originIp
        get() = this?.getString(ORIGIN_IP) ?: throw IllegalArgumentException(
            "OriginIP should not be null"
        )

    private val Bundle?.originLocation
        get() = this?.getString(ORIGIN_LOCATION) ?: throw IllegalArgumentException(
            "OriginLocation should not be null"
        )

    private val Bundle?.originBrowser
        get() = this?.getString(ORIGIN_BROWSER) ?: throw IllegalArgumentException(
            "OriginBrowser should not be null"
        )

    companion object {
        const val PUB_KEY_HASH = "PUB_KEY_HASH"
        const val MESSAGE = "MESSAGE"
        const val FORCE_PIN = "FORCE_PIN"
        const val ORIGIN_IP = "ORIGIN_IP"
        const val ORIGIN_LOCATION = "ORIGIN_LOCATION"
        const val ORIGIN_BROWSER = "ORIGIN_BROWSER"

        // todo (othman): with new MVI this will be bundled into one ARG class
        fun newInstance(
            pubKeyHash: String?,
            message: SecureChannelBrowserMessageArg?,
            forcePin: Boolean?,
            originIP: String?,
            originLocation: String?,
            originBrowser: String?
        ) =
            AuthNewLoginSheet().apply {
                arguments = Bundle().apply {
                    putString(PUB_KEY_HASH, pubKeyHash)
                    putParcelable(MESSAGE, message)
                    putBoolean(FORCE_PIN, forcePin ?: false)
                    putString(ORIGIN_IP, originIP)
                    putString(ORIGIN_LOCATION, originLocation)
                    putString(ORIGIN_BROWSER, originBrowser)
                }
            }
    }
}

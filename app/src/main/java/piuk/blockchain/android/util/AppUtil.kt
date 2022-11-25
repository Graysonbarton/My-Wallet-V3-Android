package piuk.blockchain.android.util

import android.content.Context
import android.content.Intent
import com.blockchain.commonarch.presentation.base.ActivityIndicator
import com.blockchain.commonarch.presentation.base.AppUtilAPI
import com.blockchain.commonarch.presentation.base.BlockchainActivity
import com.blockchain.core.access.PinRepository
import com.blockchain.logging.DigitalTrust
import com.blockchain.logging.RemoteLogger
import com.blockchain.preferences.SessionPrefs
import com.blockchain.preferences.WalletStatusPrefs
import com.blockchain.unifiedcryptowallet.domain.activity.service.UnifiedActivityService
import info.blockchain.wallet.payload.PayloadScopeWiper
import io.intercom.android.sdk.Intercom
import piuk.blockchain.android.ui.auth.LogoutActivity
import piuk.blockchain.android.ui.launcher.LauncherActivityV2
import piuk.blockchain.android.ui.launcher.loader.LoginMethod

class AppUtil(
    private val context: Context,
    private var payloadScopeWiper: PayloadScopeWiper,
    private val sessionPrefs: SessionPrefs,
    private val trust: DigitalTrust,
    private val pinRepository: PinRepository,
    private val remoteLogger: RemoteLogger,
    private val walletStatusPrefs: WalletStatusPrefs,
    private val unifiedActivityService: UnifiedActivityService
) : AppUtilAPI {
    override fun logout(isIntercomEnabled: Boolean) {
        pinRepository.clearPin()
        trust.clearUserId()
        context.startActivity(
            Intent(context, LogoutActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                action = BlockchainActivity.LOGOUT_ACTION
            }
        )
        if (isIntercomEnabled) {
            Intercom.client().logout()
        }
    }

    fun unpairWallet() {
        pinRepository.clearPin()
        sessionPrefs.unPairWallet()
    }

    override var activityIndicator: ActivityIndicator? = null

    fun clearCredentials() {
        remoteLogger.logEvent("Clearing credentials")
        payloadScopeWiper.wipe()
        sessionPrefs.clear()
        unifiedActivityService.clearCache()
    }

    fun clearCredentialsAndRestart() {
        clearCredentials()
        restartApp()
    }

    fun restartApp() {
        context.startActivity(
            Intent(context, LauncherActivityV2::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun loadAppWithVerifiedPin(
        loaderActivity: Class<*>,
        loginMethod: LoginMethod = LoginMethod.UNDEFINED,
        referralCode: String? = null
    ) {
        context.startActivity(
            Intent(context, loaderActivity).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(INTENT_EXTRA_VERIFIED, true)
                putExtra(LOGIN_METHOD, loginMethod)
                putExtra(INTENT_EXTRA_REFERRAL_CODE, referralCode)
            }
        )

        walletStatusPrefs.isAppUnlocked = false
    }

    companion object {
        const val INTENT_EXTRA_VERIFIED = "verified"
        const val LOGIN_METHOD = "login_method"
        const val INTENT_EXTRA_REFERRAL_CODE = "referral_code"
    }
}

package piuk.blockchain.android.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.blockchain.biometrics.BiometricAuthError
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthLockout
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthLockoutPermanent
import com.blockchain.biometrics.BiometricAuthError.BiometricAuthOther
import com.blockchain.biometrics.BiometricAuthError.BiometricKeysInvalidated
import com.blockchain.biometrics.BiometricsCallback
import com.blockchain.biometrics.BiometricsType
import com.blockchain.componentlib.legacy.MaterialProgressDialog
import com.blockchain.presentation.koin.scopedInject
import piuk.blockchain.android.R
import piuk.blockchain.android.data.biometrics.BiometricPromptUtil
import piuk.blockchain.android.data.biometrics.BiometricsController
import piuk.blockchain.android.data.biometrics.WalletBiometricData
import piuk.blockchain.android.databinding.ActivityOnboardingBinding
import piuk.blockchain.android.ui.base.BaseMvpActivity

internal class OnboardingActivity :
    BaseMvpActivity<OnboardingView, OnboardingPresenter>(),
    OnboardingView,
    BiometricsPromptFragment.OnFragmentInteractionListener,
    EmailPromptFragment.OnFragmentInteractionListener {

    private val onboardingPresenter: OnboardingPresenter by scopedInject()
    private val biometricsController: BiometricsController by scopedInject()
    private var emailLaunched = false

    private val binding: ActivityOnboardingBinding by lazy {
        ActivityOnboardingBinding.inflate(layoutInflater)
    }

    private var progressDialog: MaterialProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        progressDialog = MaterialProgressDialog(this).apply {
            setMessage(com.blockchain.stringResources.R.string.please_wait)
            setCancelable(false)
            show()
        }

        onViewReady()
    }

    override fun onResume() {
        super.onResume()

        if (emailLaunched) {
            finish()
        }
    }

    override val showEmail: Boolean
        get() = intent.showEmail

    override val showFingerprints: Boolean
        get() = intent.showFingerprints

    override fun showFingerprintPrompt() {
        if (!isFinishing) {
            dismissDialog()
            val fragmentManager = supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.content_frame, BiometricsPromptFragment.newInstance())
                .commit()
        }
    }

    override fun showEmailPrompt() {
        if (!isFinishing) {
            dismissDialog()
            val fragmentManager = supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(
                    R.id.content_frame,
                    EmailPromptFragment.newInstance(presenter?.email!!)
                )
                .commit()
        }
    }

    override fun onEnableFingerprintClicked() {
        presenter?.onEnableFingerprintClicked()
    }

    override fun showFingerprintDialog(pincode: String) {
        if (!isFinishing) {
            biometricsController.authenticate(
                this,
                BiometricsType.TYPE_REGISTER,
                object : BiometricsCallback<WalletBiometricData> {
                    override fun onAuthSuccess(unencryptedBiometricData: WalletBiometricData) {
                        if (showEmail) {
                            showEmailPrompt()
                        } else {
                            finish()
                        }
                    }

                    override fun onAuthFailed(error: BiometricAuthError) {
                        presenter?.setFingerprintUnlockDisabled()
                        when (error) {
                            is BiometricAuthLockout -> BiometricPromptUtil.showAuthLockoutDialog(
                                this@OnboardingActivity
                            )
                            is BiometricAuthLockoutPermanent -> BiometricPromptUtil.showPermanentAuthLockoutDialog(
                                this@OnboardingActivity
                            )
                            is BiometricKeysInvalidated -> BiometricPromptUtil.showInfoInvalidatedKeysDialog(
                                this@OnboardingActivity
                            )
                            is BiometricAuthOther ->
                                BiometricPromptUtil.showBiometricsGenericError(this@OnboardingActivity, error.error)
                            else -> {
                                // do nothing - this is handled by the Biometric Prompt framework
                            }
                        }
                    }

                    override fun onAuthCancelled() {
                        presenter?.setFingerprintUnlockDisabled()
                    }
                }
            )
        }
    }

    override fun showEnrollFingerprintsDialog() {
        if (!isFinishing) {
            AlertDialog.Builder(this, com.blockchain.componentlib.R.style.AlertDialogStyle)
                .setTitle(com.blockchain.stringResources.R.string.app_name)
                .setMessage(com.blockchain.stringResources.R.string.fingerprint_no_fingerprints_added)
                .setCancelable(true)
                .setPositiveButton(com.blockchain.stringResources.R.string.common_yes) { _, _ ->
                    startActivityForResult(
                        Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),
                        0
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onVerifyEmailClicked() {
        emailLaunched = true
        val intent = Intent(Intent.ACTION_MAIN)
        intent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_APP_EMAIL)
        }
        startActivity(
            Intent.createChooser(intent, getString(com.blockchain.stringResources.R.string.security_centre_email_check))
        )
    }

    override fun createPresenter() = onboardingPresenter

    override fun getView(): OnboardingView {
        return this
    }

    private fun dismissDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    companion object {
        fun launchForFingerprints(ctx: Context) {
            Intent(ctx, OnboardingActivity::class.java).let {
                it.showEmail = false
                it.showFingerprints = true
                ctx.startActivity(it)
            }
        }

        fun launchForEmail(ctx: Context) {
            Intent(ctx, OnboardingActivity::class.java).let {
                it.showEmail = true
                it.showFingerprints = false
                ctx.startActivity(it)
            }
        }

        /**
         * Flag for showing only the email verification prompt. This is for use when signup was
         * completed some other time, but the user hasn't verified their email yet.
         */
        private const val EXTRAS_SHOW_EMAIL = "show_email"
        private const val EXTRAS_SHOW_FINGERPRINTS = "show_fingerprints"

        private var Intent.showEmail: Boolean
            get() = extras?.getBoolean(EXTRAS_SHOW_EMAIL, true) ?: true
            set(v) {
                putExtra(EXTRAS_SHOW_EMAIL, v)
            }

        private var Intent.showFingerprints: Boolean
            get() = extras?.getBoolean(EXTRAS_SHOW_FINGERPRINTS, true) ?: true
            set(v) {
                putExtra(EXTRAS_SHOW_FINGERPRINTS, v)
            }
    }
}

package piuk.blockchain.android.ui.backup.wordlist

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.FragmentTransaction
import com.blockchain.analytics.Analytics
import com.blockchain.componentlib.alert.BlockchainSnackbar
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.viewextensions.invisible
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.logging.RemoteLogger
import com.blockchain.presentation.koin.scopedInject
import com.blockchain.utils.unsafeLazy
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.FragmentBackupWordListBinding
import piuk.blockchain.android.ui.backup.verify.BackupWalletVerifyFragment
import piuk.blockchain.android.ui.base.BaseFragment
import piuk.blockchain.android.ui.settings.SettingsAnalytics

class BackupWalletWordListFragment :
    BaseFragment<BackupWalletWordListView, BackupWalletWordListPresenter>(),
    BackupWalletWordListView {

    private val backupWalletWordListPresenter: BackupWalletWordListPresenter by scopedInject()
    private val remoteLogger: RemoteLogger by inject()
    private val analytics: Analytics by inject()

    private val animEnterFromRight: Animation by unsafeLazy {
        AnimationUtils.loadAnimation(
            activity,
            R.anim.enter_from_right
        )
    }
    private val animEnterFromLeft: Animation by unsafeLazy {
        AnimationUtils.loadAnimation(
            activity,
            R.anim.enter_from_left
        )
    }
    private val animExitToLeft: Animation by unsafeLazy {
        AnimationUtils.loadAnimation(
            activity,
            R.anim.exit_to_left
        )
    }
    private val animExitToRight: Animation by unsafeLazy {
        AnimationUtils.loadAnimation(
            activity,
            R.anim.exit_to_right
        )
    }
    private val word: String by unsafeLazy {
        getString(
            com.blockchain.stringResources.R.string.backup_word
        )
    }
    private val of: String by unsafeLazy {
        getString(
            com.blockchain.stringResources.R.string.backup_of
        )
    }

    var currentWordIndex = 0

    private var _binding: FragmentBackupWordListBinding? = null
    private val binding: FragmentBackupWordListBinding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupWordListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            analytics.logEvent(SettingsAnalytics.RecoveryPhraseShown)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady()

        with(binding) {
            textviewWordCounter.text = getFormattedPositionString()
            presenter.getWordForIndex(currentWordIndex)?.let {
                textviewCurrentWord.text = it
            } ?: kotlin.run {
                remoteLogger.logEvent("Mnemonic word for position $currentWordIndex was null")
                BlockchainSnackbar.make(
                    binding.root,
                    getString(com.blockchain.stringResources.R.string.common_error),
                    type = SnackbarType.Error
                ).show()
            }
            buttonNext.setOnClickListener { onNextClicked() }
            buttonPrevious.setOnClickListener { onPreviousClicked() }
        }
    }

    override fun getPageBundle(): Bundle? = arguments

    override fun createPresenter() = backupWalletWordListPresenter

    override fun getMvpView() = this

    override fun finish() {
        activity?.finish()
    }

    private fun onNextClicked() {
        with(binding) {
            if (currentWordIndex >= 0) {
                buttonPrevious.visible()
            } else {
                buttonPrevious.invisible()
            }

            if (currentWordIndex < presenter.getMnemonicSize() - 1) {
                animExitToLeft.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {
                        textviewCurrentWord.text = ""
                        textviewWordCounter.text = getFormattedPositionString()
                    }

                    override fun onAnimationRepeat(animation: Animation) {
                        // No-op
                    }

                    override fun onAnimationEnd(animation: Animation) {
                        cardLayout.startAnimation(animEnterFromRight)
                        textviewCurrentWord.text = presenter.getWordForIndex(currentWordIndex)
                    }
                })

                cardLayout.startAnimation(animExitToLeft)
            }

            currentWordIndex++

            if (currentWordIndex == presenter.getMnemonicSize()) {
                currentWordIndex = 0
                launchVerifyFragment()
            } else {
                if (currentWordIndex == presenter.getMnemonicSize() - 1) {
                    buttonNext.text = getString(com.blockchain.stringResources.R.string.backup_done)
                }
            }
        }
    }

    private fun onPreviousClicked() {
        with(binding) {
            buttonNext.text = getString(com.blockchain.stringResources.R.string.backup_next_word)
            if (currentWordIndex == 1) {
                buttonPrevious.invisible()
            }

            animExitToRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    textviewCurrentWord.text = ""
                    textviewWordCounter.text = getFormattedPositionString()
                }

                override fun onAnimationRepeat(animation: Animation) {
                    // No-op
                }

                override fun onAnimationEnd(animation: Animation) {
                    cardLayout.startAnimation(animEnterFromLeft)
                    textviewCurrentWord.text = presenter.getWordForIndex(currentWordIndex)
                }
            })

            cardLayout.startAnimation(animExitToRight)
            currentWordIndex--
        }
    }

    private fun launchVerifyFragment() {
        val fragment = BackupWalletVerifyFragment()
        if (presenter.secondPassword != null) {
            fragment.arguments = Bundle().apply {
                putString(ARGUMENT_SECOND_PASSWORD, presenter.secondPassword)
            }
        }

        fragmentManager?.run {
            beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.content_frame, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun getFormattedPositionString(): CharSequence? = "$word ${currentWordIndex + 1} $of 12"

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.run {
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        _binding = null
    }

    companion object {
        const val ARGUMENT_SECOND_PASSWORD = "second_password"
    }
}

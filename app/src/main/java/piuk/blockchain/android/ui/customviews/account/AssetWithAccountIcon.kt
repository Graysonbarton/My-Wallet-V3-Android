package piuk.blockchain.android.ui.customviews.account

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.blockchain.coincore.CryptoAccount
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ViewAssetWithAccountIconBinding
import piuk.blockchain.android.ui.resources.AccountIcon
import piuk.blockchain.android.ui.resources.AssetResources
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.setAssetIconColoursNoTint
import piuk.blockchain.android.util.visible

class AssetWithAccountIcon @JvmOverloads constructor(
    ctx: Context,
    attr: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(ctx, attr, defStyle), KoinComponent {

    private val assetResources: AssetResources by inject()

    private val binding: ViewAssetWithAccountIconBinding by lazy {
        ViewAssetWithAccountIconBinding.inflate(LayoutInflater.from(context), this, true)
    }

    init {
        binding.assetIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_default_asset_logo))
    }

    fun updateIcon(account: CryptoAccount) {
        val accountIcon = AccountIcon(account, assetResources)

        accountIcon.loadAssetIcon(binding.assetIcon)
        accountIcon.indicator?.let {
            binding.accountIcon.apply {
                visible()
                setAssetIconColoursNoTint(account.currency)
                setImageResource(it)
            }
        } ?: kotlin.run { binding.accountIcon.gone() }
    }
}

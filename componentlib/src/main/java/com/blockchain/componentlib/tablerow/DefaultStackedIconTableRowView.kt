package com.blockchain.componentlib.tablerow

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.utils.BaseAbstractComposeView

class DefaultStackedIconTableRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseAbstractComposeView(context, attrs, defStyleAttr) {

    var primaryText by mutableStateOf("")
    var secondaryText by mutableStateOf("")
    var onClick by mutableStateOf({})
    var topImageResource: ImageResource by mutableStateOf(ImageResource.None)
    var bottomImageResource: ImageResource by mutableStateOf(ImageResource.None)

    @Composable
    override fun Content() {
        AppTheme(setSystemColors = false) {
            AppSurface {
                DefaultStackedIconTableRow(
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    onClick = onClick,
                    topImageResource = topImageResource,
                    bottomImageResource = bottomImageResource,
                )
            }
        }
    }

    fun clearState() {
        primaryText = ""
        secondaryText = ""
        onClick = {}
        topImageResource = ImageResource.None
        bottomImageResource = ImageResource.None
    }
}

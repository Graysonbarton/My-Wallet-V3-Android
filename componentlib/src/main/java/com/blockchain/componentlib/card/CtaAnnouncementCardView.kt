package com.blockchain.componentlib.card

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.utils.BaseAbstractComposeView

class CtaAnnouncementCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseAbstractComposeView(context, attrs, defStyleAttr) {

    var header by mutableStateOf("")
    var subheader by mutableStateOf(
        AnnotatedString("")
    )
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var iconResource: ImageResource by mutableStateOf(ImageResource.None)
    var borderColor by mutableStateOf(null as? Color?)
    var callToActionButton by mutableStateOf(CardButton(""))
    var onClose by mutableStateOf({})

    @Composable
    override fun Content() {
        AppTheme(setSystemColors = false) {
            AppSurface {
                CtaAnnouncementCard(
                    header = header,
                    subheader = subheader,
                    title = title,
                    body = body,
                    borderColor = borderColor,
                    iconResource = iconResource,
                    callToActionButton = callToActionButton,
                    onClose = onClose
                )
            }
        }
    }

    fun clearState() {
        header = ""
        subheader = AnnotatedString("")
        title = ""
        body = ""
        iconResource = ImageResource.None
        borderColor = null
        callToActionButton = CardButton("")
        onClose = {}
    }
}

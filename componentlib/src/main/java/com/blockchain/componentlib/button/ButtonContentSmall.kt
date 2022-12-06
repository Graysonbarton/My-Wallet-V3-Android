package com.blockchain.componentlib.button

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.blockchain.componentlib.R
import com.blockchain.componentlib.basic.Image
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.theme.Blue600

@Composable
fun ButtonContentSmall(
    state: ButtonState,
    text: String,
    textColor: Color,
    contentAlpha: Float,
    icon: ImageResource = ImageResource.None,
    modifier: Modifier = Modifier,
    @DrawableRes loadingIconResId: Int = R.drawable.ic_loading
) {
    Box(modifier.animateContentSize()) {
        if (state == ButtonState.Loading) {
            ButtonLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(dimensionResource(R.dimen.medium_spacing)),
                loadingIconResId = loadingIconResId,
            )
        } else {
            Row(
                Modifier.alpha(contentAlpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (icon) {
                    is ImageResource.Local -> {
                        Image(
                            imageResource = icon.withColorFilter(ColorFilter.tint(textColor)),
                            modifier = Modifier.size(dimensionResource(R.dimen.size_standard)),
                        )
                        Spacer(Modifier.width(AppTheme.dimensions.tinySpacing))
                    }
                    is ImageResource.LocalWithResolvedDrawable -> {
                        Image(
                            imageResource = icon,
                            modifier = Modifier.size(dimensionResource(R.dimen.size_standard)),
                        )
                        Spacer(Modifier.width(AppTheme.dimensions.tinySpacing))
                    }
                    is ImageResource.LocalWithBackground -> {
                        Image(
                            imageResource = icon,
                            modifier = Modifier.size(dimensionResource(R.dimen.medium_spacing)),
                        )
                        Spacer(Modifier.width(AppTheme.dimensions.tinySpacing))
                    }
                    is ImageResource.Remote -> {
                        Image(
                            imageResource = icon,
                            modifier = Modifier.size(dimensionResource(R.dimen.medium_spacing)),
                        )
                        Spacer(Modifier.width(AppTheme.dimensions.tinySpacing))
                    }
                    is ImageResource.LocalWithResolvedBitmap,
                    is ImageResource.LocalWithBackgroundAndExternalResources,
                    ImageResource.None -> { /* no-op */
                    }
                }

                Text(
                    text = text,
                    color = textColor,
                    style = AppTheme.typography.paragraph2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewButtonContentSmall() {
    ButtonContentSmall(
        state = ButtonState.Enabled,
        text = "Receive",
        textColor = Blue600,
        contentAlpha = 1.0F,
        icon = ImageResource.Local(
            R.drawable.ic_bottom_nav_prices
        )
    )
}

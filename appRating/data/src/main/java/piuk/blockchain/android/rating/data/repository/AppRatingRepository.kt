package piuk.blockchain.android.rating.data.repository

import com.blockchain.outcome.fold
import piuk.blockchain.android.rating.data.api.AppRatingApi
import piuk.blockchain.android.rating.data.model.AppRatingApiKeys
import piuk.blockchain.android.rating.data.remoteconfig.AppRatingApiKeysRemoteConfig
import piuk.blockchain.android.rating.data.remoteconfig.AppRatingRemoteConfig
import piuk.blockchain.android.rating.domain.model.AppRating
import piuk.blockchain.android.rating.domain.service.AppRatingService

internal class AppRatingRepository(
    private val appRatingRemoteConfig: AppRatingRemoteConfig,
    private val appRatingApiKeysRemoteConfig: AppRatingApiKeysRemoteConfig,
    private val defaultThreshold: Int,
    private val appRatingApi: AppRatingApi
) : AppRatingService {

    override suspend fun getThreshold(): Int {
        return appRatingRemoteConfig.getThreshold().fold(
            onSuccess = { it },
            onFailure = { defaultThreshold }
        )
    }

    override suspend fun postRatingData(appRating: AppRating): Boolean {
        // get api keys from remote config
        val apiKeys: AppRatingApiKeys? = appRatingApiKeysRemoteConfig.getApiKeys().fold(
            onSuccess = { it },
            onFailure = { null }
        )

        // if for some reason we can't get api keys, or json is damaged, we return false
        // for the vm to retrigger again in 1 month
        return apiKeys?.let {
            appRatingApi.postRatingData(
                apiKeys = apiKeys,
                appRating = appRating
            ).fold(
                // if there is any error in the api, we return false
                // for the vm to retrigger again in 1 month
                onSuccess = { true },
                onFailure = { false }
            )
        } ?: false
    }
}

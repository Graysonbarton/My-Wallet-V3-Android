package com.blockchain.api.assetdiscovery

import com.blockchain.api.assetdiscovery.data.AssetInformationDto
import com.blockchain.api.assetdiscovery.data.DynamicCurrencyList
import com.blockchain.network.interceptor.Cacheable
import com.blockchain.network.interceptor.DoNotLogResponseBody
import com.blockchain.outcome.Outcome
import io.reactivex.rxjava3.core.Single
import retrofit2.http.GET
import retrofit2.http.Path

internal interface AssetDiscoveryApiInterface {

    @Cacheable(maxAge = Cacheable.MAX_AGE_THREE_DAYS)
    @DoNotLogResponseBody
    @GET("assets/currencies/fiat")
    fun getFiatCurrencies(): Single<DynamicCurrencyList>

    @Cacheable(maxAge = Cacheable.MAX_AGE_THREE_DAYS)
    @DoNotLogResponseBody
    @GET("assets/currencies/erc20")
    fun getEthErc20s(): Single<DynamicCurrencyList>

    @Cacheable(maxAge = Cacheable.MAX_AGE_THREE_DAYS)
    @DoNotLogResponseBody
    @GET("assets/currencies/custodial")
    fun getCustodialCurrencies(): Single<DynamicCurrencyList>

    @Cacheable(maxAge = Cacheable.MAX_AGE_THREE_DAYS)
    @DoNotLogResponseBody
    @GET("assets/currencies/coin")
    fun getL1Coins(): Single<DynamicCurrencyList>

    @Cacheable(maxAge = Cacheable.MAX_AGE_THREE_DAYS)
    @DoNotLogResponseBody
    @GET("assets/currencies/other_erc20")
    fun getOtherErc20s(): Single<DynamicCurrencyList>

    @GET("assets/info/{assetTicker}")
    suspend fun getAssetInfo(
        @Path("assetTicker") ticker: String
    ): Outcome<Exception, AssetInformationDto>
}

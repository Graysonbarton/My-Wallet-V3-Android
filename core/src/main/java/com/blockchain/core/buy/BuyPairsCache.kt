package com.blockchain.core.buy

import com.blockchain.core.common.caching.TimedCacheRequest
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyPairsDto
import com.blockchain.nabu.service.NabuService
import io.reactivex.rxjava3.core.Single

@Deprecated("use BuyPairsStore - remove when CoinView is migrated")
class BuyPairsCache(private val nabuService: NabuService) {

    private val refresh: () -> Single<SimpleBuyPairsDto> = {
        nabuService.getSupportedCurrencies()
    }

    private val cache = TimedCacheRequest(
        cacheLifetimeSeconds = CACHE_LIFETIME,
        refreshFn = refresh
    )

    fun pairs(): Single<SimpleBuyPairsDto> = cache.getCachedSingle()

    companion object {
        private const val CACHE_LIFETIME = 10 * 60L
    }
}

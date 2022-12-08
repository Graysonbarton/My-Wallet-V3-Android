package com.blockchain.earn.data.dataresources.interest

import com.blockchain.api.interest.InterestApiService
import com.blockchain.api.interest.data.InterestEligibilityDto
import com.blockchain.store.Fetcher
import com.blockchain.store.Store
import com.blockchain.store.impl.Freshness
import com.blockchain.store.impl.FreshnessMediator
import com.blockchain.store_caches_persistedjsonsqldelight.PersistedJsonSqlDelightStoreBuilder
import com.blockchain.storedatasource.FlushableDataSource
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class InterestEligibilityStore(
    private val interestApiService: InterestApiService
) : Store<Map<String, InterestEligibilityDto>> by PersistedJsonSqlDelightStoreBuilder()
    .build(
        storeId = STORE_ID,
        fetcher = Fetcher.ofSingle(
            mapper = {
                interestApiService.getTickersEligibility()
            }
        ),
        dataSerializer = MapSerializer(
            keySerializer = String.serializer(),
            valueSerializer = InterestEligibilityDto.serializer()
        ),
        mediator = FreshnessMediator(Freshness.DURATION_1_HOUR)
    ),
    FlushableDataSource {

    override fun invalidate() {
        markAsStale()
    }

    companion object {
        private const val STORE_ID = "InterestEligibilityStore"
    }
}

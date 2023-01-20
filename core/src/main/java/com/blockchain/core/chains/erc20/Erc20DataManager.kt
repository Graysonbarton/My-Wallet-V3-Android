package com.blockchain.core.chains.erc20

import com.blockchain.core.chains.EvmNetwork
import com.blockchain.core.chains.erc20.call.Erc20HistoryCallCache
import com.blockchain.core.chains.erc20.data.store.Erc20DataSource
import com.blockchain.core.chains.erc20.data.store.Erc20L2DataSource
import com.blockchain.core.chains.erc20.data.store.L1BalanceStore
import com.blockchain.core.chains.erc20.domain.Erc20L2StoreService
import com.blockchain.core.chains.erc20.domain.Erc20StoreService
import com.blockchain.core.chains.erc20.domain.model.Erc20Balance
import com.blockchain.core.chains.erc20.domain.model.Erc20HistoryList
import com.blockchain.core.chains.ethereum.EthDataManager
import com.blockchain.core.common.caching.ParameteredSingleTimedCacheRequest
import com.blockchain.data.FreshnessStrategy
import com.blockchain.data.FreshnessStrategy.Companion.withKey
import com.blockchain.data.RefreshStrategy
import com.blockchain.logging.Logger
import com.blockchain.store.asObservable
import com.blockchain.store.asSingle
import com.blockchain.store.mapData
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Currency
import info.blockchain.wallet.api.data.FeeOptions
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import java.math.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.await
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.RawTransaction

interface Erc20DataManager {
    val accountHash: String
    val requireSecondPassword: Boolean

    fun getL1TokenBalance(asset: AssetInfo): Single<CryptoValue>

    fun getErc20History(asset: AssetInfo, evmNetwork: EvmNetwork): Single<Erc20HistoryList>

    fun createErc20Transaction(
        asset: AssetInfo,
        to: String,
        amount: BigInteger,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        hotWalletAddress: String
    ): Single<RawTransaction>

    fun createEvmTransaction(
        asset: AssetInfo,
        evmNetwork: String,
        to: String,
        amount: BigInteger,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        hotWalletAddress: String
    ): Single<RawTransaction>

    fun signErc20Transaction(
        rawTransaction: RawTransaction,
        secondPassword: String = "",
        l1Chain: String
    ): Single<ByteArray>

    fun getFeesForEvmTransaction(l1Chain: String): Single<FeeOptions>

    fun pushErc20Transaction(signedTxBytes: ByteArray, l1Chain: String): Single<String>

    fun supportsErc20TxNote(asset: AssetInfo): Boolean
    fun getErc20TxNote(asset: AssetInfo, txHash: String): String?
    fun putErc20TxNote(asset: AssetInfo, txHash: String, note: String): Completable

    fun hasUnconfirmedTransactions(): Single<Boolean>
    fun latestBlockNumber(l1Chain: String? = null): Single<BigInteger>
    fun isContractAddress(address: String, l1Chain: String? = null): Single<Boolean>

    // todo(othman) remove and use Erc20Service instead - will eventually contain all erc20 operations
    fun getErc20Balance(asset: AssetInfo): Observable<Erc20Balance>
    fun getActiveAssets(
        refreshStrategy: FreshnessStrategy = FreshnessStrategy.Cached(RefreshStrategy.ForceRefresh)
    ): Flow<Set<AssetInfo>>

    fun getSupportedNetworks(): Single<Set<EvmNetwork>>

    // TODO: Get assets with balance
    fun flushCaches(asset: AssetInfo)

    fun getL1AssetFor(asset: AssetInfo): Single<AssetInfo>
}

internal class Erc20DataManagerImpl(
    private val ethDataManager: EthDataManager,
    private val l1BalanceStore: L1BalanceStore,
    private val historyCallCache: Erc20HistoryCallCache,
    private val assetCatalogue: AssetCatalogue,
    private val erc20StoreService: Erc20StoreService,
    private val erc20DataSource: Erc20DataSource,
    private val erc20L2StoreService: Erc20L2StoreService,
    private val erc20L2DataSource: Erc20L2DataSource,
) : Erc20DataManager {

    override val accountHash: String
        get() = ethDataManager.accountAddress

    override val requireSecondPassword: Boolean
        get() = ethDataManager.requireSecondPassword

    private val latestBlockCacheRequest: ParameteredSingleTimedCacheRequest<EvmNetwork, BigInteger> by lazy {
        ParameteredSingleTimedCacheRequest(
            cacheLifetimeSeconds = NODE_CALLS_CACHE_TTL_SECONDS,
            refreshFn = ::refreshLatestBlockNumber
        )
    }

    override fun getL1TokenBalance(asset: AssetInfo): Single<CryptoValue> =
        Singles.zip(
            ethDataManager.supportedNetworks,
            getL1AssetFor(asset)
        )
            .flatMap { (supportedNetworks, l1Asset) ->
                supportedNetworks.firstOrNull { evmNetwork ->
                    // Fall back to the network ticker in case of an L1 coin
                    evmNetwork.networkTicker == (asset.l1chainTicker ?: asset.networkTicker)
                }?.let { evmNetwork ->
                    l1BalanceStore.stream(
                        FreshnessStrategy.Cached(RefreshStrategy.ForceRefresh)
                            .withKey(L1BalanceStore.Key(evmNetwork.nodeUrl))
                    ).mapData { balance ->
                        CryptoValue(l1Asset, balance)
                    }.asSingle()
                } ?: throw IllegalStateException("L1 chain is missing or not supported")
            }

    override fun getErc20Balance(asset: AssetInfo): Observable<Erc20Balance> {
        require(asset.isErc20())
        requireNotNull(asset.l1chainTicker)
        requireNotNull(asset.l2identifier)

        return ethDataManager.supportedNetworks.flatMapObservable { supportedNetworks ->
            supportedNetworks.firstOrNull { it.networkTicker == asset.l1chainTicker }?.let { evmNetwork ->
                // Get the balance of the native token for example Matic in Polygon's case. Only load
                // the balances of the other tokens on that network if the native token balance is positive.
                l1BalanceStore
                    .stream(
                        FreshnessStrategy.Cached(RefreshStrategy.ForceRefresh)
                            .withKey(L1BalanceStore.Key(evmNetwork.nodeUrl))
                    )
                    .mapData { balance -> Pair(evmNetwork, balance) }
                    .asObservable()
                    .flatMap { (evmNetwork, value) -> getErc20Balance(asset, evmNetwork, value) }
            } ?: Observable.just(Erc20Balance.zero(asset))
        }
    }

    override fun getActiveAssets(refreshStrategy: FreshnessStrategy): Flow<Set<AssetInfo>> = flow {
        val erc20L2ActiveAssets = getSupportedNetworks().await().let { evmNetworks ->
            val erc20L2ActiveAssetLists = evmNetworks.map { evmNetwork ->
                erc20L2StoreService.getActiveAssets(networkTicker = evmNetwork.networkTicker)
                    .catch { emit(emptySet()) }
            }
            if (erc20L2ActiveAssetLists.isNotEmpty()) {
                combine(erc20L2ActiveAssetLists) { assets ->
                    assets.reduce { acc, set -> acc.plus(set).toSet() }
                }
            } else {
                flowOf(emptySet())
            }
        }
        emitAll(erc20L2ActiveAssets)
    }

    override fun getErc20History(asset: AssetInfo, evmNetwork: EvmNetwork): Single<Erc20HistoryList> {
        return historyCallCache.fetch(accountHash, asset, evmNetwork.networkTicker)
    }

    override fun supportsErc20TxNote(asset: AssetInfo): Boolean {
        require(asset.isErc20())
        return try {
            // TODO (dtverdota): this try catch is here because there's no token data on the
            // Ethereum blockchain for L2 Erc20 coins like USDC.MATIC. Even though we check null here,
            // there's a crash further down on erc20Tokens in the EthereumWallet when the user only got
            // coins on an L2 chain. Deal with it as part of the L2 + ETH -> EVM refactoring.
            ethDataManager.getErc20TokenData(asset) != null
        } catch (ex: Exception) {
            Logger.e(ex)
            return false
        }
    }

    override fun getErc20TxNote(asset: AssetInfo, txHash: String): String? {
        require(asset.isErc20())
        return ethDataManager.getErc20TokenData(asset)?.txNotes?.get(txHash)
    }

    override fun putErc20TxNote(asset: AssetInfo, txHash: String, note: String): Completable {
        require(asset.isErc20())
        return ethDataManager.updateErc20TransactionNotes(asset, txHash, note)
    }

    override fun isContractAddress(address: String, l1Chain: String?): Single<Boolean> =
        ethDataManager.supportedNetworks.flatMap { supportedL2Networks ->
            supportedL2Networks.firstOrNull { it.networkTicker == l1Chain }?.let { evmNetwork ->
                ethDataManager.isContractAddress(address, evmNetwork.nodeUrl)
            } ?: ethDataManager.isContractAddress(address)
        }

    override fun createErc20Transaction(
        asset: AssetInfo,
        to: String,
        amount: BigInteger,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        hotWalletAddress: String
    ): Single<RawTransaction> {
        require(asset.isErc20())
        val l1Chain = asset.l1chainTicker
        require(l1Chain != null)

        return getNonce(l1Chain)
            .map { nonce ->
                val contractAddress = asset.l2identifier
                checkNotNull(contractAddress)

                // If we couldn't find a hot wallet address for any reason (in which case the HotWalletService is
                // returning an empty address) fall back to the usual path.
                val useHotWallet = hotWalletAddress.isNotEmpty()

                RawTransaction.createTransaction(
                    nonce,
                    gasPriceWei,
                    if (useHotWallet) {
                        gasLimitGwei + ethDataManager.extraGasLimitForMemo()
                    } else {
                        gasLimitGwei
                    },
                    contractAddress,
                    0.toBigInteger(),
                    erc20TransferMethod(to, amount, hotWalletAddress, useHotWallet)
                )
            }
    }

    override fun createEvmTransaction(
        asset: AssetInfo,
        evmNetwork: String,
        to: String,
        amount: BigInteger,
        gasPriceWei: BigInteger,
        gasLimitGwei: BigInteger,
        hotWalletAddress: String
    ): Single<RawTransaction> {

        return getNonce(evmNetwork)
            .map { nonce ->
                // If we couldn't find a hot wallet address for any reason (in which case the HotWalletService is
                // returning an empty address) fall back to the usual path.
                val useHotWallet = hotWalletAddress.isNotEmpty()

                ethDataManager.createEthTransaction(
                    nonce = nonce,
                    to = if (useHotWallet) hotWalletAddress else to,
                    gasPriceWei = gasPriceWei,
                    gasLimitGwei = if (useHotWallet) {
                        gasLimitGwei + ethDataManager.extraGasLimitForMemo()
                    } else {
                        gasLimitGwei
                    },
                    weiValue = amount,
                    data = if (useHotWallet) to else ""
                )
            }
    }

    private fun getNonce(l1Chain: String): Single<BigInteger> {
        return ethDataManager.supportedNetworks.flatMap { supportedNetworks ->
            supportedNetworks.firstOrNull { evmNetwork ->
                evmNetwork.networkTicker == l1Chain
            }?.let { evmNetwork ->
                ethDataManager.getNonce(evmNetwork.nodeUrl)
            } ?: throw IllegalAccessException("Unsupported EVM Network")
        }
    }

    private fun erc20TransferMethod(
        to: String,
        amount: BigInteger,
        hotWalletAddress: String,
        useHotWallet: Boolean
    ): String {
        val transferMethodHex = "0xa9059cbb"

        return if (useHotWallet) {
            transferMethodHex + TypeEncoder.encode(Address(hotWalletAddress)) +
                TypeEncoder.encode(org.web3j.abi.datatypes.generated.Uint256(amount)) +
                TypeEncoder.encode(Address(to))
        } else {
            transferMethodHex + TypeEncoder.encode(Address(to)) +
                TypeEncoder.encode(org.web3j.abi.datatypes.generated.Uint256(amount))
        }
    }

    override fun signErc20Transaction(
        rawTransaction: RawTransaction,
        secondPassword: String,
        l1Chain: String
    ): Single<ByteArray> =
        ethDataManager.supportedNetworks.flatMap { supportedL2Networks ->
            supportedL2Networks.firstOrNull { it.networkTicker == l1Chain }?.let { evmNetwork ->
                ethDataManager.signEthTransaction(rawTransaction, secondPassword, evmNetwork.chainId)
            } ?: throw IllegalAccessException("Unsupported EVM Network")
        }

    override fun getFeesForEvmTransaction(l1Chain: String): Single<FeeOptions> =
        ethDataManager.getFeesForEvmTx(l1Chain)

    override fun pushErc20Transaction(signedTxBytes: ByteArray, l1Chain: String): Single<String> =
        ethDataManager.supportedNetworks.flatMap { supportedNetworks ->
            supportedNetworks.firstOrNull { it.networkTicker == l1Chain }?.let { evmNetwork ->
                if (evmNetwork.chainId == EthDataManager.ETH_CHAIN_ID) {
                    ethDataManager.pushTx(signedTxBytes)
                } else {
                    ethDataManager.pushEvmTx(signedTxBytes, l1Chain)
                }
            } ?: throw IllegalAccessException("Unsupported EVM Network")
        }

    override fun hasUnconfirmedTransactions(): Single<Boolean> =
        ethDataManager.isLastTxPending()

    override fun latestBlockNumber(l1Chain: String?): Single<BigInteger> =
        ethDataManager.supportedNetworks.flatMap { supportedNetworks ->
            supportedNetworks.firstOrNull { it.networkTicker == l1Chain }?.let { evmNetwork ->
                latestBlockCacheRequest.getCachedSingle(evmNetwork)
            } ?: throw IllegalAccessException("Unsupported EVM Network")
        }

    override fun getSupportedNetworks(): Single<Set<EvmNetwork>> = ethDataManager.supportedNetworks

    override fun flushCaches(asset: AssetInfo) {
        require(asset.isErc20())

        erc20DataSource.invalidate()
        erc20L2DataSource.invalidate(asset.networkTicker)

        historyCallCache.flush(asset)
    }

    override fun getL1AssetFor(asset: AssetInfo): Single<AssetInfo> {
        return ethDataManager.supportedNetworks.map { supportedNetworks ->
            assetCatalogue.fromNetworkTicker(
                supportedNetworks.firstOrNull {
                    // Fall back to the network ticker in case of an L1 coin
                    it.networkTicker == (asset.l1chainTicker ?: asset.networkTicker)
                }?.networkTicker ?: ""
            ) as? AssetInfo ?: throw IllegalAccessException("Unsupported EVM Network")
        }
    }

    private fun getErc20Balance(
        asset: AssetInfo,
        evmNetwork: EvmNetwork,
        balance: BigInteger
    ): Observable<Erc20Balance> {
        val hasNativeTokenBalance = balance > BigInteger.ZERO
        val isOnOtherEvm = evmNetwork.chainId != EthDataManager.ETH_CHAIN_ID
        return when {
            // Only load L2 balances if we have a balance of the network's native token
            isOnOtherEvm && hasNativeTokenBalance -> {
                erc20L2StoreService.getBalances(networkTicker = evmNetwork.networkTicker)
                    .map { it.getOrDefault(asset, Erc20Balance.zero(asset)) }
            }
            isOnOtherEvm.not() -> {
                erc20StoreService.getBalanceFor(asset = asset)
            }
            else -> {
                Observable.just(Erc20Balance.zero(asset))
            }
        }
    }

    private fun refreshLatestBlockNumber(evmNetwork: EvmNetwork): Single<BigInteger> {
        return ethDataManager.getLatestBlockNumber(evmNetwork.nodeUrl).map { it.number }
    }

    companion object {
        private const val NODE_CALLS_CACHE_TTL_SECONDS = 10L
    }
}

fun Currency.isErc20() =
    (this as? AssetInfo)?.isErc20 ?: false

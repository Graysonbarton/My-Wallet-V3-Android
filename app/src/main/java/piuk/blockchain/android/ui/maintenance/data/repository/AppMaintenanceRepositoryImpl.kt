package piuk.blockchain.android.ui.maintenance.data.repository

import com.blockchain.outcome.Outcome
import com.blockchain.preferences.AppUpdatePrefs
import com.google.android.play.core.appupdate.AppUpdateInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import piuk.blockchain.android.ui.maintenance.data.appupdateapi.AppUpdateInfoFactory
import piuk.blockchain.android.ui.maintenance.data.remoteconfig.AppMaintenanceRemoteConfig
import piuk.blockchain.android.ui.maintenance.domain.model.AppMaintenanceConfig
import piuk.blockchain.android.ui.maintenance.domain.repository.AppMaintenanceRepository

internal class AppMaintenanceRepositoryImpl(
    private val appMaintenanceRemoteConfig: AppMaintenanceRemoteConfig,
    private val appUpdateInfoFactory: AppUpdateInfoFactory,
    private val appUpdatePrefs: AppUpdatePrefs,
    private val dispatcher: CoroutineDispatcher
) : AppMaintenanceRepository {

    override suspend fun getAppMaintenanceConfig(): Outcome<Throwable, AppMaintenanceConfig> {
        return supervisorScope {
            val deferredMaintenanceConfig = async(dispatcher) { appMaintenanceRemoteConfig.getAppMaintenanceConfig() }
            val deferredAppUpdateInfo = async { appUpdateInfoFactory.getAppUpdateInfo() }

            val maintenanceConfig = deferredMaintenanceConfig.await()
            val appUpdateInfo: AppUpdateInfo? = try {
                deferredAppUpdateInfo.await()
            } catch (e: Throwable) {
                null
            }

            if (maintenanceConfig == null /*todo <--uncomment for prod--> || appUpdateInfo == null*/) {
                Outcome.Failure(Throwable())
            } else { // todo map
                Outcome.Success(
                    AppMaintenanceConfig(
                        playStoreVersion = /*appUpdateInfo.availableVersionCode()*/16831,
                        bannedVersions = maintenanceConfig.bannedVersions,
                        softUpgradeVersion = maintenanceConfig.softUpgradeVersion,
                        skippedSoftVersion = appUpdatePrefs.skippedVersionCode,
                        minimumOSVersion = maintenanceConfig.minimumOSVersion,
                        siteWideMaintenance = maintenanceConfig.siteWideMaintenance,
                        statusURL = maintenanceConfig.statusURL,
                        storeURI = maintenanceConfig.storeURI,
                        websiteUrl = maintenanceConfig.websiteUrl
                    )
                )
            }
        }
    }

    override fun skipAppUpdate(versionCode: Int) {
        appUpdatePrefs.skippedVersionCode = versionCode
    }
}
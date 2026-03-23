package ru.merrcurys.seacard.core.rustore

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability

/**
 * Отложенное обновление через RuStore In-app updates (см. документацию SDK).
 * После скачивания сразу запускается экран установки (completeUpdate), без отдельной кнопки в приложении.
 */
class RuStoreInAppUpdateController(
    private val activity: ComponentActivity,
) {
    private val manager: RuStoreAppUpdateManager = RuStoreAppUpdateManagerFactory.create(activity)
    private var listenerRegistered = false
    private var installUiStarted = false

    private val installListener = InstallStateUpdateListener { state ->
        when (state.installStatus) {
            InstallStatus.DOWNLOADED -> activity.runOnUiThread { startInstallUiIfNeeded() }
            InstallStatus.FAILED -> Log.e(TAG, "RuStore: ошибка скачивания обновления")
            InstallStatus.DOWNLOAD_INTERRUPTED -> Log.d(TAG, "RuStore: загрузка прервана пользователем")
            else -> Unit
        }
    }

    fun checkOnLaunch() {
        manager.getAppUpdateInfo()
            .addOnSuccessListener { info ->
                when (info.updateAvailability) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        when (info.installStatus) {
                            InstallStatus.DOWNLOADED ->
                                activity.runOnUiThread { startInstallUiIfNeeded() }
                            else -> startFlexibleUpdateFlow(info)
                        }
                    }
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        ensureListenerRegistered()
                    }
                    else -> Unit
                }
            }
            .addOnFailureListener { t -> Log.e(TAG, "getAppUpdateInfo error", t) }
    }

    private fun startFlexibleUpdateFlow(info: AppUpdateInfo) {
        ensureListenerRegistered()
        manager.startUpdateFlow(info, AppUpdateOptions.Builder().build())
            .addOnSuccessListener { resultCode ->
                if (resultCode == Activity.RESULT_CANCELED) {
                    unregisterListenerIfNeeded()
                }
            }
            .addOnFailureListener { t -> Log.e(TAG, "startUpdateFlow error", t) }
    }

    private fun startInstallUiIfNeeded() {
        if (installUiStarted) return
        installUiStarted = true
        unregisterListenerIfNeeded()
        manager.completeUpdate(
            AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build(),
        ).addOnFailureListener { t ->
            Log.e(TAG, "completeUpdate error", t)
            installUiStarted = false
        }
    }

    private fun ensureListenerRegistered() {
        if (!listenerRegistered) {
            manager.registerListener(installListener)
            listenerRegistered = true
        }
    }

    private fun unregisterListenerIfNeeded() {
        if (listenerRegistered) {
            manager.unregisterListener(installListener)
            listenerRegistered = false
        }
    }

    fun dispose() {
        unregisterListenerIfNeeded()
    }

    companion object {
        private const val TAG = "RuStoreInAppUpdate"
    }
}

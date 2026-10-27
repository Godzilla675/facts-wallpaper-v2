package com.factswallpaper.core.backup

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.factswallpaper.core.common.AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AutomaticBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val destinationStore: BackupDestinationStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val status = destinationStore.observeStatus().first()
        if (!status.automaticEnabled) return Result.success()
        val configuration = try {
            destinationStore.loadConfiguration()
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (exception: Exception) {
            destinationStore.recordResult(false, exception.message ?: "Could not unlock backup settings.")
            return Result.failure(workDataOf(KEY_ERROR to "Could not unlock backup settings."))
        } ?: run {
            destinationStore.recordResult(false, "Choose a backup destination and save its passphrase.")
            return Result.failure(workDataOf(KEY_ERROR to "Backup destination is not configured."))
        }

        setProgress(workDataOf(KEY_STATUS to "Creating encrypted backup"))
        return when (
            val result = backupManager.createBackupToUri(
                destination = Uri.parse(configuration.uri),
                passphrase = configuration.passphrase,
                options = configuration.options,
            )
        ) {
            is AppResult.Success -> {
                destinationStore.recordResult(succeeded = true)
                Result.success(
                    workDataOf(
                        KEY_DOCUMENT_COUNT to result.value.documentCount,
                        KEY_FACT_COUNT to result.value.factCount,
                    ),
                )
            }
            is AppResult.Failure -> {
                val detail = result.error.cause?.message ?: result.error.message
                destinationStore.recordResult(false, detail)
                Result.failure(workDataOf(KEY_ERROR to detail))
            }
        }
    }

    companion object {
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_DOCUMENT_COUNT = "documentCount"
        const val KEY_FACT_COUNT = "factCount"
    }
}

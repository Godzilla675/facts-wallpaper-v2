package com.factswallpaper.core.backup

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.pipeline.PipelineNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AutomaticBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val destinationStore: BackupDestinationStore,
    private val pipelineNotifier: PipelineNotifier,
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

        runCatching {
            setForeground(
                pipelineNotifier.pipelineForeground(
                    documentId = "backup",
                    documentTitle = "Facts Wallpaper",
                    stageText = "Creating encrypted backup…",
                    progressFraction = null,
                ),
            )
        }
        setProgress(workDataOf(KEY_STATUS to "Creating encrypted backup"))
        val result = when (
            val backupResult = backupManager.createBackupToUri(
                destination = Uri.parse(configuration.uri),
                passphrase = configuration.passphrase,
                options = configuration.options,
            )
        ) {
            is AppResult.Success -> {
                destinationStore.recordResult(succeeded = true)
                Result.success(
                    workDataOf(
                        KEY_DOCUMENT_COUNT to backupResult.value.documentCount,
                        KEY_FACT_COUNT to backupResult.value.factCount,
                    ),
                )
            }
            is AppResult.Failure -> {
                val detail = backupResult.error.cause?.message ?: backupResult.error.message
                destinationStore.recordResult(false, detail)
                Result.failure(workDataOf(KEY_ERROR to detail))
            }
        }
        pipelineNotifier.cancelNotification("backup")
        return result
    }

    companion object {
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_DOCUMENT_COUNT = "documentCount"
        const val KEY_FACT_COUNT = "factCount"
    }
}

/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.matrix.android.internal.session.sync.job

import android.content.Context
import androidx.work.*
import com.squareup.moshi.JsonClass
import im.vector.matrix.android.api.MatrixCallback
import im.vector.matrix.android.api.util.Cancelable
import im.vector.matrix.android.internal.session.sync.SyncTask
import im.vector.matrix.android.internal.task.TaskExecutor
import im.vector.matrix.android.internal.task.TaskThread
import im.vector.matrix.android.internal.task.configureWith
import im.vector.matrix.android.internal.worker.WorkManagerUtil
import im.vector.matrix.android.internal.worker.WorkManagerUtil.matrixOneTimeWorkRequestBuilder
import im.vector.matrix.android.internal.worker.WorkerParamsFactory
import im.vector.matrix.android.internal.worker.getSessionComponent
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject


private const val DEFAULT_LONG_POOL_TIMEOUT = 0L

internal class SyncWorker(context: Context,
                          workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    @JsonClass(generateAdapter = true)
    internal data class Params(
            val userId: String,
            val timeout: Long = DEFAULT_LONG_POOL_TIMEOUT,
            val automaticallyRetry: Boolean = false
    )

    @Inject
    lateinit var syncTask: SyncTask
    @Inject
    lateinit var taskExecutor: TaskExecutor

    override suspend fun doWork(): Result {
        Timber.i("Sync work starting")
        val params = WorkerParamsFactory.fromData<Params>(inputData) ?: return Result.success()
        val sessionComponent = getSessionComponent(params.userId) ?: return Result.success()
        sessionComponent.inject(this)


        val latch = CountDownLatch(1)
        val taskParams = SyncTask.Params(0)
        cancelableTask = syncTask.configureWith(taskParams)
                .callbackOn(TaskThread.SYNC)
                .executeOn(TaskThread.SYNC)
                .dispatchTo(object : MatrixCallback<Unit> {
                    override fun onSuccess(data: Unit) {
                        latch.countDown()
                    }

                    override fun onFailure(failure: Throwable) {
                        Timber.e(failure)
                        latch.countDown()
                    }

                })
                .executeBy(taskExecutor)

        latch.await()
        return Result.success()
    }

    companion object {


        private var cancelableTask: Cancelable? = null

        fun requireBackgroundSync(context: Context, userId: String, serverTimeout: Long = 0) {
            val data = WorkerParamsFactory.toData(Params(userId, serverTimeout, false))
            val workRequest = matrixOneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(data)
                    .setConstraints(WorkManagerUtil.workConstraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1_000, TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork("BG_SYNCP", ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun automaticallyBackgroundSync(context: Context, userId: String, serverTimeout: Long = 0, delay: Long = 30_000) {
            val data = WorkerParamsFactory.toData(Params(userId, serverTimeout, true))
            val workRequest = matrixOneTimeWorkRequestBuilder<SyncWorker>()
                    .setInputData(data)
                    .setConstraints(WorkManagerUtil.workConstraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, delay, TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork("BG_SYNCP", ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun stopAnyBackgroundSync(context: Context) {
            cancelableTask?.cancel()
            WorkManager.getInstance(context).cancelUniqueWork("BG_SYNCP")
        }
    }

}
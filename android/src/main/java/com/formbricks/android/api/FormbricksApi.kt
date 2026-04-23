package com.formbricks.android.api

import com.formbricks.android.Formbricks
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.user.AttributeValue
import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import com.formbricks.android.network.FormbricksApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object FormbricksApi {
    var service = FormbricksApiService()

    private suspend fun <T> retryApiCall(
        retries: Int = 2,
        delayTime: Long = 1000,
        block: suspend () -> Result<T>
    ): Result<T> {
        repeat(retries) { attempt ->
            val result = block()
            if (result.isSuccess) return result
            println("⚠️ Retry ${attempt + 1} due to error: ${result.exceptionOrNull()?.localizedMessage}")
            delay(delayTime)
        }
        return block()
    }

    fun initialize() {
        service.initialize(
            appUrl = Formbricks.appUrl,
            isLoggingEnabled = Formbricks.loggingEnabled
        )
    }

    suspend fun getWorkspaceState(): Result<WorkspaceDataHolder> = withContext(Dispatchers.IO) {
        retryApiCall {
            try {
                val response = service.getWorkspaceStateObject(Formbricks.workspaceId)
                val result = response.getOrThrow()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun postUser(userId: String, attributes: Map<String, AttributeValue>?): Result<UserResponse> = withContext(Dispatchers.IO) {
        retryApiCall {
            try {
                val result = service.postUser(Formbricks.workspaceId, PostUserBody.create(userId, attributes)).getOrThrow()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

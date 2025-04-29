package com.formbricks.android.api

import com.formbricks.android.Formbricks
import com.formbricks.android.model.environment.EnvironmentDataHolder
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

    suspend fun getEnvironmentState(): Result<EnvironmentDataHolder> = withContext(Dispatchers.IO) {
        retryApiCall {
            try {
                val response = service.getEnvironmentStateObject(Formbricks.environmentId)
                val result = response.getOrThrow()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun postUser(userId: String, attributes: Map<String, *>?): Result<UserResponse> = withContext(Dispatchers.IO) {
        retryApiCall {
            try {
                val result = service.postUser(Formbricks.environmentId, PostUserBody.create(userId, attributes)).getOrThrow()
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
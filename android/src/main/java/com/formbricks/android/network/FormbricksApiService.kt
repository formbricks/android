package com.formbricks.android.network

import com.formbricks.android.api.error.FormbricksAPIError
import com.formbricks.android.helper.mapToJsonElement
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.environment.EnvironmentDataHolder
import com.formbricks.android.model.environment.EnvironmentResponse
import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import com.google.gson.Gson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import retrofit2.Call
import retrofit2.Retrofit

open class FormbricksApiService {
    private var retrofit: Retrofit? = null
    private val callProvider = mutableListOf<Call<*>>()

    fun initialize(appUrl: String, isLoggingEnabled: Boolean) {
        val builder = FormbricksRetrofitBuilder(appUrl, isLoggingEnabled).getBuilder()
        if (builder != null) {
            retrofit = builder.build()
        } else {
            // Builder returned null due to HTTP URL - log error and skip initialization
            val error = RuntimeException("Failed to initialize API service due to invalid URL configuration. Only HTTPS URLs are allowed.")
            Logger.e(error)
            retrofit = null
        }
    }

    open fun getEnvironmentStateObject(environmentId: String): Result<EnvironmentDataHolder> {
        return try {
            val retrofitInstance = retrofit ?: return Result.failure(RuntimeException("API service not initialized due to invalid URL"))
            val result = execute {
                retrofitInstance.create(FormbricksService::class.java)
                    .getEnvironmentState(environmentId)
            }
            val json = Json { ignoreUnknownKeys = true }
            val resultMap = result.getOrThrow()
            val resultJson = mapToJsonElement(resultMap).jsonObject
            val environmentResponse = json.decodeFromJsonElement<EnvironmentResponse>(resultJson)
            val data = EnvironmentDataHolder(environmentResponse.data, resultMap)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open fun postUser(environmentId: String, body: PostUserBody): Result<UserResponse> {
        val retrofitInstance = retrofit ?: return Result.failure(RuntimeException("API service not initialized due to invalid URL"))
        return execute {
            retrofitInstance.create(FormbricksService::class.java)
                .postUser(environmentId, body)
        }
    }

    private inline fun <T> execute(apiCall: () -> Call<T>): Result<T> {
        val callInstance = apiCall()
        callProvider.add(callInstance)
        val call = callInstance.execute()
        return if (call.isSuccessful) {
            val body = call.body()
            if (body == null) {
                Result.failure(RuntimeException("Invalid response"))

            } else {
                Result.success(body)
            }
        } else {
            return try {
                val errorResponse =
                    Gson().fromJson(call.errorBody()?.string(), FormbricksAPIError::class.java)
                Result.failure(errorResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun cancelCallApi() {
        callProvider.map { it.cancel() }
        callProvider.clear()
    }
}
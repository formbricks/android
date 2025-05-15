package com.formbricks.android.network

import com.formbricks.android.api.error.FormbricksAPIError
import com.formbricks.android.helper.mapToJsonElement
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

    private lateinit var retrofit: Retrofit

    fun initialize(appUrl: String, isLoggingEnabled: Boolean) {
        retrofit = FormbricksRetrofitBuilder(appUrl, isLoggingEnabled)
            .getBuilder()
            .build()
    }

    open fun getEnvironmentStateObject(environmentId: String): Result<EnvironmentDataHolder> {
        return try {
            val result = execute {
                retrofit.create(FormbricksService::class.java)
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
        return execute {
            retrofit.create(FormbricksService::class.java)
                .postUser(environmentId, body)
        }
    }

    private inline fun <T> execute(apiCall: () -> Call<T>): Result<T> {
        val call = apiCall().execute()
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
}
package com.formbricks.android

import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.model.environment.EnvironmentDataHolder
import com.formbricks.android.model.environment.EnvironmentResponse
import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import com.formbricks.android.network.FormbricksApiService
import com.google.gson.Gson
import com.formbricks.android.model.error.SDKError

class MockFormbricksApiService: FormbricksApiService() {
    private val gson = Gson()
    private val environment: EnvironmentResponse
    private val user: UserResponse
    var isErrorResponseNeeded = false

    init {
        val context = InstrumentationRegistry.getInstrumentation().context
        val environmentJson = context.assets.open("Environment.json").bufferedReader().readText()
        val userJson = context.assets.open("User.json").bufferedReader().readText()
        
        environment = gson.fromJson(environmentJson, EnvironmentResponse::class.java)
        user = gson.fromJson(userJson, UserResponse::class.java)
    }

    override fun getEnvironmentStateObject(environmentId: String): Result<EnvironmentDataHolder> {
        return if (isErrorResponseNeeded) {
            Result.failure(SDKError.unableToRefreshEnvironment)
        } else {
            Result.success(EnvironmentDataHolder(environment.data, mapOf()))
        }
    }

    override fun postUser(environmentId: String, body: PostUserBody): Result<UserResponse> {
        return if (isErrorResponseNeeded) {
            Result.failure(SDKError.unableToPostResponse)
        } else {
            Result.success(user)
        }
    }
}
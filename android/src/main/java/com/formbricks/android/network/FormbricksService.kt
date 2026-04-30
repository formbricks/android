package com.formbricks.android.network

import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FormbricksService {
    @GET("$API_PREFIX/client/{workspaceId}/environment")
    fun getWorkspaceState(@Path("workspaceId") workspaceId: String): Call<Map<String, Any>>

    @POST("$API_PREFIX/client/{workspaceId}/user")
    fun postUser(@Path("workspaceId") workspaceId: String, @Body body: PostUserBody): Call<UserResponse>

    companion object {
        const val API_PREFIX = "/api/v2"
    }
}

package com.formbricks.android.network

import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

public interface FormbricksService {
    @GET("$API_PREFIX/client/{workspaceId}/environment")
    public fun getWorkspaceState(@Path("workspaceId") workspaceId: String): Call<Map<String, Any>>

    @POST("$API_PREFIX/client/{workspaceId}/user")
    public fun postUser(
        @Path("workspaceId") workspaceId: String,
        @Body body: PostUserBody
    ): Call<UserResponse>

    public companion object {
        public const val API_PREFIX: String = "/api/v2"
    }
}

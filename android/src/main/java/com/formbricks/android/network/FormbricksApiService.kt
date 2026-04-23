package com.formbricks.android.network

import com.formbricks.android.api.error.FormbricksAPIError
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.workspace.SegmentFilterResource
import com.formbricks.android.model.workspace.SegmentFilterResourceDeserializer
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.workspace.WorkspaceResponse
import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import retrofit2.Call
import retrofit2.Retrofit

open class FormbricksApiService {
    private var retrofit: Retrofit? = null

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(
            SegmentFilterResource::class.java,
            SegmentFilterResourceDeserializer()
        )
        .create()

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

    open fun getWorkspaceStateObject(workspaceId: String): Result<WorkspaceDataHolder> {
        return try {
            val retrofitInstance = retrofit ?: return Result.failure(RuntimeException("API service not initialized due to invalid URL"))
            val result = execute {
                retrofitInstance.create(FormbricksService::class.java)
                    .getWorkspaceState(workspaceId)
            }
            val resultMap = result.getOrThrow()
            normalizeWorkspaceKeys(resultMap)
            // Use Gson end-to-end so `@SerializedName(alternate=[...])` handles all
            // the workspace-rename compatibility cases (settings/workspace/project,
            // workspaceId/environmentId) and unknown server fields are ignored.
            val resultJson = gson.toJson(resultMap)
            val workspaceResponse = gson.fromJson(resultJson, WorkspaceResponse::class.java)
            val data = WorkspaceDataHolder(workspaceResponse.data, resultMap)
            Result.success(data)
        } catch (e: Exception) {
            Logger.e(RuntimeException("Failed to parse workspace state: ${e.message}", e))
            Result.failure(e)
        }
    }

    /**
     * Server may respond with `settings` (new), `workspace` (interim), or legacy
     * `project` — plus sometimes more than one simultaneously. Pick one in order
     * of preference and drop the rest so downstream decode only sees `settings`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun normalizeWorkspaceKeys(resultMap: Map<String, Any>) {
        val outer = resultMap["data"] as? MutableMap<String, Any> ?: return
        val inner = outer["data"] as? MutableMap<String, Any> ?: return
        val replacement = inner["settings"] ?: inner["workspace"] ?: inner["project"] ?: return
        inner.remove("workspace")
        inner.remove("project")
        inner["settings"] = replacement
    }

    open fun postUser(workspaceId: String, body: PostUserBody): Result<UserResponse> {
        val retrofitInstance = retrofit ?: return Result.failure(RuntimeException("API service not initialized due to invalid URL"))
        return execute {
            retrofitInstance.create(FormbricksService::class.java)
                .postUser(workspaceId, body)
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

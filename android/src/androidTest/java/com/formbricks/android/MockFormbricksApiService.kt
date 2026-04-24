package com.formbricks.android

import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.workspace.WorkspaceResponse
import com.formbricks.android.model.user.PostUserBody
import com.formbricks.android.model.user.UserResponse
import com.formbricks.android.network.FormbricksApiService
import com.google.gson.Gson
import com.formbricks.android.model.error.SDKError

class MockFormbricksApiService: FormbricksApiService() {
    private val gson = Gson()
    private val workspace: WorkspaceResponse
    internal var user: UserResponse
    var isErrorResponseNeeded = false

    init {
        val context = InstrumentationRegistry.getInstrumentation().context
        val workspaceJson = context.assets.open("Workspace.json").bufferedReader().readText()
        val userJson = context.assets.open("User.json").bufferedReader().readText()

        workspace = gson.fromJson(workspaceJson, WorkspaceResponse::class.java)
        user = gson.fromJson(userJson, UserResponse::class.java)
    }

    override fun getWorkspaceStateObject(workspaceId: String): Result<WorkspaceDataHolder> {
        return if (isErrorResponseNeeded) {
            Result.failure(SDKError.unableToRefreshEnvironment)
        } else {
            Result.success(WorkspaceDataHolder(workspace.data, mapOf()))
        }
    }

    override fun postUser(workspaceId: String, body: PostUserBody): Result<UserResponse> {
        return if (isErrorResponseNeeded) {
            Result.failure(SDKError.unableToPostResponse)
        } else {
            Result.success(user)
        }
    }
}

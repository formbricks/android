package com.formbricks.android.model.user

import com.google.gson.annotations.SerializedName

data class UserResponseData(
    @SerializedName("state") val state: UserState
)

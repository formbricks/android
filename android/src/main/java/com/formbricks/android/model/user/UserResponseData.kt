package com.formbricks.android.model.user

import com.google.gson.annotations.SerializedName

data class UserResponseData(
    @SerializedName("state") val state: UserState,
    @SerializedName("messages") val messages: List<String>? = null,
    @SerializedName("errors") val errors: List<String>? = null
)

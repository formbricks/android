package com.formbricks.android.model.user

import com.google.gson.annotations.SerializedName

public data class UserResponse(@SerializedName("data") val data: UserResponseData)

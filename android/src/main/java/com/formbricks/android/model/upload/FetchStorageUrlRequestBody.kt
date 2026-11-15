package com.formbricks.android.model.upload

import com.google.gson.annotations.SerializedName

public data class FetchStorageUrlRequestBody(
    @SerializedName("fileName") val fileName: String,
    @SerializedName("fileType") val fileType: String,
    @SerializedName("allowedFileExtensions") val allowedFileExtensions: List<String>?,
    @SerializedName("surveyId") val surveyId: String,
    @SerializedName("accessType") val accessType: String
) {
    public companion object {
        public fun create(
            fileName: String,
            fileType: String,
            allowedFileExtensions: List<String>?,
            surveyId: String,
            accessType: String = "public"
        ): FetchStorageUrlRequestBody = FetchStorageUrlRequestBody(
            fileName,
            fileType,
            allowedFileExtensions,
            surveyId,
            accessType
        )
    }
}

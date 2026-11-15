package com.formbricks.android.model.javascript

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

public data class FileUploadData(
    @SerializedName("event") val event: EventType,
    @SerializedName("fileUploadParams") val fileUploadParams: FileUploadParams
) {

    public companion object {
        public fun from(string: String): FileUploadData =
            Gson().fromJson(string, FileUploadData::class.java)
    }
}

public data class FileUploadParams(
    @SerializedName("allowedFileExtensions") val allowedFileExtensions: String?,
    @SerializedName("allowMultipleFiles") val allowMultipleFiles: Boolean
) {
    public fun allowedExtensionsArray(): Array<String> = allowedFileExtensions?.split(",")?.map {
        it
    }?.toTypedArray() ?: arrayOf()
}

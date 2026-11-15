package com.formbricks.android.logger

import android.util.Log
import com.formbricks.android.Formbricks

public object Logger {
    public fun d(message: String) {
        if (Formbricks.loggingEnabled) {
            Log.d("FormbricksSDK", message)
        }
    }

    public fun e(exception: RuntimeException) {
        if (Formbricks.loggingEnabled) {
            Log.e("FormbricksSDK", exception.localizedMessage, exception)
        }
    }

    public fun w(message: String? = "Warning", exception: RuntimeException? = null) {
        if (Formbricks.loggingEnabled) {
            Log.w("FormbricksSDK", message, exception)
        }
    }
}

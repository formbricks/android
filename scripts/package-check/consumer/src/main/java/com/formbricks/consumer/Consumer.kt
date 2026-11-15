package com.formbricks.consumer

import android.content.Context
import com.formbricks.android.Formbricks
import com.formbricks.android.helper.FormbricksConfig

/** Calls the SDK's main entry points, so the build fails if the published API cannot be used. */
fun useFormbricks(context: Context) {
    val config = FormbricksConfig.Builder("https://app.formbricks.com", "workspace-id").build()
    Formbricks.setup(context, config)
    Formbricks.setUserId("user-id")
    Formbricks.setAttribute("pro", "plan")
    Formbricks.track("qa-action")
    Formbricks.logout()
}

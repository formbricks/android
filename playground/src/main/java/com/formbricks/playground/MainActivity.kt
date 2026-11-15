package com.formbricks.playground

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formbricks.android.Formbricks
import com.formbricks.android.helper.FormbricksConfig
import com.formbricks.playground.databinding.ActivityMainBinding

/**
 * Sets the SDK up with a server URL and workspace, then offers one button per SDK call the golden
 * journey makes: set user, set attribute, track an action.
 *
 * The URL and workspace come from the launching intent ([intent]), else from the build
 * (`-Pformbricks.appUrl`, `-Pformbricks.workspaceId`; see build.gradle.kts).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appUrl = intent.getStringExtra(EXTRA_APP_URL) ?: BuildConfig.FORMBRICKS_APP_URL
        val workspaceId =
            intent.getStringExtra(EXTRA_WORKSPACE_ID) ?: BuildConfig.FORMBRICKS_WORKSPACE_ID

        // setup() is once per process; a recreated Activity only hands over its new FragmentManager.
        Formbricks.setup(
            this,
            FormbricksConfig
                .Builder(appUrl, workspaceId)
                .setLoggingEnabled(true)
                .setFragmentManager(supportFragmentManager)
                .build()
        )
        Formbricks.setFragmentManager(supportFragmentManager)
        binding.setupStatus.text = getString(R.string.setup_status, appUrl, workspaceId)

        binding.setUserId.setOnClickListener {
            val userId = binding.userId.text.toString()
            Formbricks.setUserId(userId)
            binding.lastCall.text = getString(R.string.status_user_id, userId)
        }
        binding.setAttribute.setOnClickListener {
            val key = binding.attributeKey.text.toString()
            val value = binding.attributeValue.text.toString()
            Formbricks.setAttribute(value, key)
            binding.lastCall.text = getString(R.string.status_attribute, key, value)
        }
        binding.trackAction.setOnClickListener {
            val action = binding.actionKey.text.toString()
            Formbricks.track(action)
            binding.lastCall.text = getString(R.string.status_track, action)
        }
        binding.logOut.setOnClickListener {
            Formbricks.logout()
            binding.lastCall.text = getString(R.string.status_logout)
        }
    }

    companion object {
        const val EXTRA_APP_URL = "com.formbricks.playground.APP_URL"
        const val EXTRA_WORKSPACE_ID = "com.formbricks.playground.WORKSPACE_ID"

        fun intent(context: Context, appUrl: String, workspaceId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_APP_URL, appUrl)
                .putExtra(EXTRA_WORKSPACE_ID, workspaceId)
    }
}

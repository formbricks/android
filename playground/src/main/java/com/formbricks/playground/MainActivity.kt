package com.formbricks.playground

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.formbricks.android.Formbricks
import com.formbricks.android.helper.FormbricksConfig
import com.formbricks.playground.databinding.ActivityMainBinding

/**
 * Sets the SDK up with a server URL and workspace, then offers one button per SDK call the golden
 * journey makes: set user, set attribute, track an action.
 *
 * The URL and workspace are fixed at build time (`-Pformbricks.appUrl`, `-Pformbricks.workspaceId`;
 * see build.gradle.kts), never read from the launching intent: any app can send an exported
 * activity an intent, and the URL decides where the SDK sends its requests.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appUrl = BuildConfig.FORMBRICKS_APP_URL
        val workspaceId = BuildConfig.FORMBRICKS_WORKSPACE_ID

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
}

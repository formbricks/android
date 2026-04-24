package com.formbricks.android.manager

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.Formbricks
import com.formbricks.android.model.workspace.WorkspaceResponse
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the workspace-rename backwards-compatibility surface:
 *  - server payloads that still use the legacy `project` key, the interim `workspace`
 *    key, and the new `settings` key
 *  - on-disk cache blobs written by older SDK versions under the pre-rename
 *    `formbricksDataHolder` SharedPreferences key
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceDataMigrationInstrumentedTest {

    private val prefsName = "formbricks_prefs"

    private fun prefs() =
        Formbricks.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        Formbricks.applicationContext = InstrumentationRegistry.getInstrumentation().targetContext
        prefs().edit().clear().apply()
        setBackingWorkspaceDataHolder(null)
    }

    @After
    fun tearDown() {
        prefs().edit().clear().apply()
        setBackingWorkspaceDataHolder(null)
    }

    @Test
    fun testWorkspaceDataDecodesFromSettingsKey() {
        val json = """
            {
              "data": {
                "data": {
                  "settings": {
                    "recontactDays": 7,
                    "clickOutsideClose": true,
                    "overlay": "none",
                    "placement": "bottomRight",
                    "inAppSurveyBranding": true,
                    "styling": { "allowStyleOverwrite": true }
                  },
                  "surveys": [],
                  "actionClasses": []
                },
                "expiresAt": "2099-12-31T23:59:59.999Z"
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, WorkspaceResponse::class.java)
        assertEquals(7.0, response.data.data.settings.recontactDays)
        assertEquals("bottomRight", response.data.data.settings.placement)
    }

    @Test
    fun testWorkspaceDataDecodesFromWorkspaceKey() {
        val json = """
            {
              "data": {
                "data": {
                  "workspace": {
                    "recontactDays": 3,
                    "clickOutsideClose": false,
                    "overlay": "none",
                    "placement": "center",
                    "inAppSurveyBranding": false,
                    "styling": { "allowStyleOverwrite": false }
                  },
                  "surveys": [],
                  "actionClasses": []
                },
                "expiresAt": "2099-12-31T23:59:59.999Z"
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, WorkspaceResponse::class.java)
        assertEquals(3.0, response.data.data.settings.recontactDays)
        assertEquals("center", response.data.data.settings.placement)
    }

    @Test
    fun testWorkspaceDataDecodesFromLegacyProjectKey() {
        val json = """
            {
              "data": {
                "data": {
                  "project": {
                    "recontactDays": 14,
                    "clickOutsideClose": true,
                    "overlay": "none",
                    "placement": "bottomLeft",
                    "inAppSurveyBranding": true,
                    "styling": { "allowStyleOverwrite": true }
                  },
                  "surveys": [],
                  "actionClasses": []
                },
                "expiresAt": "2099-12-31T23:59:59.999Z"
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, WorkspaceResponse::class.java)
        assertEquals(14.0, response.data.data.settings.recontactDays)
        assertEquals("bottomLeft", response.data.data.settings.placement)
    }

    /**
     * A cache blob written under the pre-rename SharedPreferences key should be
     * copied to the new key and dropped from the legacy slot when
     * `migrateLegacyCacheIfNeeded` runs at setup time.
     */
    @Test
    fun testLegacyCachedDataHolderIsMigratedOnSetup() {
        val legacyBlob = """
            {
              "data": {
                "data": {
                  "surveys": [],
                  "actionClasses": [],
                  "project": {
                    "id": "p1",
                    "recontactDays": 7,
                    "clickOutsideClose": true,
                    "overlay": "none",
                    "placement": "bottomRight",
                    "inAppSurveyBranding": true,
                    "styling": { "allowStyleOverwrite": true }
                  }
                },
                "expiresAt": "2099-12-31T23:59:59.999Z"
              },
              "originalResponseMap": {}
            }
        """.trimIndent()

        prefs().edit()
            .putString(SurveyManager.PREF_LEGACY_ENVIRONMENT_DATA_HOLDER, legacyBlob)
            .apply()

        SurveyManager.migrateLegacyCacheIfNeeded()

        val holder = SurveyManager.workspaceDataHolder
        assertNotNull("Migrated cache should be read and decoded", holder)
        assertEquals("bottomRight", holder?.data?.data?.settings?.placement)

        // Legacy key is gone, new key is populated.
        assertNull(prefs().getString(SurveyManager.PREF_LEGACY_ENVIRONMENT_DATA_HOLDER, null))
        assertTrue(prefs().contains(SurveyManager.PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER))
    }

    private fun setBackingWorkspaceDataHolder(value: Any?) {
        val field = SurveyManager::class.java.getDeclaredField("backingWorkspaceDataHolder")
        field.isAccessible = true
        field.set(SurveyManager, value)
    }
}

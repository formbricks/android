package com.formbricks.android.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.model.user.PostUserBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormbricksApiServiceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var apiService: FormbricksApiService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        apiService = FormbricksApiService()
        // You may want to initialize with a test server or mock URL
        apiService.initialize("https://example.com", isLoggingEnabled = false)
    }

    @Test
    fun testInitialization() {
        // This test just verifies that initialization does not throw
        try {
            apiService.initialize("https://example.com", isLoggingEnabled = false)
        } catch (e: Exception) {
            fail("Initialization should not throw: ${e.message}")
        }
    }

    @Test
    fun testGetEnvironmentStateObject_handlesErrorGracefully() {
        val result = apiService.getEnvironmentStateObject("dummy-environment-id")
        assertTrue(result.isFailure)
        result.exceptionOrNull()?.let { e ->
            println("Exception caught as expected: ${e.message}")
        }
    }

    @Test
    fun testPostUser_handlesErrorGracefully() {
        // This should fail gracefully since the URL is unreachable
        val dummyBody = PostUserBody("dummy-user-id", null)
        val result = apiService.postUser("dummy-environment-id", dummyBody)
        assertTrue(result.isFailure)
    }

    // Add more integration-style tests as needed, e.g.:
    // - testGetEnvironmentStateObject_withMockServer
    // - testPostUser_withMockServer
    // These would require a running test server or a mock web server
} 
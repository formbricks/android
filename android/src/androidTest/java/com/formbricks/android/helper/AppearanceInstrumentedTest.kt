package com.formbricks.android.helper

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.Formbricks
import com.formbricks.android.model.workspace.CustomCss
import com.formbricks.android.model.workspace.Settings
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceInstrumentedTest {
    private val night = Configuration.UI_MODE_NIGHT_YES
    private val day = Configuration.UI_MODE_NIGHT_NO

    @Before
    @After
    fun reset() = Appearance.reset()

    @Test
    fun defaultsToLightWhateverTheAppTheme() {
        assertEquals(FormbricksAppearance.LIGHT, Appearance.current)
        assertEquals("light", Appearance.resolve(night))
    }

    @Test
    fun lightAndDarkWinOverTheAppTheme() {
        Appearance.set(FormbricksAppearance.LIGHT)
        assertEquals("light", Appearance.resolve(night))
        Appearance.set(FormbricksAppearance.DARK)
        assertEquals("dark", Appearance.resolve(day))
    }

    @Test
    fun systemFollowsTheAppNightModeAndNeverResolvesToSystem() {
        Appearance.set(FormbricksAppearance.SYSTEM)
        assertEquals("dark", Appearance.resolve(night or 0x01))
        assertEquals("light", Appearance.resolve(day or 0x01))
        assertEquals("light", Appearance.resolve(0))
    }

    @Test
    fun systemResolvesFromAContextConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Appearance.set(FormbricksAppearance.SYSTEM)
        val expected = if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        ) "dark" else "light"
        assertEquals(expected, Appearance.resolve(context))
    }

    @Test
    fun publicApiWorksBeforeSetupFromEnumOrString() {
        Formbricks.setAppearance(FormbricksAppearance.DARK)
        assertEquals(FormbricksAppearance.DARK, Appearance.current)
        Formbricks.setAppearance("system")
        assertEquals(FormbricksAppearance.SYSTEM, Appearance.current)
    }

    @Test
    fun anUnknownStringFallsBackToLight() {
        Appearance.set("dark")
        Appearance.set("sepia")
        assertEquals(FormbricksAppearance.LIGHT, Appearance.current)
        assertNull(FormbricksAppearance.from(null))
        assertEquals(FormbricksAppearance.DARK, FormbricksAppearance.from(" Dark "))
    }

    @Test
    fun configBuilderCarriesTheAppearance() {
        val withIt = FormbricksConfig.Builder("https://app.formbricks.com", "ws")
            .setAppearance(FormbricksAppearance.DARK)
            .build()
        assertEquals(FormbricksAppearance.DARK, withIt.appearance)
        assertNull(FormbricksConfig.Builder("https://app.formbricks.com", "ws").build().appearance)
    }

    @Test
    fun listenersHearChangesUntilRemoved() {
        var calls = 0
        val listener = { calls++; Unit }
        Appearance.addListener(listener)
        Appearance.set(FormbricksAppearance.DARK)
        assertEquals(1, calls)
        Appearance.removeListener(listener)
        Appearance.set(FormbricksAppearance.LIGHT)
        assertEquals(1, calls)
    }

    @Test
    fun switchScriptToleratesAnOlderRenderer() {
        assertEquals("window.formbricksSurveys?.setAppearance?.('dark');", Appearance.switchScript("dark"))
    }

    @Test
    fun noCustomCssSendsNoKey() {
        assertNull(CustomCss.props(null, null))
        assertNull(CustomCss.props(CustomCss("", null), CustomCss()))
    }

    @Test
    fun customCssIsForwardedUntouchedWithEmptyFieldsOmitted() {
        val props = CustomCss.props(CustomCss(light = ".a{color:red}"), CustomCss(dark = ".b{margin:0}"))!!
        assertEquals(".a{color:red}", props["workspace"].asJsonObject["light"].asString)
        assertFalse(props["workspace"].asJsonObject.has("dark"))
        assertEquals(".b{margin:0}", props["survey"].asJsonObject["dark"].asString)
    }

    @Test
    fun settingsDecodeWithAndWithoutCustomCss() {
        val with = Gson().fromJson("""{"customCss":{"light":".a{}"}}""", Settings::class.java)
        assertTrue(with.customCss?.light == ".a{}")
        assertNull(Gson().fromJson("{}", Settings::class.java).customCss)
    }
}

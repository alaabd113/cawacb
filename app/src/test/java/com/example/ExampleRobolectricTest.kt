package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.WeatherRepository
import com.example.domain.ActiveDrawer
import com.example.domain.CosmosMode
import com.example.domain.StudioPreset
import com.example.ui.CosmosScreen
import com.example.ui.theme.CosmosTheme
import com.example.viewmodel.CosmosViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name resource matches Cosmos IX`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Cosmos IX", context.getString(R.string.app_name))
    }

    @Test
    fun `weather repository parses valid open-meteo payload and handles missing current object`() {
        val repo = WeatherRepository()
        val validJson = """
            {"current":{"temperature_2m":27.4,"cloud_cover":15,"weather_code":0}}
        """.trimIndent()
        val parsed = repo.parseOpenMeteoJson(validJson)
        assertTrue(parsed.isLive)
        assertEquals(15, parsed.cloudCoverPercent)
        assertEquals(27.4, parsed.temperatureC, 1e-4)
        assertTrue(parsed.descriptionAr.contains("سماء صافية"))

        // Negative / missing field case
        val missingCurrent = repo.parseOpenMeteoJson("{}")
        assertFalse(missingCurrent.isLive)
        assertEquals(28, missingCurrent.cloudCoverPercent)
    }

    @Test
    fun `cosmos ui switches between space black hole earth sky and planet studio`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CosmosViewModel(app)
        viewModel.dismissSplash()

        composeTestRule.setContent {
            CosmosTheme {
                CosmosScreen(viewModel = viewModel, enableGlSurface = false)
            }
        }

        // Initial Space mode displays Telemetry card and Bottom dock controls
        composeTestRule.onNodeWithTag("info_card_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_studio_button").assertIsDisplayed()

        // Switch to Helical Solar Vortex mode and verify auto warp + Vortex HUD
        composeTestRule.onNodeWithTag("mode_helical").performScrollTo().performClick()
        assertEquals(CosmosMode.HELICAL, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.settings.helicalMotion)
        assertTrue("Helical mode engages vortex speed >= 30d/s", viewModel.uiState.value.speedIndex >= 4)
        composeTestRule.onNodeWithTag("helical_vortex_hud").assertIsDisplayed()

        // Switch to Kerr Black Hole Lab mode (scroll horizontal mode bar first on compact screens)
        composeTestRule.onNodeWithTag("mode_black_hole").performScrollTo().performClick()
        assertEquals(CosmosMode.BLACK_HOLE, viewModel.uiState.value.mode)
        composeTestRule.onNodeWithTag("bh_dive_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bh_dive_button").performClick()
        assertTrue(viewModel.uiState.value.blackHole.divingToHorizon)

        // Switch to Earth Sky ("سماؤك الآن") mode & verify observatory HUD
        composeTestRule.onNodeWithTag("mode_earth_sky").performScrollTo().performClick()
        assertEquals(CosmosMode.EARTH_SKY, viewModel.uiState.value.mode)
        composeTestRule.onNodeWithTag("earth_sky_hud").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gyro_toggle_button").assertIsDisplayed()
        assertTrue(viewModel.uiState.value.skyTelemetry.moonPhaseNameAr.isNotBlank())

        // Return to Space & open Planet Studio drawer
        composeTestRule.onNodeWithTag("mode_space").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("open_studio_button").performScrollTo().performClick()
        assertEquals(ActiveDrawer.STUDIO, viewModel.uiState.value.activeDrawer)
        composeTestRule.onNodeWithTag("studio_preset_cinema_hdr").performScrollTo().performClick()
        assertEquals(StudioPreset.CINEMA_HDR, viewModel.uiState.value.studio.preset)
        composeTestRule.onNodeWithTag("close_drawer_button").performClick()
        assertEquals(ActiveDrawer.NONE, viewModel.uiState.value.activeDrawer)

        // Toggle Einstein Spacetime curvature grid
        assertFalse(viewModel.uiState.value.settings.spacetimeGrid)
        composeTestRule.onNodeWithTag("toggle_spacetime_button").performScrollTo().performClick()
        assertTrue(viewModel.uiState.value.settings.spacetimeGrid)

        // Enter and exit Cinema Mode
        composeTestRule.onNodeWithTag("cinema_button").performClick()
        assertTrue(viewModel.uiState.value.cinemaMode)
        composeTestRule.onNodeWithTag("exit_cinema_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_cinema_button").performClick()
        assertFalse(viewModel.uiState.value.cinemaMode)
    }
}

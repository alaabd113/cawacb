package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.SnapshotSaver
import com.example.domain.ActiveDrawer
import com.example.domain.CosmosMode
import com.example.engine.CosmosGlRenderer
import com.example.engine.CosmosGlSurfaceView
import com.example.sensors.SkyGyroscopeController
import com.example.ui.components.BlackHoleLabHudCard
import com.example.ui.components.BottomControlDock
import com.example.ui.components.EarthSkyHudCard
import com.example.ui.components.HelicalVortexHudCard
import com.example.ui.components.LuxuryDrawerHost
import com.example.ui.components.ProjectedLabelsOverlay
import com.example.ui.components.SplashOverlay
import com.example.ui.components.TelemetryInfoCard
import com.example.ui.components.TopCommandBar
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.ObsidianSheet
import com.example.ui.theme.ObsidianVoid
import com.example.viewmodel.CosmosViewModel

@Composable
fun CosmosScreen(
    viewModel: CosmosViewModel,
    enableGlSurface: Boolean = true
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val labels by viewModel.projectedLabels.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var glSurfaceRef by remember { mutableStateOf<CosmosGlSurfaceView?>(null) }

    val renderer = remember {
        CosmosGlRenderer { bhTelemetry, projectedLabels, yawRad, skyAz, skyAlt ->
            viewModel.onRendererTelemetry(bhTelemetry, projectedLabels, yawRad, skyAz, skyAlt)
        }
    }

    val gyroController = remember(context) {
        SkyGyroscopeController(context) { az, alt ->
            renderer.applyGyroOrientation(az, alt)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setGyroAvailability(gyroController.isAvailable)
        if (enableGlSurface) {
            viewModel.startStreamingNasaTextures { key, bmp ->
                renderer.enqueueTexture(key, bmp)
            }
        }
    }

    LaunchedEffect(state) {
        renderer.updateState(state)
    }

    DisposableEffect(lifecycleOwner, glSurfaceRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceRef?.onPause()
                    gyroController.setActive(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gyroController.release()
        }
    }

    BackHandler(enabled = state.cinemaMode) {
        viewModel.toggleCinemaMode()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid)
        ) {
            val isWideScreen = maxWidth >= 780.dp
            val isCompactWidth = maxWidth < 400.dp

            // Layer 1: Full-Bleed Hardware-Accelerated OpenGL ES 3.0 Relativistic Viewport
            if (enableGlSurface) {
                AndroidView(
                    factory = { ctx ->
                        CosmosGlSurfaceView(ctx, renderer).also { glSurfaceRef = it }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("cosmos_gl_viewport")
                )
            }

            // Layer 2: Dynamic Projected Astronomical Labels
            ProjectedLabelsOverlay(
                labels = labels,
                visible = state.settings.labels && !state.cinemaMode
            )

            // Layer 3 & 4: Luxury Top Command Bar + Floating Side HUDs (stacked cleanly without overlap)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                AnimatedVisibility(
                    visible = !state.cinemaMode,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    TopCommandBar(
                        state = state,
                        isWideScreen = isWideScreen,
                        isCompactWidth = isCompactWidth,
                        onSelectMode = viewModel::selectMode,
                        onToggleAudio8D = viewModel::toggleAudio8D,
                        onTakeSnapshot = {
                            val sv = glSurfaceRef
                            if (sv != null) {
                                SnapshotSaver.captureAndSave(context, sv) { ok ->
                                    viewModel.showToast(
                                        context.getString(
                                            if (ok) R.string.toast_snapshot_saved else R.string.toast_snapshot_failed
                                        )
                                    )
                                }
                            } else {
                                viewModel.showToast(context.getString(R.string.toast_snapshot_failed))
                            }
                        },
                        onToggleCinema = viewModel::toggleCinemaMode
                    )
                }

                AnimatedVisibility(
                    visible = !state.cinemaMode && state.activeDrawer == ActiveDrawer.NONE,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 2.dp)
                ) {
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (state.mode == CosmosMode.SPACE || state.mode == CosmosMode.GALAXY) {
                                TelemetryInfoCard(
                                    state = state,
                                    onToggleExpand = viewModel::toggleInfoExpanded
                                )
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.mode == CosmosMode.HELICAL) {
                                    HelicalVortexHudCard(state = state)
                                }
                                if (state.mode == CosmosMode.EARTH_SKY) {
                                    EarthSkyHudCard(
                                        state = state,
                                        onToggleGyro = {
                                            viewModel.toggleGyroscope { enable -> gyroController.setActive(enable) }
                                        }
                                    )
                                }
                                if (state.mode == CosmosMode.BLACK_HOLE) {
                                    BlackHoleLabHudCard(
                                        state = state,
                                        onSelectPreset = viewModel::selectBlackHolePreset,
                                        onToggleDive = viewModel::toggleDiveToHorizon,
                                        onOpenPhysicsSheet = { viewModel.openDrawer(ActiveDrawer.BLACK_HOLE) }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (state.mode) {
                                CosmosMode.HELICAL -> HelicalVortexHudCard(state = state)
                                CosmosMode.EARTH_SKY -> EarthSkyHudCard(
                                    state = state,
                                    onToggleGyro = {
                                        viewModel.toggleGyroscope { enable -> gyroController.setActive(enable) }
                                    }
                                )
                                CosmosMode.BLACK_HOLE -> BlackHoleLabHudCard(
                                    state = state,
                                    onSelectPreset = viewModel::selectBlackHolePreset,
                                    onToggleDive = viewModel::toggleDiveToHorizon,
                                    onOpenPhysicsSheet = { viewModel.openDrawer(ActiveDrawer.BLACK_HOLE) }
                                )
                                else -> TelemetryInfoCard(
                                    state = state,
                                    onToggleExpand = viewModel::toggleInfoExpanded
                                )
                            }
                        }
                    }
                }
            }

            // Layer 5: Floating Bottom Control Dock
            AnimatedVisibility(
                visible = !state.cinemaMode && state.activeDrawer == ActiveDrawer.NONE,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomControlDock(
                    state = state,
                    onSelectBody = viewModel::selectBody,
                    onSelectBlackHole = { viewModel.selectMode(CosmosMode.BLACK_HOLE) },
                    onTogglePlay = viewModel::togglePlay,
                    onToggleReverse = viewModel::toggleReverse,
                    onResetNow = viewModel::resetToNow,
                    onSelectSpeed = viewModel::selectSpeed,
                    onOpenStudio = { viewModel.openDrawer(ActiveDrawer.STUDIO) },
                    onToggleSpacetime = viewModel::toggleSpacetimeGrid,
                    onOpenPlaces = { viewModel.openDrawer(ActiveDrawer.PLACES) },
                    onOpenSettings = { viewModel.openDrawer(ActiveDrawer.SETTINGS) }
                )
            }

            // Layer 6: Luxury Interactive Sheets (Studio, Places, Black Hole Physics, Settings)
            LuxuryDrawerHost(
                state = state,
                onClose = viewModel::closeDrawer,
                onApplyStudioPreset = viewModel::applyStudioPreset,
                onUpdateStudio = viewModel::updateStudio,
                onSelectCity = viewModel::selectCity,
                onFlyToCity = viewModel::flyToSelectedCity,
                onViewCitySky = viewModel::viewSelectedCitySky,
                onUpdateBlackHole = viewModel::updateBlackHole,
                onUpdateSettings = viewModel::updateSettings,
                onSelectQuality = viewModel::selectQuality,
                modifier = Modifier.align(if (isWideScreen) Alignment.BottomStart else Alignment.BottomCenter)
            )

            // Layer 7: Cinema Mode Exit Floating Pill
            AnimatedVisibility(
                visible = state.cinemaMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .testTag("exit_cinema_button")
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ObsidianSheet)
                        .border(0.9.dp, HairlineGold, RoundedCornerShape(999.dp))
                        .clickable { viewModel.toggleCinemaMode() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.action_exit_cinema),
                        style = MaterialTheme.typography.labelLarge,
                        color = ChampagneGold
                    )
                }
            }

            // Layer 8: Luxury Champagne Toast Notification
            AnimatedVisibility(
                visible = state.toastMessage != null,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = if (isWideScreen) 68.dp else 112.dp, start = 16.dp, end = 16.dp)
            ) {
                state.toastMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .testTag("toast_banner")
                            .clip(RoundedCornerShape(999.dp))
                            .background(ObsidianSheet)
                            .border(0.9.dp, HairlineGold, RoundedCornerShape(999.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampagneGold
                        )
                    }
                }
            }

            // Layer 9: Animated Orbital Intro Splash
            SplashOverlay(visible = state.splashVisible)
        }
    }
}

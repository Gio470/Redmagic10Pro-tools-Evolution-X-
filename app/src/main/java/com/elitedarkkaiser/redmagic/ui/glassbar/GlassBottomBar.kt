package com.elitedarkkaiser.redmagic.ui.glassbar

// Ported from Gio470/NPatch (branch `miuix`):
//   manager/src/main/java/top/nkbe/npatch/ui/component/FloatingGlassBottomBar.kt
//   manager/src/main/java/top/nkbe/npatch/ui/component/miuix/animation/DampedDragAnimation.kt
//   manager/src/main/java/top/nkbe/npatch/ui/component/miuix/animation/InteractiveHighlight.kt
//   manager/src/main/java/top/nkbe/npatch/ui/component/miuix/modifier/DragGestureInspector.kt
// Behavior and visuals are kept as close to the original as this app's plain-Views/Compose-interop
// setup allows: same spring-physics drag-to-select pill, same AGSL press-highlight shader, same
// real-time blur/lens/vibrancy backdrop.

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.Font
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import com.elitedarkkaiser.redmagic.ui.CardBackdrop
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * One tab's identity, shaped like NPatch's own MainTab.
 *
 * One glyph rather than NPatch's filled/outlined pair: these come from Font Awesome's solid face,
 * which has no outlined twin, and the bar already separates the selected tab from the rest by
 * colour and by the pill drawn behind it.
 */
data class GlassBottomBarTab(
    /** A Font Awesome codepoint from [com.elitedarkkaiser.redmagic.ui.Icons]. */
    val glyph: String,
    val label: String
)

/**
 * Colors the bar samples instead of NPatch's `COUITheme.colorScheme.*`, so it stays driven by
 * RedMagic's own Material You dynamic palette rather than a second theming system.
 */
data class GlassBottomBarColors(
    val background: Color,
    val container: Color,
    val accent: Color,
    val onSurface: Color,
    /** The unselected tab's colour. Dimmer and greyer than [onSurface], which in a generated dark
     *  palette is a near-white only a couple of tones off the accent. */
    val onSurfaceVariant: Color = onSurface,
    val isLightTheme: Boolean,
    // Only read by the animated background (see BackgroundType) to give its shapes some variety
    // instead of every one of them reading as the same accent color.
    val secondaryAccent: Color = accent,
    val tertiaryAccent: Color = accent
)

/** The colour a bar's glass is washed with at a given Tint. */
private fun GlassBottomBarColors.glassWash(tint: Float): Color =
    Color(
        com.elitedarkkaiser.redmagic.ui.GlassTint.bar(
            background.toArgb(), isLightTheme, tint
        )
    )

/**
 * The press highlight is an AGSL RuntimeShader, which needs API 33 — the same gate NPatch uses
 * (ThemeConfig.isFloatingGlassBottomBarBlurSupported). Below that the bar falls back to the
 * plain non-blurred pill instead of crashing.
 */
fun isGlassBlurSupported(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

private val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

// NPatch's tab width. It decides the pill's shape as much as the layout: the pill is CircleShape
// (a 50% corner radius), so against the 56dp pill height this 76dp gives the capsule NPatch has,
// while a near-square tab would render the very same shape as a circle.
//
// NPatch hard-codes it because 3 tabs always fit, and this bar is back to 3. The measure and cap
// below stay anyway: they cost nothing here, and they are what kept it honest at four and five.
private val FloatingBottomBarItemMaxWidth = 76.dp
private val LocalFloatingBottomBarItemMinWidth = staticCompositionLocalOf { FloatingBottomBarItemMaxWidth }
private val FloatingBottomBarHorizontalPadding = 4.dp
private val FloatingBottomBarVerticalPadding = 4.dp

/**
 * Top-level entry point: hosts [contentView] (this app's classic-View screen content) full-bleed
 * and floats the NPatch glass bar over it.
 */
@Composable
fun GlassBarScaffold(
    contentView: View,
    isLoading: () -> Boolean,
    onSettings: () -> Unit,
    tabs: List<GlassBottomBarTab>,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    colors: GlassBottomBarColors,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = isGlassBlurSupported(),
    backgroundType: com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType =
        com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.NONE,
    /** True to show the tab row; false to collapse it away and leave just the Settings FAB, the
     *  way HorizontalFloatingToolbarAsScaffoldFabSample's toolbar collapses around its content
     *  slot while its FAB stays put. Driven by the shared content scroll (see MainActivity's
     *  onContentScrollDelta) rather than the loading state [isLoading] already governs. */
    expanded: () -> Boolean = { true },
    /** True while Settings is the visible tab, so the attached FAB shows Home (a way back out)
     *  instead of Settings (which would just go nowhere, already being there). */
    showHomeIcon: () -> Boolean = { false },
    /**
     * Swaps the floating pill for the docked bar ported from AxManager -- full width, seated on
     * the bottom edge, Settings as a tab of its own rather than a FAB beside it. See
     * [DockedGlassBottomBar].
     */
    dockedBar: () -> Boolean = { false },
    /** Essentials-style blur under the top edge and the bottom bar. See [EdgeBlur]. */
    edgeBlur: () -> Boolean = { true },
    /** What the loading screen says it is waiting on. */
    loadingMessage: () -> String = { "Starting up…" },
    /**
     * The blur/refraction numbers both bars are drawn with, set in the Glass playground. A lambda
     * rather than a value so a change applies on the next frame -- the playground is a separate
     * Activity, and this one is behind it, still composed.
     */
    effects: () -> com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects = { com.elitedarkkaiser.redmagic.ui.GlassPrefs.DEFAULT },
    /**
     * The name of the page currently open over the tabs, or null when none is.
     *
     * Non-null puts the bar in back mode -- see [BackGlassBar]. A page opened from a row is the
     * one place a tab row has nothing to say, so the bar spends that time being the way out of it
     * instead.
     */
    backTitle: () -> String? = { null },
    onBack: () -> Unit = {}
) {
    val cardBackground = rememberGraphicsLayer()
    val hostView = LocalView.current
    DisposableEffect(cardBackground) {
        onDispose { CardBackdrop.clear(cardBackground) }
    }
    // Also recorded for the edge blur alone, when the bar itself is set to plain.
    val backdrop = if (isBlurEnabled || (edgeBlur() && isGlassBlurSupported())) {
        rememberLayerBackdrop {
            drawRect(colors.background)
            drawContent()
        }
    } else {
        null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The background fill/animation and contentView are recorded into the backdrop TOGETHER,
        // as one Box, rather than attaching layerBackdrop to contentView alone. The blur/lens
        // effects below (bar, FAB, loading overlay) all sample that one recorded layer, and
        // contentView's own root is deliberately left transparent now (see MainUiLauncher) so an
        // animated background can show through its gaps -- recording contentView alone would then
        // capture those gaps as literal transparent holes, which blur badly (a bright seam where
        // recorded-transparent meets recorded-opaque, worst right at the screen edges).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        cardBackground.record { this@drawWithContent.drawContent() }
                        drawLayer(cardBackground)
                        CardBackdrop.update(cardBackground, hostView, this)
                    }
                    .background(colors.background)
            ) {
                if (backgroundType != com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.NONE) {
                    val scheme = if (colors.isLightTheme) {
                        androidx.compose.material3.lightColorScheme(
                            primary = colors.accent,
                            secondary = colors.secondaryAccent,
                            tertiary = colors.tertiaryAccent,
                            background = colors.background
                        )
                    } else {
                        androidx.compose.material3.darkColorScheme(
                            primary = colors.accent,
                            secondary = colors.secondaryAccent,
                            tertiary = colors.tertiaryAccent,
                            background = colors.background
                        )
                    }
                    androidx.compose.material3.MaterialTheme(colorScheme = scheme) {
                        com.elitedarkkaiser.redmagic.ui.backgrounds.AnimatedBackground(
                            type = backgroundType,
                            resolvedType = remember(backgroundType) {
                                if (backgroundType == com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.RANDOM) {
                                    com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.RANDOMIZABLE.random()
                                } else {
                                    null
                                }
                            }
                        )
                    }
                }
            }

            AndroidView(
                factory = { contentView },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (edgeBlur()) {
            // The status bar is hidden in this app, so the band is sized for the cutout area
            // plus the start of the content, and grows with the status bar when it is shown.
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            EdgeBlur(
                backdrop = if (isGlassBlurSupported()) backdrop else null,
                tint = colors.container,
                top = true,
                height = topInset * 1.15f + 48.dp,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            EdgeBlur(
                backdrop = if (isGlassBlurSupported()) backdrop else null,
                tint = colors.container,
                top = false,
                height = 130.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // NPatch's 76dp tabs, narrowed only if this screen can't seat them all at that width.
        // Three always fit, so this resolves to the full 76dp. Accounts for the same 12dp
        // horizontal padding on both edges, the bar's own inner padding, and the fixed end
        // reservation for the Settings FAB (see BarToFabGap/FabDiameter) -- all of it competes
        // for the same available width even though the bar and FAB no longer share one Row.
        val available = maxWidth - BarHorizontalPadding * 2 - FloatingBottomBarHorizontalPadding * 2 -
            BarToFabGap - FabDiameter
        val itemMinWidth = minOf(FloatingBottomBarItemMaxWidth, available / tabs.size)

        // The bar and the Settings FAB enter/exit together on the loading state, as one wrapping
        // Box, but position independently *within* it: HorizontalFloatingToolbarAsScaffoldFabSample
        // (per the reference screenshots) keeps its FAB fixed near the screen's end edge in both
        // its collapsed and expanded states -- Scaffold's FabPosition.End -- and only grows or
        // shrinks the toolbar to the FAB's left.
        if (dockedBar()) {
            AnimatedVisibility(
                visible = !isLoading(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(durationMillis = 450, delayMillis = 250)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 450, delayMillis = 250),
                        initialOffsetY = { it }
                    ),
                exit = fadeOut(tween(durationMillis = 150)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 150),
                        targetOffsetY = { it }
                    )
            ) {
                val back = backTitle()
                if (back != null) {
                    // Docked like the tab bar it stands in for: full width on the bottom edge.
                    DockedBackGlassBar(
                        title = back,
                        onBack = onBack,
                        colors = colors,
                        backdrop = backdrop,
                        isBlurEnabled = isBlurEnabled,
                        effects = effects()
                    )
                } else {
                    DockedGlassBottomBar(
                        tabs = tabs,
                        selectedIndex = selectedIndex,
                        onSelected = onSelected,
                        onSettings = onSettings,
                        isOnSettings = showHomeIcon,
                        colors = colors,
                        backdrop = backdrop,
                        isBlurEnabled = isBlurEnabled,
                        effects = effects()
                    )
                }
            }
        } else AnimatedVisibility(
            visible = !isLoading(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(durationMillis = 450, delayMillis = 250)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 450, delayMillis = 250),
                    initialOffsetY = { it }
                ),
            exit = fadeOut(tween(durationMillis = 150)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 150),
                    targetOffsetY = { it }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // NPatch: 12.dp + the navigation bar inset (MainScreen.kt).
                    .padding(
                        bottom = 12.dp + WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                    ),
                // Centers the bar and the FAB vertically against each other (they differ in
                // height, 64dp vs the FAB's 56dp) while aligning both to the end edge -- each
                // child's own end padding below is what then pins it a fixed distance in from
                // that edge, independent of the other child's size.
                contentAlignment = Alignment.CenterEnd
            ) {
                val back = backTitle()
                if (back != null) {
                    // Takes the whole footprint: in back mode there is no FAB to leave room for,
                    // and Settings from inside a page would land you on a tab behind it.
                    BackGlassBar(
                        title = back,
                        onBack = onBack,
                        colors = colors,
                        backdrop = backdrop,
                        isBlurEnabled = isBlurEnabled,
                        effects = effects(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = BarHorizontalPadding)
                    )
                } else {
                // Collapses away to just the FAB on scroll, the way
                // floatingToolbarVerticalNestedScroll drives HorizontalFloatingToolbar's own
                // expanded state -- a spring rather than the loading AnimatedVisibility's tweens
                // above, since this one is meant to feel tied to an ongoing drag rather than
                // settling toward a fixed end state.
                AnimatedVisibility(
                    visible = expanded(),
                    modifier = Modifier.padding(end = BarHorizontalPadding + BarToFabGap + FabDiameter),
                    // StiffnessMedium (1500f) here previously settled in ~130ms flat -- fast enough
                    // that a recorded screen capture showed the bar going from fully expanded to
                    // fully gone in four frames, reading as an abrupt pop rather than the smooth
                    // glide this was meant to feel like. StiffnessLow (200f) settles closer to
                    // 300ms, slow enough to actually see the width and fade animate.
                    enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        expandHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            expandFrom = Alignment.End
                        ),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                        shrinkHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            shrinkTowards = Alignment.End
                        )
                ) {
                    CompositionLocalProvider(
                        LocalFloatingBottomBarItemMinWidth provides itemMinWidth
                    ) {
                        GlassBottomBar(
                            tabs = tabs,
                            selectedIndex = selectedIndex,
                            onSelected = onSelected,
                            colors = colors,
                            backdrop = backdrop,
                            isBlurEnabled = isBlurEnabled,
                            effects = effects()
                        )
                    }
                }
                // The FAB grows while the bar collapses away and eases back down as it reappears --
                // on the same spring as the bar's own width/fade so the two settle together -- to
                // read as one piece morphing shut rather than a shrinking bar next to a static
                // button.
                val fabScale by animateFloatAsState(
                    targetValue = if (expanded()) 1f else FabCollapsedScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "settingsFabScale"
                )
                SettingsFab(
                    onClick = onSettings,
                    showHomeIcon = showHomeIcon(),
                    colors = colors,
                    backdrop = backdrop,
                    isBlurEnabled = isBlurEnabled,
                    effects = effects(),
                    modifier = Modifier
                        .padding(end = BarHorizontalPadding)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        }
                )
                }
            }
        }

        // Last in the Box, so it covers the bar too: while loading the bar must be both hidden
        // and unreachable, and on the way out the bar slides back in *behind* the clearing blur
        // rather than popping in sharp on top of it.
        LoadingOverlay(
            isLoading = isLoading,
            colors = colors,
            message = loadingMessage,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/** NPatch applies this to the bar itself (MainScreen.kt: `Modifier.padding(horizontal = 12.dp)`). */
private val BarHorizontalPadding = 12.dp

/** Gap between the tab pill's fixed right edge and the Settings FAB's fixed left edge. Part of
 *  the bar's own constant end padding in [GlassBarScaffold] (alongside [FabDiameter] and
 *  [BarHorizontalPadding]), which is what keeps that edge pinned just left of the FAB regardless
 *  of which of the two is currently animating. */
private val BarToFabGap = 12.dp

/** [FloatingActionButtonDefaults]' own default container size, needed up front to reserve room
 *  for the FAB when the tab row is deciding how much width it can spend on each tab. */
private val FabDiameter = 56.dp

/** How much the FAB grows while the tab row is tucked away -- bigger, not smaller, so it visibly
 *  swells to cover the space the collapsing bar vacates instead of leaving a gap next to a
 *  shrunken button, then eases back down to its normal size as the bar reappears. */
private const val FabCollapsedScale = 1.4f

/** The bar in back mode: a way out of a page, and the page's name. */
@Composable
private fun BackGlassBar(
    title: String,
    onBack: () -> Unit,
    colors: GlassBottomBarColors,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean,
    effects: com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects,
    modifier: Modifier = Modifier
) {
    val frosted = backdrop != null && isBlurEnabled
    val containerColor = colors.glassWash(effects.tintAlpha)

    Row(
        modifier = modifier
            // Sized to what is on it, not to the screen -- the way essentials' own toolbar is,
            // and the reason it reads as a floating control rather than as a bar bolted to the
            // bottom edge.
            .then(
                if (frosted) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(effects.blurRadiusDp.dp.toPx())
                            lens(
                                // Capped at the corner radius for the same reason the docked bar
                                // caps it: past that, the refraction shader's own seam reaches
                                // into the band and shows up as diagonal wedges.
                                // Capped at the capsule's own radius, the way the docked bar
                                // caps it: past that the refraction shader's seam reaches into
                                // the band and shows up as diagonal wedges.
                                refractionHeight = minOf(effects.refractionHeightDp, 32f)
                                    .dp.toPx(),
                                refractionAmount = effects.refractionAmountDp.dp.toPx(),
                                depthEffect = effects.depthEffect,
                                chromaticAberration = effects.dispersion
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(if (colors.isLightTheme) 0.06f else 0.12f)
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                } else {
                    Modifier.background(containerColor.copy(alpha = 0.94f), CircleShape)
                }
            )
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.background)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = com.elitedarkkaiser.redmagic.ui.Icons.BACK,
                color = colors.accent,
                fontSize = 17.sp,
                fontFamily = faSolid(),
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = title,
            color = colors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 12.dp)
                // Bounded rather than free, at essentials' own limits: a floor so the bar is not
                // a nub for a one-word page, and a ceiling so a long name cannot stretch it across
                // the whole screen.
                .widthIn(min = 100.dp, max = 250.dp)
        )
        // Balance the back button so the title is centered across the whole capsule.
        Spacer(Modifier.size(48.dp))
    }
}

/**
 * One edge of essentials' progressive blur (sameerasw/essentials, ProgressiveBlurModifier.kt):
 * what scrolls under the top edge or the bottom bar is blurred, strongest at the screen edge and
 * fading to sharp toward the middle, with essentials' tinted gradient over it.
 */
@Composable
private fun EdgeBlur(
    backdrop: Backdrop?,
    tint: Color,
    top: Boolean,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val fade = if (top) {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
    }
    val overlay = if (top) {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.65f), Color.Transparent))
    } else {
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.65f)))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Offscreen so the mask below cuts the blurred band, not what is under it.
                    .graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(fade, blendMode = BlendMode.DstIn)
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { androidx.compose.ui.graphics.RectangleShape },
                        effects = { blur(14.dp.toPx()) }
                    )
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlay)
        )
    }
}

/**
 * Back mode for the docked bar: the same full-width glass seated on the bottom edge as
 * [DockedGlassBottomBar], with the back button at the start and the page's name centred.
 */
@Composable
private fun DockedBackGlassBar(
    title: String,
    onBack: () -> Unit,
    colors: GlassBottomBarColors,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean,
    effects: com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects
) {
    val frosted = backdrop != null && isBlurEnabled
    val corner = effects.dockedCornerDp.dp
    // Same shape trick as DockedGlassBottomBar: uniform corners, pushed down by the radius so the
    // bottom rounding falls off the screen edge.
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = corner.toPx() }
            .then(
                if (frosted) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(effects.blurRadiusDp.dp.toPx())
                            lens(
                                refractionHeight = minOf(
                                    effects.refractionHeightDp,
                                    effects.dockedCornerDp
                                ).dp.toPx(),
                                refractionAmount = effects.refractionAmountDp.dp.toPx(),
                                depthEffect = effects.depthEffect,
                                chromaticAberration = effects.dispersion
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(if (colors.isLightTheme) 0.06f else 0.12f)
                            )
                        },
                        onDrawSurface = { drawRect(colors.glassWash(effects.tintAlpha)) }
                    )
                } else {
                    Modifier
                        .clip(shape)
                        .background(colors.container)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = 10.dp + corner + WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.background)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = com.elitedarkkaiser.redmagic.ui.Icons.BACK,
                    color = colors.accent,
                    fontSize = 17.sp,
                    fontFamily = faSolid(),
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = title,
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            // Balances the back button so the title is centred across the bar.
            Spacer(Modifier.size(48.dp))
        }
    }
}

/**
 * The settings button, anchored by its caller ([GlassBarScaffold]) to a fixed spot near the
 * screen's end edge rather than floating freely -- [HorizontalFloatingToolbarAsScaffoldFabSample]'s
 * Scaffold `FabPosition.End` style, where [GlassBottomBar] grows and shrinks to this FAB's left
 * instead of the two sharing a Row that would carry the FAB along as the bar's width changes.
 */
@Composable
private fun SettingsFab(
    onClick: () -> Unit,
    colors: GlassBottomBarColors,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean,
    effects: com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects,
    showHomeIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val frosted = backdrop != null && isBlurEnabled

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .then(
                if (frosted) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(effects.blurRadiusDp.dp.toPx())
                            lens(
                                refractionHeight = effects.refractionHeightDp.dp.toPx(),
                                refractionAmount = effects.refractionAmountDp.dp.toPx(),
                                depthEffect = effects.depthEffect,
                                chromaticAberration = effects.dispersion
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(if (colors.isLightTheme) 0.06f else 0.12f)
                            )
                        },
                        onDrawSurface = { drawRect(colors.glassWash(effects.tintAlpha)) }
                    )
                } else {
                    Modifier
                }
            ),
        containerColor = if (frosted) Color.Transparent else colors.container,
        contentColor = colors.onSurface,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Text(
            text = if (showHomeIcon) {
                com.elitedarkkaiser.redmagic.ui.Icons.HOME
            } else {
                com.elitedarkkaiser.redmagic.ui.Icons.SETTINGS
            },
            fontSize = 19.sp,
            fontFamily = faSolid(),
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Sized to its own content ([IntrinsicSize.Min], set inside [FloatingGlassBottomBar]) rather than
 * filling the width itself, so it grows and shrinks purely by its left edge moving while its
 * caller ([GlassBarScaffold]) pins its right edge a fixed distance left of [SettingsFab] -- the
 * style [HorizontalFloatingToolbarAsScaffoldFabSample] shows, a toolbar that grows toward a FAB
 * anchored at a fixed screen position rather than the two re-centering together as one block.
 */
@Composable
private fun GlassBottomBar(
    tabs: List<GlassBottomBarTab>,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    colors: GlassBottomBarColors,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean,
    effects: com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects,
    modifier: Modifier = Modifier
) {
    val hasGlassPill = isBlurEnabled && backdrop != null
    FloatingGlassBottomBar(
        modifier = modifier.padding(start = BarHorizontalPadding),
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        tabsCount = tabs.size,
        backdrop = backdrop,
        isBlurEnabled = isBlurEnabled,
        colors = colors,
        effects = effects
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedIndex() == index
            // One colour for every tab, and the accent comes from somewhere else entirely.
            val itemColor by animateColorAsState(
                targetValue = if (isSelected && !hasGlassPill) {
                    colors.accent
                } else {
                    colors.onSurfaceVariant.copy(alpha = 0.75f)
                },
                animationSpec = tween(durationMillis = 220),
                label = "barItemColor"
            )
            FloatingGlassBottomBarItem(
                onClick = { onSelected(index) },
                selected = isSelected,
                label = tab.label,
                // The glass pill above is the indicator whenever the bar is glass. Without it --
                // blur switched off in Settings, or an API below 33 -- the fallback row drew no
                // indicator at all, and the selected tab was marked by nothing but its text
                // colour. This is that missing pill.
                pill = if (!hasGlassPill && isSelected) {
                    colors.accent.copy(alpha = 0.22f)
                } else {
                    null
                }
            ) {
                FloatingGlassBottomBarIcon(
                    selected = isSelected,
                    glyph = tab.glyph,
                    color = itemColor
                )
                // Not bolded on selection either: a tab that thickens is a second selection
                // marker competing with the pill, and it makes the refracted copy disagree with
                // the recorded one mid-drag.
                FloatingGlassBottomBarLabel(tab.label, itemColor)
            }
        }
    }
}

@Composable
private fun RowScope.FloatingGlassBottomBarItem(
    onClick: () -> Unit,
    selected: Boolean = false,
    label: String,
    modifier: Modifier = Modifier,
    /** A capsule drawn behind this tab, for the bar that has no glass pill of its own. */
    pill: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier
            .defaultMinSize(minWidth = LocalFloatingBottomBarItemMinWidth.current)
            .then(
                if (pill != null) {
                    Modifier
                        .clip(CircleShape)
                        .background(pill)
                } else {
                    Modifier
                }
            )
            // NPatch clips the item to CircleShape here. With indication = null and no
            // background on this Column it draws nothing, so the clip's only real effect is
            // cropping the tab's own icon/label — which is what cut the widest label off once
            // the drag scale-up (1.2x) pushed it toward the edges. Dropping it is visually
            // identical and leaves the label room to render.
            .clearAndSetSemantics {
                this.selected = selected
                contentDescription = label
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun FloatingGlassBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop?,
    tabsCount: Int,
    colors: GlassBottomBarColors,
    effects: com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects,
    isBlurEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (!isBlurEnabled || backdrop == null) {
        FloatingGlassBottomBarFallback(
            modifier = modifier,
            containerColor = colors.container,
            content = content
        )
        return
    }

    val isInLightTheme = colors.isLightTheme
    val accentColor = colors.accent
    val containerColor = colors.glassWash(effects.tintAlpha)

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) {
                    FloatingBottomBarHorizontalPadding.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
    }

    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DampedDragAnimationHolder() }
    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { FloatingBottomBarHorizontalPadding.toPx() }
                val globalTouchX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f
                )
            }
        )
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .clearAndSetSemantics {}
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { FloatingBottomBarHorizontalPadding.toPx() * 2f }
                    tabWidthPx = contentWidthPx / tabsCount
                }
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        if (isBlurEnabled) {
                            vibrancy()
                            blur(effects.blurRadiusDp.dp.toPx())
                            lens(
                                refractionHeight = effects.refractionHeightDp.dp.toPx(),
                                refractionAmount = effects.refractionAmountDp.dp.toPx(),
                                depthEffect = effects.depthEffect,
                                chromaticAberration = effects.dispersion
                            )
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (isBlurEnabled) 1f else 0f)
                    },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(if (isInLightTheme) 0.06f else 0.12f),
                        )
                    },
                    layerBlock = {
                        if (isBlurEnabled) {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        }
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(if (isBlurEnabled) interactiveHighlight.modifier else Modifier)
                .height(64.dp)
                .padding(horizontal = FloatingBottomBarHorizontalPadding, vertical = FloatingBottomBarVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalFloatingBottomBarTabScale provides {
                if (isBlurEnabled) lerp(1f, 1.2f, dampedDragAnimation.pressProgress) else 1f
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .semantics { invisibleToUser() }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            if (isBlurEnabled) {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(effects.blurRadiusDp.dp.toPx())
                                lens(
                                    refractionHeight = effects.refractionHeightDp.dp.toPx() * progress,
                                    refractionAmount = effects.refractionAmountDp.dp.toPx() * progress,
                                    depthEffect = effects.depthEffect,
                                    chromaticAberration = effects.dispersion
                                )
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(if (isBlurEnabled) interactiveHighlight.modifier else Modifier)
                    .height(56.dp)
                    .padding(horizontal = FloatingBottomBarHorizontalPadding)
                    // Every tab in accent, and this row is the one the pill refracts -- it is
                    // drawn at alpha 0 and exists only to be recorded into tabsBackdrop. So what
                    // the pill shows is an accent-coloured copy of whatever tab it is over, which
                    // is the tab lighting up as the pill is dragged across it.
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = FloatingBottomBarHorizontalPadding)
                    .graphicsLayer {
                        val contentWidth = totalWidthPx - with(density) { FloatingBottomBarHorizontalPadding.toPx() * 2f }
                        val singleTabWidth = contentWidth / tabsCount
                        val progressOffset = dampedDragAnimation.value * singleTabWidth

                        translationX = if (isLtr) {
                            progressOffset + panelOffset
                        } else {
                            -progressOffset + panelOffset
                        }
                    }
                    .then(if (isBlurEnabled) interactiveHighlight.gestureModifier else Modifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { CircleShape },
                        effects = {
                            if (isBlurEnabled) {
                                val progress = dampedDragAnimation.pressProgress
                                lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, true)
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f)
                        },
                        shadow = { Shadow(alpha = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f) },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * dampedDragAnimation.pressProgress,
                                alpha = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f
                            )
                        },
                        layerBlock = {
                            if (isBlurEnabled) {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            }
                        },
                        onDrawSurface = {
                            val progress = if (isBlurEnabled) dampedDragAnimation.pressProgress else 0f
                            // Accent-tinted rather than the neutral white/black wash it was. This
                            // pill is the only thing on the bar that marks the selection, and a
                            // 10% white wash over a blurred dark surface is a change you have to
                            // go looking for -- which is exactly what was reported.
                            drawRect(
                                color = accentColor.copy(alpha = if (isInLightTheme) 0.20f else 0.26f),
                                alpha = 1f - progress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    )
                    .height(56.dp)
                    .width(
                        with(density) {
                            ((totalWidthPx - FloatingBottomBarHorizontalPadding.toPx() * 2f) / tabsCount).toDp()
                        }
                    )
            )
        }
    }
}

@Composable
private fun FloatingGlassBottomBarFallback(
    modifier: Modifier = Modifier,
    containerColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(containerColor.copy(alpha = 0.94f))
                .height(64.dp)
                .padding(
                    horizontal = FloatingBottomBarHorizontalPadding,
                    vertical = FloatingBottomBarVerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * The tab's Font Awesome glyph, drawn as text rather than as a vector.
 *
 * The bar took ImageVectors, which meant every icon in it had to exist as one -- the Software tab's
 * had to be hand-authored as paths because the Material set has nothing like it. Text in the icon
 * font is the same mechanism the rest of the app's icons already use, so there is one place icons
 * come from instead of two.
 */
@Composable
private fun FloatingGlassBottomBarIcon(
    selected: Boolean,
    glyph: String,
    color: Color
) {
    Text(
        text = glyph,
        color = color,
        fontSize = 19.sp,
        fontFamily = faSolid(),
        // Off, or the font's own line spacing pads the glyph away from the label below it.
        lineHeight = 19.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.size(24.dp)
    )
}

/** The icon face, resolved once per composition tree. */
@Composable
private fun faSolid(): FontFamily = remember {
    FontFamily(Font(com.elitedarkkaiser.redmagic.R.font.fa_solid_900))
}

@Composable
private fun FloatingGlassBottomBarLabel(label: String, color: Color, bold: Boolean = false) {
    BasicText(
        text = label,
        style = TextStyle(
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            // NPatch's own 11sp. It was dropped to 10sp when this bar carried five tabs: they
            // narrowed below 76dp, and tab content scales to 1.2x while dragging
            // (LocalFloatingBottomBarTabScale), so "Hardware" outgrew its tab mid-drag. At three
            // they are back to full width and the label fits.
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = color
        ),
        maxLines = 1,
        softWrap = false,
        // NPatch's value: the label must be free to draw past its own bounds, otherwise the
        // drag scale-up crops it ("text gets cut out" while dragging).
        overflow = TextOverflow.Visible
    )
}

private class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            val position = change.position
            val previousPosition = change.previousPosition
            if (canDrag(position) && canDrag(previousPosition)) {
                onDrag(size, dragAmount)
            }
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

@SuppressLint("NewApi")
private class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader = RuntimeShader(
        """
        uniform float2 size;
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;

        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            return color * intensity;
        }"""
    )

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(
                Color.White.copy(0.06f * progress),
                blendMode = BlendMode.Plus
            )
            shader.apply {
                val position = position(size, positionAnimation.value)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                setFloatUniform("radius", size.minDimension * 1.2f)
                setFloatUniform(
                    "position",
                    position.x.fastCoerceIn(0f, size.width),
                    position.y.fastCoerceIn(0f, size.height)
                )
            }
            drawRect(
                ShaderBrush(shader),
                blendMode = BlendMode.Plus
            )
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                }
            }
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)

        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = drag(
            pointerId = initialDown.id,
            onDrag = { onDrag(it, it.positionChange()) }
        )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) return null

    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            }
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}

/** The docked navigation bar, ported from Gio470/Bus-tracker's AxManager (AxActivity.BottomBar). */
@Composable
private fun DockedGlassBottomBar(
    tabs: List<GlassBottomBarTab>,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    onSettings: () -> Unit,
    isOnSettings: () -> Boolean,
    colors: GlassBottomBarColors,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean,
    effects: com.elitedarkkaiser.redmagic.ui.GlassPrefs.Effects,
    /** False in the playground's preview, where there is no system bar underneath to clear. */
    applyNavigationInset: Boolean = true
) {
    val frosted = backdrop != null && isBlurEnabled
    val corner = effects.dockedCornerDp.dp
    // Uniform, not top-only, and this is the fix for the wedges that showed at both ends of the
    // bar. The refraction shader picks one corner radius for the whole shape and -- because it
    // tests the raw shader coordinate, which is never negative -- the one it picks is always the
    // bottom-right.
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Only on the real bar: the preview has no screen edge to hide the rounding behind,
            // and there the whole shape is the thing worth seeing.
            .graphicsLayer { translationY = if (applyNavigationInset) corner.toPx() else 0f }
            .then(
                if (frosted) {
                    // This is where "the docked bar does not look like liquid glass" came from.
                    // It had no lens and no highlight -- 16dp of blur under a 0.55 tint, which is
                    // a sheet of frosted plastic: nothing bends at the rim, nothing catches a
                    // light along the edge, and the tint was heavy enough to bury what little
                    // was showing through.
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(effects.blurRadiusDp.dp.toPx())
                            lens(
                                // Capped at the corner radius. The shader's gradient field has a
                                // 45-degree discontinuity that starts one corner-radius in from
                                // each end; keeping the refracted band no deeper than that keeps
                                // the seam out of it. The floating bar needs no such cap because
                                // a capsule's radius is half its height, which puts the seam
                                // outside the shape entirely.
                                refractionHeight = minOf(
                                    effects.refractionHeightDp,
                                    effects.dockedCornerDp
                                ).dp.toPx(),
                                refractionAmount = effects.refractionAmountDp.dp.toPx(),
                                depthEffect = effects.depthEffect,
                                chromaticAberration = effects.dispersion
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(if (colors.isLightTheme) 0.06f else 0.12f)
                            )
                        },
                        // Still tinted, just not opaquely: a bar is read against whatever scrolls
                        // under it, and clear glass leaves the labels competing with the content.
                        onDrawSurface = { drawRect(colors.glassWash(effects.tintAlpha)) }
                    )
                } else {
                    Modifier
                        .clip(shape)
                        .background(colors.container)
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 10.dp,
                    // The gesture inset, so the row sits above the home indicator rather than
                    // under it -- the floating bar takes the same inset as bottom padding.
                    bottom = 10.dp + if (applyNavigationInset) {
                        corner + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding()
                    } else {
                        0.dp
                    }
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onSettingsTab = isOnSettings()
            tabs.forEachIndexed { index, tab ->
                DockedGlassBottomBarItem(
                    glyph = tab.glyph,
                    label = tab.label,
                    // Nothing in the row is selected while Settings is open, which is what says
                    // Settings is a destination rather than an action taken from one of these.
                    selected = !onSettingsTab && selectedIndex() == index,
                    colors = colors,
                    onClick = { onSelected(index) }
                )
            }
            DockedGlassBottomBarItem(
                glyph = com.elitedarkkaiser.redmagic.ui.Icons.SETTINGS,
                label = "Settings",
                selected = onSettingsTab,
                colors = colors,
                onClick = onSettings
            )
        }
    }
}

/**
 * One destination in the docked bar: the icon in a pill that fills in when selected, the label
 * under it.
 */
@Composable
private fun RowScope.DockedGlassBottomBarItem(
    glyph: String,
    label: String,
    selected: Boolean,
    colors: GlassBottomBarColors,
    onClick: () -> Unit
) {
    // Same reasoning as the floating bar: 0.7 was not far enough from the accent to read.
    val content by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.onSurfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 220),
        label = "dockedItemColor"
    )
    // The pill used to appear and vanish between frames, which is the one place this bar read as
    // a set of buttons rather than a bar. It grows out of the middle on a slightly bouncy spring
    // and takes the icon up with it, so the selection moves rather than teleports.
    val selection by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dockedItemSelection"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockedItemPress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                // The pill below is the feedback; a ripple on top of it reads as two answers to
                // one tap, and a ripple over glass is muddy anyway.
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 58.dp, height = 32.dp)
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // Widens as it fades in, so the pill reads as opening around the icon.
                        scaleX = 0.6f + 0.4f * selection
                        alpha = selection
                    }
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.26f))
            )
            Text(
                text = glyph,
                color = content,
                fontSize = 17.sp,
                lineHeight = 17.sp,
                fontFamily = faSolid(),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    translationY = -1.5.dp.toPx() * selection
                    val scale = 1f + 0.08f * selection
                    scaleX = scale
                    scaleY = scale
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = content,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

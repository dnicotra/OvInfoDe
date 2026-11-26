package io.github.dnicotra.ovinfode.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigationevent.NavigationEvent

/**
 * Screen transitions for the [androidx.navigation.compose.NavHost]: a new screen slides in over the
 * current one from the end edge, and going back slides it off again, uncovering the previous screen.
 * The screen underneath moves a little along (parallax) so the motion reads as one movement.
 * Nothing fades or scales, so there are no see-through or empty frames.
 *
 * The predictive back gesture uses the same slide with linear easing, so the screen follows the
 * finger; without explicit predictive transitions NavHost falls back to its own scale-out default.
 */
class ScreenTransitions(private val rtl: Boolean) {
    private val sign = if (rtl) -1 else 1

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spec()) { width -> sign * width }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spec()) { width -> -sign * width / PARALLAX_DIVISOR }
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spec()) { width -> -sign * width / PARALLAX_DIVISOR }
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spec()) { width -> sign * width }
    }

    // During the gesture, move away from the edge the swipe started at.
    val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { edge ->
        slideInHorizontally(spec(LinearEasing)) { width -> -gestureSign(edge) * width / PARALLAX_DIVISOR }
    }
    val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { edge ->
        slideOutHorizontally(spec(LinearEasing)) { width -> gestureSign(edge) * width }
    }

    private fun gestureSign(edge: Int) = when (edge) {
        NavigationEvent.EDGE_LEFT -> 1
        NavigationEvent.EDGE_RIGHT -> -1
        else -> sign
    }

    private fun <T> spec(easing: Easing = FastOutSlowInEasing) = tween<T>(DURATION_MILLIS, easing = easing)

    private companion object {
        const val DURATION_MILLIS = 350
        const val PARALLAX_DIVISOR = 4
    }
}

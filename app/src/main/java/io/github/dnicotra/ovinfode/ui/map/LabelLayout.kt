package io.github.dnicotra.ovinfode.ui.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** A rectangle on the screen, in pixels. */
data class ScreenBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: ScreenBox, gap: Float = 0f): Boolean =
        left < other.right + gap && other.left < right + gap && top < other.bottom + gap && other.top < bottom + gap

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun offset(dx: Float, dy: Float) = ScreenBox(left + dx, top + dy, right + dx, bottom + dy)

    companion object {
        fun around(x: Float, y: Float, width: Float, height: Float) =
            ScreenBox(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
    }
}

/** A label to place: the point it names and its size, in pixels. A required label is always shown. */
data class LabelRequest(val x: Float, val y: Float, val width: Float, val height: Float, val required: Boolean = false)

/** How labels make room for each other, in pixels. */
data class LabelSpacing(
    /** Free space kept between two labels. */
    val gap: Float,
    /** Radius of the dot that marks a point whose label moved away or found no room. */
    val dot: Float,
    /** How far a label moves away from its point at each try. */
    val step: Float,
    /** How far from its point a label may go before it is left out. */
    val maxDistance: Float,
)

/**
 * Places [labels] so that none covers another label, another label's point or an obstacle (the
 * stop's pin). They are placed in the order given, most important first: each sits centred on its
 * point when there is room, else as close beside it as it fits (the map then joins it to its point
 * with a line), else nowhere (null: only a dot marks the point until the map is zoomed in). A
 * required label (the selected one) goes on its point when nothing near it is free.
 */
fun placeLabels(labels: List<LabelRequest>, obstacles: List<ScreenBox>, spacing: LabelSpacing): List<ScreenBox?> {
    val dots = labels.map { ScreenBox.around(it.x, it.y, 2 * spacing.dot, 2 * spacing.dot) }
    val placed = mutableListOf<ScreenBox>()
    return labels.mapIndexed { index, label ->
        fun free(box: ScreenBox): Boolean {
            // A label that moved away from its point leaves the point's dot visible, too.
            val onItsPoint = box.contains(label.x, label.y)
            return obstacles.none { it.overlaps(box) } &&
                placed.none { it.overlaps(box, spacing.gap) } &&
                dots.indices.none { (it != index || !onItsPoint) && dots[it].overlaps(box) }
        }
        val box = candidates(label, spacing).firstOrNull(::free)
            ?: ScreenBox.around(label.x, label.y, label.width, label.height).takeIf { label.required }
        box?.also { placed += it }
    }
}

/** The places to try for [label]: on its point, then around it, ever further away. */
private fun candidates(label: LabelRequest, spacing: LabelSpacing): Sequence<ScreenBox> = sequence {
    yield(ScreenBox.around(label.x, label.y, label.width, label.height))
    // The distance from the point to the nearest edge of the label, along each direction.
    var distance = spacing.dot + spacing.gap
    while (distance <= spacing.maxDistance) {
        for ((dx, dy) in DIRECTIONS) {
            val toEdge = min(halfAlong(label.width, dx), halfAlong(label.height, dy))
            val centre = toEdge + distance
            yield(ScreenBox.around(label.x + dx * centre, label.y + dy * centre, label.width, label.height))
        }
        distance += spacing.step
    }
}

private fun halfAlong(size: Float, direction: Float) = if (abs(direction) < 1e-6f) Float.MAX_VALUE else size / 2 / abs(direction)

/**
 * Sixteen directions: straight above and below first (labels are wide, so these move them least),
 * then ever more sideways; above before below, right before left.
 */
private val DIRECTIONS: List<Pair<Float, Float>> = (0 until 16)
    .map { k -> cos(k * PI / 8).toFloat() to sin(k * PI / 8).toFloat() }
    .sortedWith(
        compareByDescending<Pair<Float, Float>> { (abs(it.second) * 1000).roundToInt() }
            .thenBy { sign(it.second) }
            .thenByDescending { it.first },
    )

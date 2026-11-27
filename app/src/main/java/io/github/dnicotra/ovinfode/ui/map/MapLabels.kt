package io.github.dnicotra.ovinfode.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * The labels of the platforms and the other stops on the map, laid out by [placeLabels] so that they
 * cover neither each other nor the stop's pin. A label that had to move is joined to its place by a
 * line; one that found no room shows as a dot until the map is zoomed in. Tapping either selects it.
 */
internal class MapLabels(context: Context, private val onSelect: (MapTarget) -> Unit) : Overlay() {
    /** A label: where it points, its picture and its colour (for the dot). */
    class Label(val target: MapTarget, val bitmap: Bitmap, val color: Int, val selected: Boolean)

    private val density = context.resources.displayMetrics.density
    private val spacing = LabelSpacing(gap = 2 * density, dot = 4 * density, step = 6 * density, maxDistance = 64 * density)

    /** Most important first: the selected label, then the list's order. */
    private var labels: List<Label> = emptyList()
    private var pin: GeoPoint? = null
    private var pinWidth = 0
    private var pinHeight = 0

    // The layout depends only on the zoom: panning moves every point alike. Boxes are relative to their point.
    private var layoutZoom = Double.NaN
    private var layout: List<ScreenBox?> = emptyList()

    // Where everything was last drawn, for taps.
    private var anchors: List<Point> = emptyList()
    private var boxes: List<ScreenBox?> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = OUTLINE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Shows [labels] (the selected one first). [pin] is the stop's own marker, a [pinWidth] by
     * [pinHeight] pixel icon standing on its point, which labels keep clear of.
     */
    fun show(labels: List<Label>, pin: GeoPoint, pinWidth: Int, pinHeight: Int) {
        this.labels = labels
        this.pin = pin
        this.pinWidth = pinWidth
        this.pinHeight = pinHeight
        layoutZoom = Double.NaN
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        anchors = labels.map { projection.toPixels(it.target.location.let { l -> GeoPoint(l.lat, l.lon) }, null) }
        if (projection.zoomLevel != layoutZoom || layout.size != labels.size) {
            layout = relayout(projection)
            layoutZoom = projection.zoomLevel
        }
        boxes = layout.mapIndexed { i, box -> box?.offset(anchors[i].x.toFloat(), anchors[i].y.toFloat()) }
        // Lines and dots under the labels, then the labels, the most important on top.
        labels.indices.forEach { i ->
            val (x, y) = anchors[i].let { it.x.toFloat() to it.y.toFloat() }
            val box = boxes[i]
            if (box != null && box.contains(x, y)) return@forEach
            if (box != null) canvas.drawLine(x, y, x.coerceIn(box.left, box.right), y.coerceIn(box.top, box.bottom), linePaint)
            dotPaint.color = OUTLINE
            canvas.drawCircle(x, y, spacing.dot + density, dotPaint)
            dotPaint.color = labels[i].color
            canvas.drawCircle(x, y, spacing.dot, dotPaint)
        }
        labels.indices.reversed().forEach { i ->
            boxes[i]?.let { canvas.drawBitmap(labels[i].bitmap, it.left, it.top, null) }
        }
    }

    private fun relayout(projection: Projection): List<ScreenBox?> {
        val requests = labels.mapIndexed { i, label ->
            LabelRequest(anchors[i].x.toFloat(), anchors[i].y.toFloat(), label.bitmap.width.toFloat(), label.bitmap.height.toFloat(), required = label.selected)
        }
        val obstacles = pin?.let { projection.toPixels(it, null) }?.let { p ->
            listOf(ScreenBox(p.x - pinWidth / 2f, p.y - pinHeight.toFloat(), p.x + pinWidth / 2f, p.y.toFloat()))
        }.orEmpty()
        return placeLabels(requests, obstacles, spacing).mapIndexed { i, box -> box?.offset(-requests[i].x, -requests[i].y) }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val hit = boxes.indexOfFirst { it?.contains(e.x, e.y) == true }.takeIf { it >= 0 }
            ?: anchors.indices
                .filter { i -> boxes.getOrNull(i)?.contains(anchors[i].x.toFloat(), anchors[i].y.toFloat()) != true }
                .minByOrNull { i -> squared(anchors[i].x - e.x, anchors[i].y - e.y) }
                ?.takeIf { i -> squared(anchors[i].x - e.x, anchors[i].y - e.y) <= squared(TOUCH_RADIUS * density, 0f) }
            ?: return false
        onSelect(labels[hit].target)
        return true
    }

    private fun squared(dx: Float, dy: Float) = dx * dx + dy * dy
}

private const val TOUCH_RADIUS = 20f

// Marker colours do not follow the app's theme: the map underneath is always light.
private const val PLATFORM_LABEL = 0xFF1F3A5F.toInt()
private const val OTHER_STOP_LABEL = 0xFF4A4A4A.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val OUTLINE = 0x99000000.toInt()

/**
 * Background and text colour of a marker: a platform in the colour of its main line, as on
 * the line badges, where the feed has one; otherwise dark blue, other stops dark grey.
 */
internal fun labelColors(target: MapTarget): Pair<Int, Int> {
    if (target is MapTarget.Platform) {
        val line = target.place.lines.firstOrNull { it.color != null }
        val background = line?.color
        if (background != null) return background to (line.textColor ?: readableTextOn(background))
        return PLATFORM_LABEL to WHITE
    }
    return OTHER_STOP_LABEL to WHITE
}

/** White or black, whichever reads better on [background] (relative luminance, WCAG). */
internal fun readableTextOn(background: Int): Int {
    fun channel(shift: Int): Double {
        val c = ((background shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    // Contrast with white (1.05 / (L + 0.05)) against contrast with black ((L + 0.05) / 0.05).
    return if (1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.05) WHITE else BLACK
}

/** A rounded label with [text] in [textColor] on [background], larger and outlined when [selected]. */
internal fun labelBitmap(context: Context, text: String, background: Int, textColor: Int, selected: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (selected) 15 else 13) * density
        typeface = Typeface.DEFAULT_BOLD
        color = textColor
    }
    val padH = 8 * density
    val padV = 5 * density
    // A thin outline keeps light labels apart from the map; the selected one gets a thick one.
    val border = (if (selected) 3 else 1) * density
    val width = paint.measureText(text) + 2 * padH + 2 * border
    val height = paint.fontMetrics.let { it.descent - it.ascent } + 2 * padV + 2 * border
    val bitmap = Bitmap.createBitmap(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = 8 * density
    val outline = if (selected) WHITE else OUTLINE
    canvas.drawRoundRect(RectF(0f, 0f, width, height), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline })
    canvas.drawRoundRect(RectF(border, border, width - border, height - border), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
    canvas.drawText(text, border + padH, border + padV - paint.fontMetrics.ascent, paint)
    return bitmap
}

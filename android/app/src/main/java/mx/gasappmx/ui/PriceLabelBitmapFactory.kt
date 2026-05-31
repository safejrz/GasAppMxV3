package mx.gasappmx.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

// Marker layout constants (all in dp; converted to px at runtime via density)
private const val HORIZONTAL_PADDING_DP = 10f
private const val VERTICAL_PADDING_DP   = 6f
private const val CORNER_RADIUS_DP      = 6f
private const val POINTER_HEIGHT_DP     = 8f
private const val POINTER_HALF_WIDTH_DP = 6f
private const val TEXT_SIZE_SP          = 12f
private const val BORDER_STROKE_DP      = 2.5f
private const val SELECTED_SCALE        = 1.25f  // selected markers are 25 % larger

/**
 * Creates a [BitmapDescriptor] shaped like a map callout bubble displaying [priceText].
 *
 * The bubble is filled with [tier]'s fill color and the text is drawn in [tier]'s text
 * color.  When [isSelected] is true the whole bitmap is scaled up by [SELECTED_SCALE]
 * and a white stroke border is drawn around the bubble for emphasis.
 *
 * The returned descriptor can be passed directly as the `icon` parameter of a Maps
 * Compose [Marker].
 *
 * @param context    Android context used to resolve display density.
 * @param priceText  Short price string shown inside the bubble, e.g. "$23.49" or "—".
 * @param tier       Color tier derived from relative price ranking.
 * @param isSelected Whether this station is currently selected by the user.
 */
fun priceLabelBitmapDescriptor(
    context: Context,
    priceText: String,
    tier: MarkerPriceTier,
    isSelected: Boolean,
): BitmapDescriptor {
    val bitmap = buildPriceLabelBitmap(context, priceText, tier, isSelected)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun buildPriceLabelBitmap(
    context: Context,
    priceText: String,
    tier: MarkerPriceTier,
    isSelected: Boolean,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val scaledDensity = context.resources.displayMetrics.scaledDensity

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = TEXT_SIZE_SP * scaledDensity
        color = tier.textColor
    }

    val fontMetrics = textPaint.fontMetrics
    val textWidth   = textPaint.measureText(priceText)
    val textHeight  = fontMetrics.descent - fontMetrics.ascent

    val hPad     = HORIZONTAL_PADDING_DP * density
    val vPad     = VERTICAL_PADDING_DP   * density
    val pointer  = POINTER_HEIGHT_DP     * density
    val ptrHalf  = POINTER_HALF_WIDTH_DP * density
    val corner   = CORNER_RADIUS_DP      * density
    val border   = BORDER_STROKE_DP      * density

    // Bubble dimensions (without pointer)
    val bubbleW = textWidth  + hPad * 2
    val bubbleH = textHeight + vPad * 2

    // Total bitmap dimensions (include half-border bleed on each edge to avoid clipping)
    val bleed = if (isSelected) border else 0f
    val totalW = (bubbleW + bleed * 2).toInt().coerceAtLeast(1)
    val totalH = (bubbleH + pointer + bleed * 2).toInt().coerceAtLeast(1)

    val rawBitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas    = Canvas(rawBitmap)

    val left   = bleed
    val top    = bleed
    val right  = bleed + bubbleW
    val bottom = bleed + bubbleH

    // --- fill bubble ---
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tier.fillColor
    }
    val bubbleRect = RectF(left, top, right, bottom)
    canvas.drawRoundRect(bubbleRect, corner, corner, fillPaint)

    // --- downward-pointing triangle pointer ---
    val ptrCenterX = left + bubbleW / 2f
    val pointerPath = Path().apply {
        moveTo(ptrCenterX - ptrHalf, bottom)
        lineTo(ptrCenterX + ptrHalf, bottom)
        lineTo(ptrCenterX, bottom + pointer)
        close()
    }
    canvas.drawPath(pointerPath, fillPaint)

    // --- white border when selected ---
    if (isSelected) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            strokeWidth = border
            color       = android.graphics.Color.WHITE
        }
        canvas.drawRoundRect(bubbleRect, corner, corner, borderPaint)
    }

    // --- price text centered inside bubble ---
    val textX = left + hPad
    val textY = top  + vPad - fontMetrics.ascent   // ascent is negative, so subtract
    canvas.drawText(priceText, textX, textY, textPaint)

    // Scale up selected markers so they stand out against peers
    return if (isSelected) {
        val scaledW = (totalW * SELECTED_SCALE).toInt().coerceAtLeast(1)
        val scaledH = (totalH * SELECTED_SCALE).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(rawBitmap, scaledW, scaledH, true)
        rawBitmap.recycle()
        scaled
    } else {
        rawBitmap
    }
}

package com.quran.labs.androidquran.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper

/**
 * Marks the ayah number of each ayah that has linked hadiths with a small dot, and remembers
 * where the ayah numbers are so a tap on one can open its hadiths.
 */
class HadithIndicatorDrawer(
  private val ayahCoordinates: () -> AyahCoordinates?,
  private val hadithAyahs: () -> Set<SuraAyah>,
  private val isNightMode: () -> Boolean
) : ImageDrawHelper {

  private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val scaledRect = RectF()

  // in page image coordinates, already grown to a comfortable touch target
  private val touchTargets = mutableMapOf<SuraAyah, RectF>()

  override fun draw(pageCoordinates: PageCoordinates, canvas: Canvas, image: ImageView) {
    touchTargets.clear()
    val ayahs = hadithAyahs()
    val coordinates = ayahCoordinates()
    if (ayahs.isEmpty() || coordinates == null) return

    val metrics = image.resources.displayMetrics
    val radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOT_RADIUS_DIPS, metrics)
    val ring = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, RING_DIPS, metrics)
    val touchSlop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TOUCH_SLOP_DIPS, metrics)
    val imageScale = image.imageMatrix.mapRadius(1f).takeIf { it > 0f } ?: 1f

    val context = image.context
    val nightMode = isNightMode()
    dotPaint.color = ContextCompat.getColor(
      context, if (nightMode) R.color.hadith_indicator_night else R.color.hadith_indicator
    )
    ringPaint.color = ContextCompat.getColor(
      context, if (nightMode) R.color.hadith_indicator_ring_night else R.color.hadith_indicator_ring
    )

    for (ayah in ayahs) {
      val marker = markerBounds(coordinates, ayah) ?: continue

      image.imageMatrix.mapRect(scaledRect, marker)
      scaledRect.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
      // sits on the top corner of the ayah number, like a badge
      val centerX = scaledRect.right - radius * 0.4f
      val centerY = scaledRect.top + radius * 0.4f
      canvas.drawCircle(centerX, centerY, radius + ring, ringPaint)
      canvas.drawCircle(centerX, centerY, radius, dotPaint)

      val slop = touchSlop / imageScale
      touchTargets[ayah] = RectF(marker).apply { inset(-slop, -slop) }
    }
  }

  /** The ayah whose number is at this point in page image coordinates, if it has hadiths. */
  fun ayahAt(pageX: Float, pageY: Float): SuraAyah? {
    return touchTargets.entries
      .filter { it.value.contains(pageX, pageY) }
      .minByOrNull { (_, bounds) ->
        val dx = bounds.centerX() - pageX
        val dy = bounds.centerY() - pageY
        dx * dx + dy * dy
      }
      ?.key
  }

  private fun markerBounds(coordinates: AyahCoordinates, ayah: SuraAyah): RectF? {
    val glyphs = coordinates.glyphCoordinates?.glyphsByAyah?.get(ayah)
    val endGlyph = glyphs?.lastOrNull { it.glyph is AyahEndGlyph }
    if (endGlyph != null) {
      return RectF(endGlyph.bounds)
    }

    // without glyph data, assume the ayah number closes the ayah's last line on its left
    val lastLine = coordinates.ayahCoordinates["${ayah.sura}:${ayah.ayah}"]
      ?.lastOrNull()
      ?.bounds
      ?: return null
    return RectF(lastLine.left, lastLine.top, lastLine.left + lastLine.height(), lastLine.bottom)
  }

  private companion object {
    const val DOT_RADIUS_DIPS = 4f
    const val RING_DIPS = 1.5f
    const val TOUCH_SLOP_DIPS = 10f
  }
}

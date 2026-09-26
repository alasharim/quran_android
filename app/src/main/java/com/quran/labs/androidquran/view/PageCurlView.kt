package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.withClip
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.helpers.CurlPoint
import com.quran.labs.androidquran.ui.helpers.PageCurlMesh
import com.quran.labs.androidquran.ui.helpers.PageCurlShape
import com.quran.labs.androidquran.ui.helpers.place
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws a leaf of paper curling over while the pages of a book are turned.
 *
 * The leaf being turned is hidden in the pager and drawn here instead, from snapshots of its two
 * sides: the front is the leaf being turned away and the back is the leaf that it becomes once
 * it has turned over (the front of the next leaf). Without a next leaf, the back is blank paper.
 * Either way the print on the front shows through the back faintly, as it does on thin paper.
 *
 * The pages underneath stay where they are in the pager; this view is laid over it and doesn't
 * take touches, so the pager keeps handling the drag.
 */
internal class PageCurlView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  private val mesh = PageCurlMesh()
  private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val showThroughPaint =
    Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = SHOW_THROUGH_ALPHA }
  private val shadowColor = ContextCompat.getColor(context, R.color.page_curl_shadow)
  private val shadowWidth = resources.getDimension(R.dimen.page_curl_shadow_width)
  private val shadowPath = Path()
  private val point = CurlPoint()
  private val location = IntArray(2)

  private var frontBitmap: Bitmap? = null
  private var backBitmap: Bitmap? = null
  private var frontLeaf: View? = null

  /** The leaf on the back of the turning leaf this frame, or null for blank paper. */
  private var backLeaf: View? = null

  /** What [backBitmap] currently shows: a leaf, [BLANK_PAPER], or null if it is stale. */
  private var backShows: Any? = null

  private var shape: PageCurlShape? = null
  private var leafWidth = 0f
  private var leafHeight = 0f
  private var originX = 0f
  private var originY = 0f
  private var progress = 0f

  init {
    isClickable = false
    isFocusable = false
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  /**
   * Sets the leaf being turned and its [shape] at this point of the turn. [page] is the pager
   * page that holds [leaf], and [spineX] is where the spine is within that page.
   *
   * Returns false if the leaf couldn't be captured, in which case nothing is drawn and the
   * caller should turn the leaf itself.
   */
  fun setFront(
    page: View,
    leaf: View,
    spineX: Float,
    shape: PageCurlShape,
    progress: Float
  ): Boolean {
    if (leaf.width <= 0 || leaf.height <= 0) return false

    if (leaf !== frontLeaf || needsRecapture(leaf)) {
      val bitmap = capture(leaf, frontBitmap)
      if (bitmap == null) {
        clear()
        return false
      }
      frontBitmap = bitmap
      frontLeaf = leaf
      // the back shows the front through it, so it is stale now too
      backShows = null
    }

    page.getLocationInWindow(location)
    val pageX = location[0]
    val pageY = location[1]
    getLocationInWindow(location)
    originX = pageX - location[0] + spineX
    originY = pageY - location[1] + leaf.top.toFloat()

    leafWidth = leaf.width.toFloat()
    leafHeight = leaf.height.toFloat()
    this.shape = shape
    this.progress = progress
    invalidate()
    return true
  }

  /** Sets the leaf on the back of the turning leaf. Null shows blank paper on the back. */
  fun setBack(leaf: View?) {
    if (leaf !== backLeaf) {
      backLeaf = leaf
      invalidate()
    }
  }

  /** Stops drawing the turn and lets go of the leaves. The snapshot bitmaps are kept for reuse. */
  fun clear() {
    if (shape == null && frontLeaf == null && backLeaf == null) return
    shape = null
    frontLeaf = null
    backLeaf = null
    backShows = null
    invalidate()
  }

  /** Lets go of the snapshot bitmaps too, for when page turns won't be drawn here for a while. */
  fun release() {
    clear()
    // not recycled: the last frame drawn with them may still be rendering
    frontBitmap = null
    backBitmap = null
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    release()
  }

  override fun onDraw(canvas: Canvas) {
    val shape = shape ?: return
    val front = frontBitmap ?: return
    val back = prepareBack(front) ?: return

    mesh.update(
      shape,
      leafWidth,
      leafHeight,
      originX,
      originY,
      focusX = width / 2f,
      focusY = height / 2f,
      viewerDistance = VIEWER_DISTANCE_PER_HEIGHT * leafHeight
    )

    // how deeply the leaf shades the pages under it, strongest in the middle of the turn
    val depth = sin(PI * progress.coerceIn(0f, 1f)).toFloat()

    drawCurlShadow(canvas, shape, depth)
    canvas.drawBitmapMesh(
      front, mesh.columns, mesh.rows, mesh.frontVertices, 0, mesh.frontColors, 0, bitmapPaint
    )
    drawEdgeShadow(canvas, shape, depth)
    canvas.drawBitmapMesh(
      back, mesh.columns, mesh.rows, mesh.backVertices, 0, mesh.backColors, 0, bitmapPaint
    )
  }

  /**
   * The shadow that the raised curl casts onto the page it is uncovering, running along the
   * outside of the curl on the side away from the spine.
   */
  private fun drawCurlShadow(canvas: Canvas, shape: PageCurlShape, depth: Float) {
    if (shape.radius <= 0f || depth <= 0f) return

    // the outside of the curl is the fold line pushed out by the radius
    val x0 = originX + shape.foldU + shape.dirU * shape.radius
    val y0 = originY + shape.foldV + shape.dirV * shape.radius
    // the higher the curl, the further its shadow reaches
    val length = shadowWidth + shape.radius
    val x1 = x0 + shape.dirU * length
    val y1 = y0 + shape.dirV * length
    // far enough along the fold line to cross the whole leaf
    val reach = 2f * (leafWidth + leafHeight)
    val alongX = -shape.dirV * reach
    val alongY = shape.dirU * reach

    shadowPath.reset()
    shadowPath.moveTo(x0 - alongX, y0 - alongY)
    shadowPath.lineTo(x0 + alongX, y0 + alongY)
    shadowPath.lineTo(x1 + alongX, y1 + alongY)
    shadowPath.lineTo(x1 - alongX, y1 - alongY)
    shadowPath.close()
    shadowPaint.shader =
      LinearGradient(x0, y0, x1, y1, shadow(depth), Color.TRANSPARENT, Shader.TileMode.CLAMP)

    // only the page being uncovered is shaded, on the side of the spine the leaf is lifting from
    canvas.withClip(originX, originY, originX + leafWidth, originY + leafHeight) {
      drawPath(shadowPath, shadowPaint)
    }
  }

  /**
   * The shadow that the turned over part of the leaf casts past its free edge onto whatever is
   * below it.
   */
  private fun drawEdgeShadow(canvas: Canvas, shape: PageCurlShape, depth: Float) {
    if (depth <= 0f) return

    // once turned over, the free edge runs between where its two corners landed
    shape.place(leafWidth, 0f, point)
    if (point.angle < PI.toFloat()) return
    val topX = originX + point.u
    val topY = originY + point.v
    shape.place(leafWidth, leafHeight, point)
    if (point.angle < PI.toFloat()) return
    val bottomX = originX + point.u
    val bottomY = originY + point.v

    val edgeLength = hypot(bottomX - topX, bottomY - topY)
    if (edgeLength <= 0f) return
    // the shadow falls on the side of the edge away from the fold
    var normalX = -(bottomY - topY) / edgeLength * shadowWidth
    var normalY = (bottomX - topX) / edgeLength * shadowWidth
    if (normalX * shape.dirU + normalY * shape.dirV > 0f) {
      normalX = -normalX
      normalY = -normalY
    }

    shadowPath.reset()
    shadowPath.moveTo(topX, topY)
    shadowPath.lineTo(bottomX, bottomY)
    shadowPath.lineTo(bottomX + normalX, bottomY + normalY)
    shadowPath.lineTo(topX + normalX, topY + normalY)
    shadowPath.close()
    shadowPaint.shader = LinearGradient(
      topX,
      topY,
      topX + normalX,
      topY + normalY,
      shadow(depth * EDGE_SHADOW_STRENGTH),
      Color.TRANSPARENT,
      Shader.TileMode.CLAMP
    )
    canvas.drawPath(shadowPath, shadowPaint)
  }

  private fun shadow(strength: Float): Int {
    val alpha = (Color.alpha(shadowColor) * strength.coerceIn(0f, 1f)).toInt()
    return (shadowColor and 0x00FFFFFF) or (alpha shl 24)
  }

  /** Gets the back of the leaf ready: the next leaf when there is one, or blank paper. */
  private fun prepareBack(front: Bitmap): Bitmap? {
    val leaf = backLeaf
    if (leaf != null && leaf.width > 0 && leaf.height > 0) {
      if (leaf === backShows && !needsRecapture(leaf)) {
        return backBitmap
      }
      val bitmap = capture(leaf, backBitmap)
      if (bitmap != null) {
        showThrough(bitmap, front)
        backBitmap = bitmap
        backShows = leaf
        return bitmap
      }
    }

    if (backShows === BLANK_PAPER) {
      return backBitmap
    }
    val bitmap = reuse(backBitmap, front.width, front.height) ?: return null
    bitmap.eraseColor(paperColor(front))
    showThrough(bitmap, front)
    backBitmap = bitmap
    backShows = BLANK_PAPER
    return bitmap
  }

  /** The print on the other side of the paper shows through faintly, mirrored. */
  private fun showThrough(back: Bitmap, front: Bitmap) {
    val canvas = Canvas(back)
    canvas.scale(-back.width.toFloat() / front.width, back.height.toFloat() / front.height)
    canvas.translate(-front.width.toFloat(), 0f)
    canvas.drawBitmap(front, 0f, 0f, showThroughPaint)
  }

  /** The colour of the paper, taken from the margin at the top of the spine. */
  private fun paperColor(front: Bitmap): Int {
    val color = front[PAPER_SAMPLE_INSET.coerceAtMost(front.width - 1), 0]
    return if (Color.alpha(color) == 0) Color.WHITE else color or (0xFF shl 24)
  }

  /**
   * Whether a leaf's snapshot should be retaken each frame, because it is a page whose image
   * is still loading and will change once it arrives.
   */
  private fun needsRecapture(leaf: View): Boolean {
    return leaf is QuranImagePageLayout && leaf.getImageView().drawable == null
  }

  private fun capture(leaf: View, reusable: Bitmap?): Bitmap? {
    val bitmap = reuse(reusable, leaf.width, leaf.height) ?: return null
    return try {
      // pages draw their own background, this is just in case one doesn't
      bitmap.eraseColor(Color.WHITE)
      leaf.draw(Canvas(bitmap))
      bitmap
    } catch (e: RuntimeException) {
      Timber.w(e, "Unable to capture a page for the page curl")
      null
    }
  }

  private fun reuse(bitmap: Bitmap?, width: Int, height: Int): Bitmap? {
    if (bitmap != null && bitmap.width == width && bitmap.height == height) {
      return bitmap
    }
    return try {
      createBitmap(width, height)
    } catch (e: OutOfMemoryError) {
      Timber.w(e, "Not enough memory for the page curl")
      null
    }
  }

  private companion object {
    val BLANK_PAPER = Any()

    /** 0 to 255: how strongly the print on one side of the paper shows through on the other. */
    const val SHOW_THROUGH_ALPHA = 14

    /** How far away the viewer is, in page heights, for the perspective of the raised paper. */
    const val VIEWER_DISTANCE_PER_HEIGHT = 4f

    /** The shadow past the free edge is softer than the one under the curl. */
    const val EDGE_SHADOW_STRENGTH = 0.6f

    /** Where to sample the colour of the paper, in from the top of the spine. */
    const val PAPER_SAMPLE_INSET = 4
  }
}

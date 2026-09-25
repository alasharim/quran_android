package com.quran.labs.androidquran.ui.helpers

import android.view.View
import androidx.viewpager.widget.ViewPager
import com.quran.labs.androidquran.view.TabletView
import kotlin.math.min

/**
 * A [ViewPager.PageTransformer] that turns pages like the leaves of a book instead of
 * sliding them.
 *
 * While the pager scrolls there is a page on the left (position in (-1, 0)) and a page on the
 * right (position in (0, 1)). Both are pinned in place and stacked, and the transition is drawn
 * as one leaf being turned over around the spine:
 *
 * - In the first half of the turn the left page sits on top and its right leaf lifts up around
 *   the spine, revealing the right page's right leaf underneath.
 * - In the second half the right page sits on top and its left leaf comes down around the
 *   spine, covering the left page's left leaf.
 *
 * In dual page mode the spine is the centre of the [TabletView], which is also where the hinge
 * of an unfolded foldable sits. In single page mode the whole page is one leaf hinged on its
 * left edge and it lifts away to reveal the incoming page.
 *
 * The geometry is computed by [bookTurnStateFor] so that it can be unit tested without views.
 */
class BookPageTransformer : ViewPager.PageTransformer {

  override fun transformPage(page: View, position: Float) {
    val width = page.width
    if (width == 0) return

    val state = bookTurnStateFor(position)
    if (page is TabletView) {
      applyToSpread(page, state, width)
    } else {
      applyToSinglePage(page, state, width)
    }
  }

  private fun applyToSpread(page: TabletView, state: BookTurnState?, width: Int) {
    val leftLeaf: View? = page.leftPage
    val rightLeaf: View? = page.rightPage
    if (leftLeaf == null || rightLeaf == null) {
      return
    }

    if (state == null) {
      resetPage(page)
      resetLeaf(leftLeaf)
      resetLeaf(rightLeaf)
      return
    }

    page.translationX = state.translationFraction * width
    page.translationZ = if (state.onTop) ON_TOP_Z else 0f
    // the left leaf hinges on its right edge and the right leaf on its left edge, i.e. both
    // hinge on the spine in the middle of the spread.
    turnLeaf(leftLeaf, state.leftLeafAngle, state.leftLeafHidden, hingeOnLeftEdge = false)
    turnLeaf(rightLeaf, state.rightLeafAngle, state.rightLeafHidden, hingeOnLeftEdge = true)
  }

  private fun applyToSinglePage(page: View, state: BookTurnState?, width: Int) {
    if (state == null) {
      resetPage(page)
      resetLeaf(page)
      return
    }

    page.translationX = state.translationFraction * width
    page.translationZ = if (state.isLeftPage) ON_TOP_Z else 0f
    turnLeaf(page, state.singleLeafAngle, hidden = false, hingeOnLeftEdge = true)
  }

  private fun turnLeaf(leaf: View, angle: Float, hidden: Boolean, hingeOnLeftEdge: Boolean) {
    leaf.pivotX = if (hingeOnLeftEdge) 0f else leaf.width.toFloat()
    leaf.pivotY = leaf.height / 2f
    leaf.cameraDistance = CAMERA_DISTANCE_DP * leaf.resources.displayMetrics.density
    leaf.rotationY = angle
    leaf.alpha = if (hidden) 0f else 1f

    // rendering a rotating leaf from a hardware layer avoids re-drawing the page bitmap
    // on every frame of the turn.
    val turning = angle != 0f && !hidden
    setLayerType(leaf, if (turning) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE)
  }

  private fun resetPage(page: View) {
    page.translationX = 0f
    page.translationZ = 0f
  }

  private fun resetLeaf(leaf: View) {
    leaf.rotationY = 0f
    leaf.alpha = 1f
    setLayerType(leaf, View.LAYER_TYPE_NONE)
  }

  private fun setLayerType(view: View, layerType: Int) {
    if (view.layerType != layerType) {
      view.setLayerType(layerType, null)
    }
  }

  companion object {
    private const val ON_TOP_Z = 1f
    private const val CAMERA_DISTANCE_DP = 10_000f
  }
}

/**
 * The geometry of a page during a book style page turn.
 *
 * @property isLeftPage whether this is the page on the left of the viewport (position < 0).
 * @property translationFraction translation, as a fraction of the page width, that pins the
 * page in place while the pager scrolls.
 * @property onTop whether this page should draw above the other page.
 * @property leftLeafAngle rotation of the left leaf of a spread around the spine, in degrees.
 * @property leftLeafHidden whether the left leaf of a spread is hidden.
 * @property rightLeafAngle rotation of the right leaf of a spread around the spine, in degrees.
 * @property rightLeafHidden whether the right leaf of a spread is hidden.
 * @property singleLeafAngle rotation of a single page around its left edge, in degrees.
 */
internal data class BookTurnState(
  val isLeftPage: Boolean,
  val translationFraction: Float,
  val onTop: Boolean,
  val leftLeafAngle: Float,
  val leftLeafHidden: Boolean,
  val rightLeafAngle: Float,
  val rightLeafHidden: Boolean,
  val singleLeafAngle: Float
)

private const val QUARTER_TURN = 90f
private const val HALF_WAY = 0.5f

/**
 * Computes the [BookTurnState] for a page at the given ViewPager [position], or null when the
 * page is at rest (centred or fully off screen) and all transforms should be reset.
 *
 * The turn progresses from 0 (the left page is centred) to 1 (the right page is centred),
 * regardless of which direction the user is swiping, so the same geometry plays forwards
 * and backwards.
 */
internal fun bookTurnStateFor(position: Float): BookTurnState? {
  if (position <= -1f || position >= 1f || position == 0f) {
    return null
  }

  return if (position < 0f) {
    // the page on the left: its right leaf lifts during the first half of the turn
    val progress = -position
    BookTurnState(
      isLeftPage = true,
      translationFraction = -position,
      onTop = progress < HALF_WAY,
      leftLeafAngle = 0f,
      leftLeafHidden = false,
      rightLeafAngle = -QUARTER_TURN * min(1f, progress * 2f),
      rightLeafHidden = progress >= HALF_WAY,
      singleLeafAngle = -QUARTER_TURN * progress
    )
  } else {
    // the page on the right: its left leaf comes down during the second half of the turn
    val progress = 1f - position
    BookTurnState(
      isLeftPage = false,
      translationFraction = -position,
      onTop = progress >= HALF_WAY,
      leftLeafAngle = QUARTER_TURN * min(1f, (1f - progress) * 2f),
      leftLeafHidden = progress < HALF_WAY,
      rightLeafAngle = 0f,
      rightLeafHidden = false,
      singleLeafAngle = 0f
    )
  }
}

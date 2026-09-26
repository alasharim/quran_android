package com.quran.labs.androidquran.ui.helpers

import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import androidx.viewpager.widget.ViewPager
import com.quran.labs.androidquran.view.PageCurlView
import com.quran.labs.androidquran.view.TabletView
import kotlin.math.min

/**
 * A [ViewPager.PageTransformer] that turns pages like the leaves of a book instead of
 * sliding them.
 *
 * While the pager scrolls there is a page on the left (position in (-1, 0)) and a page on the
 * right (position in (0, 1)). Both are pinned in place, and the transition is drawn as one leaf
 * being turned over around the spine: the right leaf of the left page lifts, turns over, and
 * comes down as the left leaf of the right page.
 *
 * In dual page mode the spine is the centre of the [TabletView], which is also where the hinge
 * of an unfolded foldable sits. In single page mode the whole page is one leaf hinged on its
 * left edge.
 *
 * With a [curlView], the turning leaf is hidden in the pager and drawn there as paper that
 * curls over, following the finger that is turning it (see [pageCurlFor]). Without one, or if
 * the leaf can't be captured, the leaf is turned as a flat card around the spine instead, with
 * the geometry from [bookTurnStateFor].
 */
class BookPageTransformer : ViewPager.PageTransformer {

  /** Draws the turning leaf as curling paper, or null to turn it as a flat card. */
  internal var curlView: PageCurlView? = null
    set(value) {
      field?.clear()
      field = value
    }

  private val touchTracker = PageCurlTouchTracker()

  /** The page whose leaf is being turned, while a turn is in progress. */
  private var turningPage: View? = null

  /** Set when the leaf couldn't be captured, so this turn falls back to the flat card. */
  private var curlFailed = false

  private var lastTravel = Float.NaN
  private var lastFrameTime = 0L
  private var speed = 0f

  /** Lets the curl follow the finger; pass it every touch event that reaches the pager. */
  fun onTouchEvent(event: MotionEvent) {
    touchTracker.onTouchEvent(event)
  }

  override fun transformPage(page: View, position: Float) {
    val width = page.width
    if (width == 0) return

    val state = bookTurnStateFor(position)
    if (state == null) {
      if (page === turningPage) {
        endTurn()
      }
      resetAll(page)
      return
    }

    val curl = curlView
    if (curl != null && !curlFailed && applyCurl(curl, page, state, position, width)) {
      return
    }

    if (page is TabletView) {
      applyToSpread(page, state, width)
    } else {
      applyToSinglePage(page, state, width)
    }
  }

  /**
   * Hides the turning leaves of [page] and hands them to [curl]. Returns false if the curl
   * can't be drawn, in which case the leaf should be turned as a flat card.
   */
  private fun applyCurl(
    curl: PageCurlView,
    page: View,
    state: BookTurnState,
    position: Float,
    width: Int
  ): Boolean {
    val spread = page as? TabletView
    val leftLeaf: View? = spread?.leftPage
    val rightLeaf: View? = spread?.rightPage
    if (spread != null && (leftLeaf == null || rightLeaf == null)) {
      return false
    }

    page.translationX = state.translationFraction * width
    page.translationZ = 0f

    if (!state.isLeftPage) {
      // the page being uncovered: its left leaf is the back of the turning leaf
      if (leftLeaf != null && rightLeaf != null) {
        resetLeaf(rightLeaf)
        hideLeaf(leftLeaf)
        curl.setBack(leftLeaf)
      } else {
        resetLeaf(page)
        curl.setBack(null)
      }
      return true
    }

    // the page being turned away: its right leaf (or the whole page) is the turning leaf
    val leaf = rightLeaf ?: page
    val progress = -position
    if (turningPage !== page) {
      turningPage = page
      lastTravel = Float.NaN
      speed = 0f
    }

    val singlePage = spread == null
    updateSpeed(pageCurlTravel(progress, singlePage))
    touchTracker.step()
    val downY = touchTracker.downY
    val shape = pageCurlFor(
      leafWidth = leaf.width.toFloat(),
      leafHeight = leaf.height.toFloat(),
      progress = progress,
      singlePage = singlePage,
      // without a finger, turn the page by its bottom corner
      grabV = if (downY.isNaN()) leaf.height.toFloat() else downY - leaf.top,
      fingerOffsetV = touchTracker.offsetY,
      speed = speed
    )

    val spineX = if (rightLeaf != null) rightLeaf.left.toFloat() else 0f
    if (!curl.setFront(page, leaf, spineX, shape, progress)) {
      curlFailed = true
      return false
    }

    if (leftLeaf != null) {
      resetLeaf(leftLeaf)
    }
    hideLeaf(leaf)
    return true
  }

  /** Tracks how fast the edge of the leaf is moving, in leaf widths per second. */
  private fun updateSpeed(travel: Float) {
    val now = AnimationUtils.currentAnimationTimeMillis()
    val elapsed = now - lastFrameTime
    if (!lastTravel.isNaN() && elapsed > 0) {
      val instant = (travel - lastTravel) * 1000f / elapsed
      speed += (instant - speed) * SPEED_SMOOTHING
    }
    if (elapsed > 0 || lastTravel.isNaN()) {
      lastTravel = travel
      lastFrameTime = now
    }
  }

  private fun endTurn() {
    turningPage = null
    curlFailed = false
    lastTravel = Float.NaN
    speed = 0f
    touchTracker.reset()
    curlView?.clear()
  }

  private fun applyToSpread(page: TabletView, state: BookTurnState, width: Int) {
    val leftLeaf: View = page.leftPage ?: return
    val rightLeaf: View = page.rightPage ?: return

    page.translationX = state.translationFraction * width
    page.translationZ = if (state.onTop) ON_TOP_Z else 0f
    // the left leaf hinges on its right edge and the right leaf on its left edge, i.e. both
    // hinge on the spine in the middle of the spread.
    turnLeaf(leftLeaf, state.leftLeafAngle, state.leftLeafHidden, hingeOnLeftEdge = false)
    turnLeaf(rightLeaf, state.rightLeafAngle, state.rightLeafHidden, hingeOnLeftEdge = true)
  }

  private fun applyToSinglePage(page: View, state: BookTurnState, width: Int) {
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

  private fun resetAll(page: View) {
    page.translationX = 0f
    page.translationZ = 0f
    if (page is TabletView) {
      page.leftPage?.let { resetLeaf(it) }
      page.rightPage?.let { resetLeaf(it) }
    }
    // in single page mode the page is the leaf; in dual page mode this is a no-op
    resetLeaf(page)
  }

  private fun resetLeaf(leaf: View) {
    leaf.rotationY = 0f
    leaf.alpha = 1f
    setLayerType(leaf, View.LAYER_TYPE_NONE)
  }

  /** Hides a leaf that is being drawn by the curl view instead. */
  private fun hideLeaf(leaf: View) {
    leaf.rotationY = 0f
    leaf.alpha = 0f
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
    private const val SPEED_SMOOTHING = 0.3f
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

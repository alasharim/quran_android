package com.quran.labs.androidquran.ui.helpers

import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry of a leaf of paper curling over while it is turned.
 *
 * Positions are in leaf coordinates: `u` runs from the spine (0) to the free edge of the leaf
 * (the leaf width) and `v` runs from the top of the leaf (0) to the bottom (the leaf height).
 * Once the leaf is turned over it lies at negative `u`, mirrored on the other side of the spine.
 *
 * The leaf is bent around a cylinder of radius [radius] whose axis is parallel to the fold
 * line. The fold line passes through ([foldU], [foldV]) and [dirU], [dirV] is the unit vector
 * perpendicular to it pointing towards the free edge. For a point at distance `d` from the fold
 * line along that direction:
 * - `d <= 0`: the paper lies flat and has not moved.
 * - `0 < d < PI * radius`: the paper wraps around the cylinder.
 * - `d >= PI * radius`: the paper has come over the top of the cylinder and lies flat again,
 *   upside down, on top of the rest of the leaf.
 */
internal data class PageCurlShape(
  val foldU: Float,
  val foldV: Float,
  val dirU: Float,
  val dirV: Float,
  val radius: Float
)

/** Fraction of the leaf width that a grab at the very top or bottom of the page lifts the corner. */
private const val CORNER_LIFT = 0.3f

/** Largest radius the curl opens to, as a fraction of the leaf width. */
internal const val MAX_RADIUS_FRACTION = 0.16f

/** How much the radius grows with speed, per leaf width per second of travel. */
private const val BILLOW_PER_SPEED = 0.18f

/** Largest multiple of the normal radius that a fast flick can open the curl to. */
private const val MAX_BILLOW = 1.8f

/**
 * At the start of a turn, the radius is kept below this fraction of how far the edge has
 * travelled. Being under 1 / PI, the grabbed edge is always over the top of the curl and
 * exactly under the finger.
 */
private const val RADIUS_PER_TRAVEL = 0.3f

/** Keeps a tilted fold line a hair away from the spine so the hinge never lifts. */
private const val SPINE_EPSILON = 0.5f

/** The tilt fades in over this much of the start of a turn... */
private const val TILT_FADE_IN_END = 0.06f

/** ...and fades out from here, so that the leaf always lands flat. */
private const val TILT_FADE_OUT_START = 0.55f

private const val HALF_PI = (PI / 2).toFloat()
private const val FLOAT_PI = PI.toFloat()

/**
 * How far the free edge of the leaf has travelled towards the spine, in leaf widths, at the
 * given turn [progress]. In dual page mode the edge follows the finger 1:1 (a spread is two
 * leaves wide). In single page mode the page is one leaf wide, so the edge starts off
 * following the finger and speeds up to carry the leaf fully off screen by the end.
 */
internal fun pageCurlTravel(progress: Float, singlePage: Boolean): Float {
  val p = progress.coerceIn(0f, 1f)
  return if (singlePage) p * (1f + p) else 2f * p
}

/**
 * Scales the tilt of the fold over the course of a turn: it fades in at the very start so a
 * turn begins from a flat page, and fades out towards the end so the leaf always lands flat.
 */
internal fun pageCurlTiltEnvelope(progress: Float): Float {
  val p = progress.coerceIn(0f, 1f)
  return smoothStep(0f, TILT_FADE_IN_END, p) * (1f - smoothStep(TILT_FADE_OUT_START, 1f, p))
}

/**
 * The radius of the curl. It is zero at both ends of the turn so the leaf starts and ends flat,
 * is held small while the edge first lifts, grows with [speed] (in leaf widths per second) like
 * paper catching the air, and shrinks as the leaf lands so the fold never passes the spine.
 */
internal fun pageCurlRadius(leafWidth: Float, travel: Float, speed: Float): Float {
  val billow = min(MAX_BILLOW, 1f + BILLOW_PER_SPEED * abs(speed))
  val open = MAX_RADIUS_FRACTION * leafWidth * billow
  val lifting = RADIUS_PER_TRAVEL * travel * leafWidth
  // the fold is at u = W - (travel + PI * R) / 2 when it is not tilted, keep it at u >= 0.
  val landing = max(0f, (2f - travel) * leafWidth / FLOAT_PI)
  return max(0f, min(open, min(lifting, landing)))
}

/**
 * Computes the shape of a leaf of [leafWidth] x [leafHeight] at the given turn [progress].
 *
 * The leaf is held at its free edge at height [grabV]; that point is carried to a virtual
 * finger whose horizontal position comes from [progress] and whose vertical position is
 * [grabV] moved by [fingerOffsetV]. Grabbing near the top or bottom of the page additionally
 * lifts the corner, so the fold runs diagonally like a real corner being peeled.
 */
internal fun pageCurlFor(
  leafWidth: Float,
  leafHeight: Float,
  progress: Float,
  singlePage: Boolean,
  grabV: Float,
  fingerOffsetV: Float,
  speed: Float = 0f
): PageCurlShape {
  val w = leafWidth
  val h = leafHeight
  val travel = pageCurlTravel(progress, singlePage)
  val radius = pageCurlRadius(w, travel, speed)

  val grab = grabV.coerceIn(0f, h)
  val halfHeight = h / 2f
  val cornerBias = if (halfHeight > 0f) -(grab - halfHeight) / halfHeight * CORNER_LIFT * w else 0f
  val offset = (fingerOffsetV + cornerBias) * pageCurlTiltEnvelope(progress)

  val fingerU = w - travel * w
  val untilted = shapeFor(w, grab, fingerU, grab, radius)
  val tilted = shapeFor(w, grab, fingerU, grab + offset, radius)
  if (offset == 0f || !liftsSpine(tilted, h)) {
    return tilted
  }

  // tilting this far would lift the hinge, so find the largest tilt that doesn't.
  var low = 0f
  var high = 1f
  var best = untilted
  repeat(12) {
    val mid = (low + high) / 2f
    val candidate = shapeFor(w, grab, fingerU, grab + offset * mid, radius)
    if (liftsSpine(candidate, h)) {
      high = mid
    } else {
      low = mid
      best = candidate
    }
  }
  return best
}

private fun shapeFor(
  grabU: Float,
  grabV: Float,
  fingerU: Float,
  fingerV: Float,
  radius: Float
): PageCurlShape {
  val du = grabU - fingerU
  val dv = grabV - fingerV
  val length = hypot(du, dv)
  val dirU: Float
  val dirV: Float
  if (length < 1e-3f) {
    dirU = 1f
    dirV = 0f
  } else {
    dirU = du / length
    dirV = dv / length
  }
  // the grab point wraps around the cylinder and lands on the finger once it is over the top
  val grabDistance = (length + FLOAT_PI * radius) / 2f
  return PageCurlShape(
    foldU = grabU - dirU * grabDistance,
    foldV = grabV - dirV * grabDistance,
    dirU = dirU,
    dirV = dirV,
    radius = radius
  )
}

private fun liftsSpine(shape: PageCurlShape, leafHeight: Float): Boolean {
  val top = shape.distanceTo(0f, 0f)
  val bottom = shape.distanceTo(0f, leafHeight)
  return max(top, bottom) > -SPINE_EPSILON
}

/** Signed distance of a leaf point from the fold line, positive towards the free edge. */
internal fun PageCurlShape.distanceTo(u: Float, v: Float): Float =
  (u - foldU) * dirU + (v - foldV) * dirV

/** The result of [PageCurlShape.place]. */
internal class CurlPoint {
  var u = 0f
  var v = 0f
  var z = 0f

  /** Angle travelled around the cylinder: 0 is flat and face up, PI is flat and face down. */
  var angle = 0f
}

/**
 * Where the leaf point ([u], [v]) ends up. When [collapseFrontFacing] is set, points that still
 * face up are moved onto the silhouette of the curl, so that a mesh of the back of the leaf only
 * covers the part of the leaf that is face down.
 */
internal fun PageCurlShape.place(
  u: Float,
  v: Float,
  out: CurlPoint,
  collapseFrontFacing: Boolean = false
) {
  val d = distanceTo(u, v)
  val s: Float
  val z: Float
  var angle: Float
  if (d <= 0f) {
    s = d
    z = 0f
    angle = 0f
  } else if (radius <= 0f) {
    s = -d
    z = 0f
    angle = FLOAT_PI
  } else if (d < FLOAT_PI * radius) {
    angle = d / radius
    s = radius * sin(angle)
    z = radius * (1f - cos(angle))
  } else {
    angle = FLOAT_PI
    s = -(d - FLOAT_PI * radius)
    z = 2f * radius
  }

  var placedS = s
  var placedZ = z
  if (collapseFrontFacing && angle < HALF_PI) {
    placedS = radius
    placedZ = radius
    angle = HALF_PI
  }

  // move along the fold direction from the point's foot on the fold line
  out.u = u + (placedS - d) * dirU
  out.v = v + (placedS - d) * dirV
  out.z = placedZ
  out.angle = angle
}

/** Light falls on the page from above, leaning slightly towards the spine. */
private const val LIGHT_TOWARDS_SPINE = 0.35f
private const val AMBIENT_LIGHT = 0.5f

/**
 * Brightness (0 to 1) of paper that has travelled [angle] radians around the curl. Flat paper is
 * fully lit, paper standing on edge at the silhouette is darkest, and the underside brightens
 * again as it lays over.
 */
internal fun pageCurlBrightness(angle: Float, backSide: Boolean): Float {
  // the surface normal, as (component along the fold direction, component towards the viewer)
  val normalS = if (backSide) sin(angle) else -sin(angle)
  val normalZ = if (backSide) -cos(angle) else cos(angle)
  val lightLength = sqrt(LIGHT_TOWARDS_SPINE * LIGHT_TOWARDS_SPINE + 1f)
  val diffuse = (normalS * -LIGHT_TOWARDS_SPINE + normalZ) / lightLength
  // normalise so that paper lying flat is exactly fully lit
  val lit = (diffuse * lightLength).coerceIn(0f, 1f)
  return AMBIENT_LIGHT + (1f - AMBIENT_LIGHT) * lit
}

/**
 * Vertices and colours for drawing both sides of a curled leaf with
 * [android.graphics.Canvas.drawBitmapMesh].
 *
 * The front mesh is a grid over the bitmap of the front of the leaf. The back mesh is a grid
 * over the bitmap of the back of the leaf, which is the front of the next leaf, so its columns
 * run from the spine side of the next leaf, which is the free edge of this one. Vertices of the
 * back that are still face up are collapsed onto the silhouette of the curl so that the back
 * only covers the part of the leaf that has turned over.
 */
internal class PageCurlMesh(val columns: Int = 40, val rows: Int = 40) {
  private val vertexCount = (columns + 1) * (rows + 1)
  val frontVertices = FloatArray(vertexCount * 2)
  val frontColors = IntArray(vertexCount)
  val backVertices = FloatArray(vertexCount * 2)
  val backColors = IntArray(vertexCount)

  private val point = CurlPoint()

  /**
   * Fills the meshes for [shape] on a leaf of [leafWidth] x [leafHeight] whose spine is at
   * ([originX], [originY]) on the canvas. Raised paper is scaled away from ([focusX], [focusY])
   * as if seen from [viewerDistance] away.
   */
  fun update(
    shape: PageCurlShape,
    leafWidth: Float,
    leafHeight: Float,
    originX: Float,
    originY: Float,
    focusX: Float,
    focusY: Float,
    viewerDistance: Float
  ) {
    var index = 0
    for (row in 0..rows) {
      val v = leafHeight * row / rows
      for (column in 0..columns) {
        val bitmapU = leafWidth * column / columns

        shape.place(bitmapU, v, point)
        project(point, originX, originY, focusX, focusY, viewerDistance, frontVertices, index)
        frontColors[index] = gray(pageCurlBrightness(point.angle, backSide = false))

        shape.place(leafWidth - bitmapU, v, point, collapseFrontFacing = true)
        project(point, originX, originY, focusX, focusY, viewerDistance, backVertices, index)
        backColors[index] = gray(pageCurlBrightness(point.angle, backSide = true))

        index++
      }
    }
  }

  private fun project(
    point: CurlPoint,
    originX: Float,
    originY: Float,
    focusX: Float,
    focusY: Float,
    viewerDistance: Float,
    vertices: FloatArray,
    index: Int
  ) {
    val x = originX + point.u
    val y = originY + point.v
    val scale = if (viewerDistance > point.z) viewerDistance / (viewerDistance - point.z) else 1f
    vertices[index * 2] = focusX + (x - focusX) * scale
    vertices[index * 2 + 1] = focusY + (y - focusY) * scale
  }

  private fun gray(brightness: Float): Int {
    val level = (brightness.coerceIn(0f, 1f) * 255f).roundToInt()
    return (0xFF shl 24) or (level shl 16) or (level shl 8) or level
  }
}

/**
 * Follows the finger on the pager so that the curl can tilt towards where the page was grabbed.
 * Vertical movement of the finger is smoothed so that the paper doesn't jitter.
 */
internal class PageCurlTouchTracker {
  /** Height at which the finger first touched, or NaN if the page isn't being touched. */
  var downY = Float.NaN
    private set

  /** Smoothed vertical movement of the finger since it touched down. */
  var offsetY = 0f
    private set

  var isTouching = false
    private set

  private var rawOffsetY = 0f

  fun onTouchEvent(event: MotionEvent) {
    onTouch(event.actionMasked, event.y)
  }

  fun onTouch(action: Int, y: Float) {
    when (action) {
      MotionEvent.ACTION_DOWN -> {
        downY = y
        rawOffsetY = 0f
        offsetY = 0f
        isTouching = true
      }
      MotionEvent.ACTION_MOVE -> if (isTouching) {
        rawOffsetY = y - downY
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTouching = false
    }
  }

  /** Eases the smoothed offset towards the finger; called once per frame of the turn. */
  fun step() {
    offsetY += (rawOffsetY - offsetY) * SMOOTHING
  }

  /** Forgets the last grab once a turn has finished. */
  fun reset() {
    if (!isTouching) {
      downY = Float.NaN
      rawOffsetY = 0f
      offsetY = 0f
    }
  }

  private companion object {
    const val SMOOTHING = 0.35f
  }
}

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
  val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

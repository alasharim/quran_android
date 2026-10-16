package com.quran.labs.androidquran.ui.helpers

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI

class PageCurlTest {
  private val width = 600f
  private val height = 1000f
  private val tolerance = 0.5f
  private val point = CurlPoint()

  private fun curl(
    progress: Float,
    grabV: Float = height / 2f,
    fingerOffsetV: Float = 0f,
    singlePage: Boolean = false,
    speed: Float = 0f
  ) = pageCurlFor(width, height, progress, singlePage, grabV, fingerOffsetV, speed)

  private fun leafPoints() = sequence {
    for (u in 0..10) {
      for (v in 0..10) {
        yield(width * u / 10f to height * v / 10f)
      }
    }
  }

  @Test
  fun `the leaf is flat before the turn starts`() {
    val shape = curl(0f, grabV = height)
    for ((u, v) in leafPoints()) {
      shape.place(u, v, point)
      assertThat(point.u).isWithin(tolerance).of(u)
      assertThat(point.v).isWithin(tolerance).of(v)
      assertThat(point.z).isWithin(tolerance).of(0f)
    }
  }

  @Test
  fun `the leaf lies flat on the other side of the spine once turned`() {
    for (singlePage in listOf(false, true)) {
      val shape = curl(1f, grabV = 0f, fingerOffsetV = 200f, singlePage = singlePage)
      for ((u, v) in leafPoints()) {
        shape.place(u, v, point)
        assertThat(point.u).isWithin(tolerance).of(-u)
        assertThat(point.v).isWithin(tolerance).of(v)
        assertThat(point.z).isWithin(tolerance).of(0f)
      }
    }
  }

  @Test
  fun `the spine never lifts`() {
    for (step in 0..20) {
      val progress = step / 20f
      for (grabV in listOf(0f, height / 2f, height)) {
        for (offset in listOf(-2 * height, -300f, 0f, 300f, 2 * height)) {
          val shape = curl(progress, grabV = grabV, fingerOffsetV = offset, speed = 5f)
          for (v in listOf(0f, height / 2f, height)) {
            shape.place(0f, v, point)
            assertThat(point.u).isWithin(tolerance).of(0f)
            assertThat(point.v).isWithin(tolerance).of(v)
            assertThat(point.z).isWithin(tolerance).of(0f)
          }
        }
      }
    }
  }

  @Test
  fun `the grabbed edge follows the finger`() {
    val progress = 0.4f
    val grabV = height / 2f
    val shape = curl(progress, grabV = grabV, fingerOffsetV = 80f)
    shape.place(width, grabV, point)

    // dual page mode: the edge travels two leaf widths over the turn
    assertThat(point.u).isWithin(tolerance).of(width - 2f * width * progress)
    assertThat(point.v).isWithin(tolerance).of(grabV + 80f * pageCurlTiltEnvelope(progress))
  }

  @Test
  fun `the edge follows the finger at the start of a single page turn`() {
    val progress = 0.05f
    val shape = curl(progress, singlePage = true)
    shape.place(width, height / 2f, point)
    // p * (1 + p) leaf widths, which is close to the finger's p leaf widths early on
    assertThat(point.u).isWithin(tolerance).of(width - width * progress * (1f + progress))
  }

  @Test
  fun `grabbing a corner folds the page diagonally`() {
    val bottom = curl(0.3f, grabV = height)
    val top = curl(0.3f, grabV = 0f)
    val middle = curl(0.3f, grabV = height / 2f)

    // the bottom corner peels ahead of the rest of the leaf, and the top corner likewise
    assertThat(bottom.dirV).isGreaterThan(0.05f)
    assertThat(top.dirV).isLessThan(-0.05f)
    assertThat(middle.dirV).isWithin(0.001f).of(0f)
  }

  @Test
  fun `the curl has no radius at either end of the turn`() {
    assertThat(pageCurlRadius(width, pageCurlTravel(0f, false), 0f)).isEqualTo(0f)
    assertThat(pageCurlRadius(width, pageCurlTravel(1f, false), 0f)).isEqualTo(0f)
    assertThat(pageCurlRadius(width, pageCurlTravel(1f, true), 0f)).isEqualTo(0f)
  }

  @Test
  fun `the curl opens wider when the page is flung`() {
    val travel = pageCurlTravel(0.5f, false)
    val resting = pageCurlRadius(width, travel, 0f)
    val flung = pageCurlRadius(width, travel, 4f)

    assertThat(resting).isWithin(0.01f).of(MAX_RADIUS_FRACTION * width)
    assertThat(flung).isGreaterThan(resting)
    assertThat(pageCurlRadius(width, travel, 1000f)).isAtMost(2f * resting)
  }

  @Test
  fun `the fold never passes the spine`() {
    for (step in 0..100) {
      val progress = step / 100f
      val shape = curl(progress, speed = 10f)
      assertThat(shape.distanceTo(0f, 0f)).isAtMost(0.001f)
      assertThat(shape.distanceTo(0f, height)).isAtMost(0.001f)
    }
  }

  @Test
  fun `the tilt fades in and out so the leaf starts and lands flat`() {
    assertThat(pageCurlTiltEnvelope(0f)).isEqualTo(0f)
    assertThat(pageCurlTiltEnvelope(0.3f)).isEqualTo(1f)
    assertThat(pageCurlTiltEnvelope(1f)).isEqualTo(0f)
  }

  @Test
  fun `the back of the leaf only covers what has turned over`() {
    val shape = curl(0.3f)
    val silhouette = shape.radius
    for ((u, v) in leafPoints()) {
      shape.place(u, v, point, collapseFrontFacing = true)
      assertThat(point.angle).isAtLeast((PI / 2).toFloat() - 0.001f)
      // nothing on the back is further out than the silhouette of the curl
      assertThat(shape.distanceTo(point.u, point.v)).isAtMost(silhouette + 0.001f)
    }
  }

  @Test
  fun `paper is fully lit flat and darkest on edge`() {
    assertThat(pageCurlBrightness(0f, backSide = false)).isWithin(0.001f).of(1f)
    assertThat(pageCurlBrightness(PI.toFloat(), backSide = true)).isWithin(0.001f).of(1f)
    val edge = pageCurlBrightness((PI / 2).toFloat(), backSide = true)
    assertThat(edge).isLessThan(pageCurlBrightness((PI * 0.8).toFloat(), backSide = true))
    assertThat(edge).isLessThan(0.6f)
  }

  @Test
  fun `the mesh covers the whole leaf`() {
    val mesh = PageCurlMesh(columns = 4, rows = 4)
    mesh.update(curl(0f), width, height, 100f, 50f, 0f, 0f, viewerDistance = 4000f)

    // flat, so the front is the leaf's rectangle offset to the spine
    assertThat(mesh.frontVertices[0]).isWithin(tolerance).of(100f)
    assertThat(mesh.frontVertices[1]).isWithin(tolerance).of(50f)
    val last = mesh.frontVertices.size - 2
    assertThat(mesh.frontVertices[last]).isWithin(tolerance).of(100f + width)
    assertThat(mesh.frontVertices[last + 1]).isWithin(tolerance).of(50f + height)
    assertThat(mesh.frontColors.all { it == 0xFFFFFFFF.toInt() }).isTrue()
  }
}

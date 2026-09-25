package com.quran.labs.androidquran.ui.helpers

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookTurnStateTest {
  private val tolerance = 0.001f

  @Test
  fun `pages at rest have no turn state`() {
    assertThat(bookTurnStateFor(0f)).isNull()
    assertThat(bookTurnStateFor(1f)).isNull()
    assertThat(bookTurnStateFor(-1f)).isNull()
    assertThat(bookTurnStateFor(2f)).isNull()
    assertThat(bookTurnStateFor(-1.5f)).isNull()
  }

  @Test
  fun `pages are pinned in place while the pager scrolls`() {
    assertThat(bookTurnStateFor(-0.25f)!!.translationFraction).isWithin(tolerance).of(0.25f)
    assertThat(bookTurnStateFor(0.75f)!!.translationFraction).isWithin(tolerance).of(-0.75f)
  }

  @Test
  fun `left page lifts its right leaf during the first half of the turn`() {
    val state = bookTurnStateFor(-0.25f)!!

    assertThat(state.isLeftPage).isTrue()
    assertThat(state.onTop).isTrue()
    assertThat(state.leftLeafAngle).isWithin(tolerance).of(0f)
    assertThat(state.leftLeafHidden).isFalse()
    assertThat(state.rightLeafAngle).isWithin(tolerance).of(-45f)
    assertThat(state.rightLeafHidden).isFalse()
  }

  @Test
  fun `left page drops underneath with its right leaf hidden in the second half`() {
    val state = bookTurnStateFor(-0.75f)!!

    assertThat(state.onTop).isFalse()
    assertThat(state.rightLeafAngle).isWithin(tolerance).of(-90f)
    assertThat(state.rightLeafHidden).isTrue()
    assertThat(state.leftLeafAngle).isWithin(tolerance).of(0f)
    assertThat(state.leftLeafHidden).isFalse()
  }

  @Test
  fun `right page stays underneath with its left leaf hidden in the first half`() {
    val state = bookTurnStateFor(0.75f)!!

    assertThat(state.isLeftPage).isFalse()
    assertThat(state.onTop).isFalse()
    assertThat(state.leftLeafAngle).isWithin(tolerance).of(90f)
    assertThat(state.leftLeafHidden).isTrue()
    assertThat(state.rightLeafAngle).isWithin(tolerance).of(0f)
    assertThat(state.rightLeafHidden).isFalse()
  }

  @Test
  fun `right page lowers its left leaf on top during the second half`() {
    val state = bookTurnStateFor(0.25f)!!

    assertThat(state.onTop).isTrue()
    assertThat(state.leftLeafAngle).isWithin(tolerance).of(45f)
    assertThat(state.leftLeafHidden).isFalse()
    assertThat(state.rightLeafAngle).isWithin(tolerance).of(0f)
  }

  @Test
  fun `exactly one page is on top at every point of the turn`() {
    for (step in 1 until 20) {
      val leftPosition = -step / 20f
      val left = bookTurnStateFor(leftPosition)!!
      val right = bookTurnStateFor(leftPosition + 1f)!!
      assertThat(left.onTop).isNotEqualTo(right.onTop)
    }
  }

  @Test
  fun `the leaf is edge on at the hand off between the two pages`() {
    val left = bookTurnStateFor(-0.5f)!!
    val right = bookTurnStateFor(0.5f)!!

    assertThat(left.rightLeafAngle).isWithin(tolerance).of(-90f)
    assertThat(right.leftLeafAngle).isWithin(tolerance).of(90f)
    assertThat(left.onTop).isFalse()
    assertThat(right.onTop).isTrue()
  }

  @Test
  fun `single page lifts the left page and leaves the right page flat`() {
    assertThat(bookTurnStateFor(-0.5f)!!.singleLeafAngle).isWithin(tolerance).of(-45f)
    assertThat(bookTurnStateFor(0.5f)!!.singleLeafAngle).isWithin(tolerance).of(0f)
  }
}

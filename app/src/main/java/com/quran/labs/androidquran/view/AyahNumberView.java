package com.quran.labs.androidquran.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.quran.labs.androidquran.R;
import java.util.Objects;

public class AyahNumberView extends View {
  private int boxColor;
  private int nightBoxColor;
  private int boxWidth;
  private int boxHeight;
  private int padding;
  private int textSize;
  private String suraAyah;
  private boolean isNightMode;

  private final Paint boxPaint;
  private final TextPaint textPaint;
  private StaticLayout textLayout;

  // a tappable label beside the box for ayat that have linked hadiths
  private String hadithLabel;
  private final Paint hadithLabelPaint;
  private final TextPaint hadithLabelTextPaint;
  private final RectF hadithLabelBounds = new RectF();
  private float lastTouchX = -1f;
  private float lastTouchY = -1f;

  public AyahNumberView(Context context) {
    this(context, null);
  }

  public AyahNumberView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    int textColor = 0;
    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AyahNumberView);
      textColor = ta.getColor(R.styleable.AyahNumberView_android_textColor, textColor);
      boxColor = ta.getColor(R.styleable.AyahNumberView_backgroundColor, boxColor);
      nightBoxColor = ta.getColor(R.styleable.AyahNumberView_nightBackgroundColor, nightBoxColor);
      boxWidth = ta.getDimensionPixelSize(R.styleable.AyahNumberView_verseBoxWidth, boxWidth);
      boxHeight = ta.getDimensionPixelSize(R.styleable.AyahNumberView_verseBoxHeight, boxHeight);
      textSize = ta.getDimensionPixelSize(R.styleable.AyahNumberView_android_textSize, textSize);
      ta.recycle();
    }

    boxPaint = new Paint();
    boxPaint.setColor(boxColor);
    textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setColor(textColor);
    textPaint.setTextSize(textSize);

    final float density = getResources().getDisplayMetrics().density;
    hadithLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    hadithLabelPaint.setStyle(Paint.Style.STROKE);
    hadithLabelPaint.setStrokeWidth(1.5f * density);
    hadithLabelTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    hadithLabelTextPaint.setTextSize(textSize);
    updateHadithLabelColor();
  }

  public void setHadithLabel(@Nullable String hadithLabel) {
    if (!Objects.equals(this.hadithLabel, hadithLabel)) {
      this.hadithLabel = hadithLabel;
      invalidate();
    }
  }

  /** Whether the last touch on this view landed on the hadith label. */
  public boolean isHadithLabelTouched() {
    if (hadithLabel == null) {
      return false;
    }
    final float slop = 8 * getResources().getDisplayMetrics().density;
    return lastTouchX >= hadithLabelBounds.left - slop && lastTouchX <= hadithLabelBounds.right + slop &&
        lastTouchY >= hadithLabelBounds.top - slop && lastTouchY <= hadithLabelBounds.bottom + slop;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      lastTouchX = event.getX();
      lastTouchY = event.getY();
    }
    return super.onTouchEvent(event);
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  private void updateHadithLabelColor() {
    final int color = ContextCompat.getColor(getContext(),
        isNightMode ? R.color.hadith_indicator_night : R.color.hadith_indicator);
    hadithLabelPaint.setColor(color);
    hadithLabelTextPaint.setColor(color);
  }

  public void setAyahString(@NonNull String suraAyah) {
    if (!suraAyah.equals(this.suraAyah)) {
      this.suraAyah = suraAyah;
      this.textLayout = new StaticLayout(suraAyah, textPaint, boxWidth,
          Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
      invalidate();
    }
  }

  public void setNightMode(boolean isNightMode) {
    if (this.isNightMode != isNightMode) {
      boxPaint.setColor(isNightMode ? nightBoxColor : boxColor);
      this.isNightMode = isNightMode;
      updateHadithLabelColor();
      invalidate();
    }
  }

  public int getBoxCenterX() {
    return padding + (boxWidth / 2);
  }

  public int getBoxBottomY() {
    return padding + boxHeight;
  }

  public void setTextColor(int textColor) {
    textPaint.setColor(textColor);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    padding = (getMeasuredHeight() - boxHeight) / 2;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.drawRect(padding, padding, padding + boxWidth, padding + boxHeight, boxPaint);
    if (hadithLabel != null) {
      drawHadithLabel(canvas);
    }
    if (this.textLayout != null) {
      int startY = padding + ((boxHeight - this.textLayout.getHeight()) / 2);
      canvas.translate(padding, startY);
      this.textLayout.draw(canvas);
      canvas.translate(padding, -startY);
    }
  }

  private void drawHadithLabel(Canvas canvas) {
    final float density = getResources().getDisplayMetrics().density;
    final float horizontalPadding = 10 * density;
    final float height = boxHeight * 0.62f;
    final float left = padding + boxWidth + 12 * density;
    final float top = (getHeight() - height) / 2f;
    final float width = hadithLabelTextPaint.measureText(hadithLabel) + 2 * horizontalPadding;
    hadithLabelBounds.set(left, top, left + width, top + height);
    canvas.drawRoundRect(hadithLabelBounds, height / 2f, height / 2f, hadithLabelPaint);

    final float baseline = hadithLabelBounds.centerY() -
        (hadithLabelTextPaint.descent() + hadithLabelTextPaint.ascent()) / 2f;
    canvas.drawText(hadithLabel, left + horizontalPadding, baseline, hadithLabelTextPaint);
  }
}

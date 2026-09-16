/*
 * This file is part of LocalTube.
 *
 * LocalTube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LocalTube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LocalTube. If not, see <https://www.gnu.org/licenses/>.
 */

package com.lexus2026.localtube;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.*;

public class PlayerUi {

    
    public static final int ICON_PLAY      = 0;
    public static final int ICON_PAUSE     = 1;
    public static final int ICON_REWIND    = 2;
    public static final int ICON_FORWARD   = 3;
    public static final int ICON_NEXT      = 4;
    public static final int ICON_PREV      = 5;
    public static final int ICON_MINIMIZE  = 6;
    public static final int ICON_PIP       = 7;
    public static final int ICON_CLOSE     = 8;
    public static final int ICON_VOLUME    = 9;

    
    public static final int COLOR_ACCENT = 0xFFFF004D;
    public static final int COLOR_TEXT   = 0xFFFFFFFF;

    
    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    
    
    
    public static class IconButton extends View {

        private int icon;
        private int bgColor    = 0x33FFFFFF;
        private int bgPressed  = 0x66FFFFFF;
        private int iconColor  = COLOR_TEXT;
        private float iconScale = 0.55f;
        private boolean pressed = false;

        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public IconButton(Context ctx) {
            super(ctx);
            init();
        }

        public IconButton(Context ctx, int icon) {
            super(ctx);
            this.icon = icon;
            init();
        }

        private void init() {
            setClickable(true);
            setFocusable(true);
            iconPaint.setColor(iconColor);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setIcon(int i) {
            if (this.icon == i) return;
            this.icon = i;
            invalidate();
        }

        public void setIconColor(int c) {
            iconColor = c;
            iconPaint.setColor(c);
            invalidate();
        }

        public void setIconScale(float s) {
            iconScale = s;
            invalidate();
        }

        public void setBgColor(int c) {
            bgColor = c;
            invalidate();
        }

        public void setBgColorPressed(int c) {
            bgPressed = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float size = Math.min(w, h);

            int alpha = (bgColor >>> 24);
            if (alpha > 0) {
                bgPaint.setColor(pressed ? bgPressed : bgColor);
                canvas.drawCircle(w / 2f, h / 2f, size / 2f, bgPaint);
            }

            float cx = w / 2f;
            float cy = h / 2f;
            float is = size * iconScale;
            drawIcon(canvas, icon, cx, cy, is, iconPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed = true;
                    animate().scaleX(0.90f).scaleY(0.90f).setDuration(80).start();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_UP: {
						pressed = false;
						animate().scaleX(1f).scaleY(1f).setDuration(120).start();
						invalidate();
						float x = e.getX(), y = e.getY();
						if (x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight()) {
							performClick();
						}
						return true;
					}

                case MotionEvent.ACTION_CANCEL:
                    pressed = false;
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }

    
    
    
    public static void drawIcon(Canvas c, int icon, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        switch (icon) {
            case ICON_PLAY: {
					Path path = new Path();
					path.moveTo(cx - s * 0.30f, cy - s * 0.42f);
					path.lineTo(cx - s * 0.30f, cy + s * 0.42f);
					path.lineTo(cx + s * 0.42f, cy);
					path.close();
					c.drawPath(path, p);
					break;
				}
            case ICON_PAUSE: {
					float barW = s * 0.15f;
					float barH = s * 0.72f;
					float gap  = s * 0.09f;
					float rr   = barW * 0.35f;
					RectF r1 = new RectF(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f);
					RectF r2 = new RectF(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f);
					c.drawRoundRect(r1, rr, rr, p);
					c.drawRoundRect(r2, rr, rr, p);
					break;
				}
            case ICON_REWIND:  drawCircleArrow(c, cx, cy, s, p, false); break;
            case ICON_FORWARD: drawCircleArrow(c, cx, cy, s, p, true);  break;
            case ICON_NEXT: {
					Path path = new Path();
					path.moveTo(cx - s * 0.45f, cy - s * 0.35f);
					path.lineTo(cx - s * 0.45f, cy + s * 0.35f);
					path.lineTo(cx + s * 0.10f, cy);
					path.close();
					c.drawPath(path, p);
					float barW = s * 0.12f;
					RectF bar = new RectF(cx + s * 0.22f, cy - s * 0.35f,
										  cx + s * 0.22f + barW, cy + s * 0.35f);
					float rr = barW * 0.35f;
					c.drawRoundRect(bar, rr, rr, p);
					break;
				}
            case ICON_PREV: {
					Path path = new Path();
					path.moveTo(cx + s * 0.45f, cy - s * 0.35f);
					path.lineTo(cx + s * 0.45f, cy + s * 0.35f);
					path.lineTo(cx - s * 0.10f, cy);
					path.close();
					c.drawPath(path, p);
					float barW = s * 0.12f;
					RectF bar = new RectF(cx - s * 0.22f - barW, cy - s * 0.35f,
										  cx - s * 0.22f, cy + s * 0.35f);
					float rr = barW * 0.35f;
					c.drawRoundRect(bar, rr, rr, p);
					break;
				}
            case ICON_MINIMIZE: {
					p.setStyle(Paint.Style.STROKE);
					p.setStrokeWidth(s * 0.13f);
					Path path = new Path();
					path.moveTo(cx - s * 0.42f, cy - s * 0.15f);
					path.lineTo(cx, cy + s * 0.28f);
					path.lineTo(cx + s * 0.42f, cy - s * 0.15f);
					c.drawPath(path, p);
					break;
				}
            case ICON_PIP: {
					p.setStyle(Paint.Style.STROKE);
					p.setStrokeWidth(s * 0.10f);
					RectF outer = new RectF(cx - s * 0.48f, cy - s * 0.35f,
											cx + s * 0.48f, cy + s * 0.35f);
					float rr = s * 0.10f;
					c.drawRoundRect(outer, rr, rr, p);

					p.setStyle(Paint.Style.FILL);
					RectF inner = new RectF(cx + s * 0.03f, cy + s * 0.03f,
											cx + s * 0.42f, cy + s * 0.30f);
					c.drawRoundRect(inner, rr * 0.6f, rr * 0.6f, p);
					break;
				}
            case ICON_CLOSE: {
					p.setStyle(Paint.Style.STROKE);
					p.setStrokeWidth(s * 0.13f);
					c.drawLine(cx - s * 0.38f, cy - s * 0.38f,
							   cx + s * 0.38f, cy + s * 0.38f, p);
					c.drawLine(cx + s * 0.38f, cy - s * 0.38f,
							   cx - s * 0.38f, cy + s * 0.38f, p);
					break;
				}
            case ICON_VOLUME: {
					Path sp = new Path();
					sp.moveTo(cx - s * 0.42f, cy - s * 0.15f);
					sp.lineTo(cx - s * 0.18f, cy - s * 0.15f);
					sp.lineTo(cx + s * 0.02f, cy - s * 0.38f);
					sp.lineTo(cx + s * 0.02f, cy + s * 0.38f);
					sp.lineTo(cx - s * 0.18f, cy + s * 0.15f);
					sp.lineTo(cx - s * 0.42f, cy + s * 0.15f);
					sp.close();
					c.drawPath(sp, p);

					p.setStyle(Paint.Style.STROKE);
					p.setStrokeWidth(s * 0.10f);
					RectF a1 = new RectF(cx - s * 0.05f, cy - s * 0.30f,
										 cx + s * 0.35f, cy + s * 0.30f);
					c.drawArc(a1, -55, 110, false, p);
					RectF a2 = new RectF(cx - s * 0.05f, cy - s * 0.48f,
										 cx + s * 0.55f, cy + s * 0.48f);
					c.drawArc(a2, -55, 110, false, p);
					break;
				}
        }
    }

    private static void drawCircleArrow(Canvas c, float cx, float cy, float s,
                                        Paint p, boolean forward) {
        float r = s * 0.40f;
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.09f);
        p.setStrokeCap(Paint.Cap.ROUND);

        if (forward) {
            c.drawArc(oval, 0, 280, false, p);

            double a = Math.toRadians(280);
            float ax = cx + r * (float) Math.cos(a);
            float ay = cy + r * (float) Math.sin(a);
            Path ah = new Path();
            ah.moveTo(ax + s * 0.16f, ay);
            ah.lineTo(ax - s * 0.05f, ay - s * 0.11f);
            ah.lineTo(ax - s * 0.05f, ay + s * 0.11f);
            ah.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(ah, p);
        } else {
            c.drawArc(oval, 260, 280, false, p);

            double a = Math.toRadians(260);
            float ax = cx + r * (float) Math.cos(a);
            float ay = cy + r * (float) Math.sin(a);
            Path ah = new Path();
            ah.moveTo(ax - s * 0.16f, ay);
            ah.lineTo(ax + s * 0.05f, ay - s * 0.11f);
            ah.lineTo(ax + s * 0.05f, ay + s * 0.11f);
            ah.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(ah, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setTextSize(s * 0.42f);
        c.drawText("10", cx, cy + s * 0.14f, p);
    }

    
    public static Bitmap renderIconBitmap(Context ctx, int icon, int sizeDp, int color) {
        float density = ctx.getResources().getDisplayMetrics().density;
        int sizePx = Math.max(1, (int) (sizeDp * density + 0.5f));

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);

        drawIcon(canvas, icon, sizePx / 2f, sizePx / 2f, sizePx * 0.85f, paint);
        return bmp;
    }

    
    
    
    public static class SeekBar extends View {

        public interface OnSeekListener {
            void onSeekStart();
            void onSeekProgress(int progressMs);
            void onSeekEnd(int progressMs);
        }

        private int max      = 100;
        private int progress = 0;
        private int buffered = 0;
        private OnSeekListener listener;
        private boolean dragging = false;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect  = new RectF();

        public SeekBar(Context ctx) {
            super(ctx);
        }

        public SeekBar(Context ctx, AttributeSet attrs) {
            super(ctx, attrs);
        }

        public void setOnSeekListener(OnSeekListener l) { this.listener = l; }

        public void setMax(int m) {
            this.max = Math.max(1, m);
            invalidate();
        }

        public void setProgress(int p) {
            if (dragging) return;
            this.progress = clamp(p, 0, max);
            invalidate();
        }

        public void setBuffered(int b) {
            this.buffered = clamp(b, 0, max);
            invalidate();
        }

        public int getProgress() { return progress; }
        public int getMax()      { return max; }

        private int clamp(int v, int lo, int hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }

        private float trackH()  { return dp(getContext(), 4); }
        private float thumbR()  { return dp(getContext(), 7); }
        private float touchPad(){ return dp(getContext(), 14); }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) {
                w = dp(getContext(), 200);
            }
            int h = (int) (thumbR() * 2 + touchPad() * 2);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cy = h / 2f;
            float tr = thumbR();
            float th = trackH();
            float startX = tr + dp(getContext(), 2);
            float endX   = w - tr - dp(getContext(), 2);
            float trackW = endX - startX;
            float rr = th / 2f;

            paint.setColor(0x40FFFFFF);
            rect.set(startX, cy - th / 2f, endX, cy + th / 2f);
            canvas.drawRoundRect(rect, rr, rr, paint);

            if (buffered > 0 && max > 0) {
                float bf = (float) buffered / max;
                float bx = startX + trackW * bf;
                paint.setColor(0x66FFFFFF);
                rect.set(startX, cy - th / 2f, bx, cy + th / 2f);
                canvas.drawRoundRect(rect, rr, rr, paint);
            }

            float pf = (max > 0) ? (float) progress / max : 0f;
            float px = startX + trackW * pf;
            paint.setColor(COLOR_ACCENT);
            rect.set(startX, cy - th / 2f, px, cy + th / 2f);
            canvas.drawRoundRect(rect, rr, rr, paint);

            float tR = dragging ? tr * 1.35f : tr;
            paint.setColor(COLOR_TEXT);
            canvas.drawCircle(px, cy, tR, paint);

            if (dragging) {
                paint.setColor(COLOR_ACCENT);
                canvas.drawCircle(px, cy, tR * 0.5f, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int w = getWidth();
            float tr = thumbR();
            float startX = tr + dp(getContext(), 2);
            float endX   = w - tr - dp(getContext(), 2);
            float trackW = endX - startX;

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
						float x = clampF(e.getX(), startX, endX);
						progress = (int) ((x - startX) / trackW * max);
						dragging = true;
						if (getParent() != null) {
							getParent().requestDisallowInterceptTouchEvent(true);
						}
						if (listener != null) listener.onSeekStart();
						invalidate();
						return true;
					}
                case MotionEvent.ACTION_MOVE: {
						float x = clampF(e.getX(), startX, endX);
						progress = (int) ((x - startX) / trackW * max);
						if (listener != null) listener.onSeekProgress(progress);
						invalidate();
						return true;
					}
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
						dragging = false;
						if (listener != null) listener.onSeekEnd(progress);
						invalidate();
						return true;
					}
            }
            return super.onTouchEvent(e);
        }

        private float clampF(float v, float lo, float hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }
    }

    
    
    
    public static class VolumeSlider extends View {

        public interface OnVolumeChangeListener {
            void onVolumeChanged(int value);
        }

        private int max   = 15;
        private int value = 8;
        private OnVolumeChangeListener listener;
        private boolean dragging = false;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect  = new RectF();

        public VolumeSlider(Context ctx) { super(ctx); }
        public VolumeSlider(Context ctx, AttributeSet attrs) { super(ctx, attrs); }

        public void setOnVolumeChangeListener(OnVolumeChangeListener l) {
            this.listener = l;
        }

        public void setMax(int m) { this.max = Math.max(1, m); invalidate(); }

        public void setValue(int v) {
            if (dragging) return;
            this.value = clamp(v, 0, max);
            invalidate();
        }

        public int getValue() { return value; }

        private int clamp(int v, int lo, int hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) {
                w = dp(getContext(), 160);
            }
            int h = (int) (dp(getContext(), 6) * 2 + dp(getContext(), 8) * 2);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cy = h / 2f;
            float iconSize = h * 0.60f;
            float iconCx = iconSize * 0.5f + dp(getContext(), 2);
            float i = iconSize * 0.5f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            paint.setStrokeCap(Paint.Cap.ROUND);
            Path sp = new Path();
            sp.moveTo(iconCx - i * 0.65f, cy - i * 0.22f);
            sp.lineTo(iconCx - i * 0.25f, cy - i * 0.22f);
            sp.lineTo(iconCx + i * 0.15f, cy - i * 0.60f);
            sp.lineTo(iconCx + i * 0.15f, cy + i * 0.60f);
            sp.lineTo(iconCx - i * 0.25f, cy + i * 0.22f);
            sp.lineTo(iconCx - i * 0.65f, cy + i * 0.22f);
            sp.close();
            canvas.drawPath(sp, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(iconSize * 0.10f);
            RectF a1 = new RectF(iconCx - i * 0.05f, cy - i * 0.5f,
                                 iconCx + i * 0.45f, cy + i * 0.5f);
            canvas.drawArc(a1, -50, 100, false, paint);

            float tr = dp(getContext(), 6);
            float th = dp(getContext(), 3);
            float startX = iconSize + dp(getContext(), 10);
            float endX   = w - tr - dp(getContext(), 2);
            float trackW = endX - startX;
            float rr = th / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33FFFFFF);
            rect.set(startX, cy - th / 2f, endX, cy + th / 2f);
            canvas.drawRoundRect(rect, rr, rr, paint);

            float frac = (max > 0) ? (float) value / max : 0f;
            float px = startX + trackW * frac;
            paint.setColor(0xFFFFFFFF);
            rect.set(startX, cy - th / 2f, px, cy + th / 2f);
            canvas.drawRoundRect(rect, rr, rr, paint);

            float tR = dragging ? tr * 1.25f : tr;
            canvas.drawCircle(px, cy, tR, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int w = getWidth();
            int h = getHeight();
            float iconSize = h * 0.60f;
            float tr = dp(getContext(), 6);
            float startX = iconSize + dp(getContext(), 10);
            float endX   = w - tr - dp(getContext(), 2);
            float trackW = endX - startX;

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
						float x = clampF(e.getX(), startX, endX);
						value = Math.round((x - startX) / trackW * max);
						dragging = true;
						if (getParent() != null) {
							getParent().requestDisallowInterceptTouchEvent(true);
						}
						if (listener != null) listener.onVolumeChanged(value);
						invalidate();
						return true;
					}
                case MotionEvent.ACTION_MOVE: {
						float x = clampF(e.getX(), startX, endX);
						value = Math.round((x - startX) / trackW * max);
						if (listener != null) listener.onVolumeChanged(value);
						invalidate();
						return true;
					}
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
						dragging = false;
						invalidate();
						return true;
					}
            }
            return super.onTouchEvent(e);
        }

        private float clampF(float v, float lo, float hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }
    }
}

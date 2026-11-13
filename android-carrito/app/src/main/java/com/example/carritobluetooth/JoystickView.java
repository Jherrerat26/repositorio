package com.example.carritobluetooth;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Native, resolution-independent joystick. Only an actively held pointer can drive. */
public final class JoystickView extends View {
    public interface Listener { void onDirection(String command); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private float centerX, centerY, radius, knobX, knobY;
    private int pointer = -1, activeKey = -1;
    private String command = "S";
    private ValueAnimator returnAnimation;

    public JoystickView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Joystick: arrastra para mover; suelta o vuelve al centro para detener. "
                + "También admite las flechas de un teclado físico mientras se mantienen pulsadas.");
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener value) { listener = value; }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        release();
        centerX = width / 2f;
        centerY = height / 2f;
        radius = Math.max(0, Math.min(width, height) / 2f - NeonTheme.dp(getContext(), 18));
    }

    private void stroke(Canvas canvas, float r, int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(NeonTheme.dp(getContext(), width));
        canvas.drawCircle(centerX, centerY, r, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0) return;
        boolean moving = !"S".equals(command);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(centerX, centerY, radius,
                new int[] {0xFF123963, 0xFF091F3F, 0xFF051326}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setShader(null);
        paint.setShadowLayer(NeonTheme.dp(getContext(), moving ? 14 : 8), 0, 0, 0x9958CDFF);
        stroke(canvas, radius * .96f, moving ? NeonTheme.CYAN : 0xFF3F9CCA, 1.5f);
        paint.clearShadowLayer();
        stroke(canvas, radius * .82f, 0xFF174667, 1);
        stroke(canvas, radius * .57f, 0xFF153B5D, 1);
        stroke(canvas, radius * JoystickDirection.DEAD_ZONE, 0xFF245375, 1);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(NeonTheme.dp(getContext(), 2));
        for (int i = 0; i < 4; i++) {
            canvas.save();
            canvas.rotate(i * 90, centerX, centerY);
            String axis = new String[] {"F", "L", "B", "R"}[i];
            paint.setColor(command.equals(axis) ? 0xFFE0F9FF : 0xFF65B8E3);
            float tip = centerY - radius * .70f;
            float wing = radius * .055f;
            canvas.drawLine(centerX - wing, tip + wing, centerX, tip, paint);
            canvas.drawLine(centerX, tip, centerX + wing, tip + wing, paint);
            canvas.restore();
        }
        float knobRadius = radius * .285f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(centerX + knobX - knobRadius * .25f,
                centerY + knobY - knobRadius * .35f, knobRadius * 1.5f,
                new int[] {0xFF285D89, 0xFF113657, 0xFF0A233F}, null, Shader.TileMode.CLAMP));
        paint.setShadowLayer(NeonTheme.dp(getContext(), moving ? 22 : 12), 0, 0, 0xAA43BCFF);
        canvas.drawCircle(centerX + knobX, centerY + knobY, knobRadius, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(NeonTheme.dp(getContext(), 2));
        paint.setColor(NeonTheme.CYAN);
        canvas.drawCircle(centerX + knobX, centerY + knobY, knobRadius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFAEEDFF);
        canvas.drawCircle(centerX + knobX, centerY + knobY, NeonTheme.dp(getContext(), 3), paint);
    }

    private void emit(String next) {
        if (command.equals(next)) return;
        command = next;
        if (!"S".equals(next)) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (listener != null) listener.onDirection(next);
        invalidate();
    }

    private void move(float x, float y) {
        float dx = x - centerX, dy = y - centerY;
        float length = (float) Math.hypot(dx, dy);
        float travel = radius * .52f;
        float scale = length > travel && length > 0 ? travel / length : 1;
        knobX = dx * scale;
        knobY = dy * scale;
        emit(JoystickDirection.command(dx, dy, radius));
        invalidate();
    }

    /** Cancel movement without emitting another callback (used by STOP/lifecycle). */
    public void reset() {
        pointer = -1;
        activeKey = -1;
        command = "S";
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (returnAnimation != null) returnAnimation.cancel();
        final float fromX = knobX, fromY = knobY;
        if (fromX == 0 && fromY == 0) { invalidate(); return; }
        returnAnimation = ValueAnimator.ofFloat(1, 0);
        returnAnimation.setDuration(180);
        returnAnimation.setInterpolator(new DecelerateInterpolator());
        returnAnimation.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            knobX = fromX * fraction;
            knobY = fromY * fraction;
            invalidate();
        });
        returnAnimation.start();
    }

    private void release() {
        emit("S");
        reset();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!isEnabled() || radius <= 0 || Math.hypot(event.getX() - centerX,
                    event.getY() - centerY) > radius) return false;
            if (returnAnimation != null) returnAnimation.cancel();
            activeKey = -1;
            pointer = event.getPointerId(0);
            getParent().requestDisallowInterceptTouchEvent(true);
            move(event.getX(), event.getY());
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE) {
            release();
            return true;
        }
        if (pointer == -1) return true;
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && event.getPointerId(event.getActionIndex()) == pointer) {
            release();
            performClick();
        } else if (action == MotionEvent.ACTION_MOVE) {
            int index = event.findPointerIndex(pointer);
            if (index < 0 || event.getX(index) < 0 || event.getX(index) >= getWidth()
                    || event.getY(index) < 0 || event.getY(index) >= getHeight()) release();
            else move(event.getX(index), event.getY(index));
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled() || pointer != -1) return super.onKeyDown(keyCode, event);
        if (event.getRepeatCount() > 0 && activeKey != keyCode) return true;
        float x = 0, y = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: y = -radius / 2; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: y = radius / 2; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: x = -radius / 2; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: x = radius / 2; break;
            default: return super.onKeyDown(keyCode, event);
        }
        activeKey = keyCode;
        if (returnAnimation != null) returnAnimation.cancel();
        move(centerX + x, centerY + y);
        return true;
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == activeKey) { release(); return true; }
        return super.onKeyUp(keyCode, event);
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect old) {
        super.onFocusChanged(focused, direction, old);
        if (!focused) release();
    }

    @Override protected void onDetachedFromWindow() {
        release();
        if (returnAnimation != null) returnAnimation.cancel();
        super.onDetachedFromWindow();
    }
}

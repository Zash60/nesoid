package com.androidemu.nes.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

import com.androidemu.Emulator;
import com.androidemu.nes.R;

public class VirtualKeypad {
    private static final int[] DPAD_4WAY = {
            Emulator.GAMEPAD_LEFT,
            Emulator.GAMEPAD_UP,
            Emulator.GAMEPAD_RIGHT,
            Emulator.GAMEPAD_DOWN
    };

    private static final float[] DPAD_DEADZONE_VALUES = {
            0.1f, 0.14f, 0.1667f, 0.2f, 0.25f,
    };

    private final Context context;
    private final View view;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private int transparency = 50;

    private final GameKeyListener gameKeyListener;
    private int keyStates = 0;

    private final Vibrator vibrator;
    private boolean vibratorEnabled = true;
    private boolean dpad4Way = false;
    private float dpadDeadZone = DPAD_DEADZONE_VALUES[2];
    private float pointSizeThreshold = 1.0f;
    private boolean inBetweenPress = false;

    private final ArrayList<Control> controls = new ArrayList<>();
    private final Control dpad;
    private final Control buttons;
    private final Control extraButtons;
    private final Control selectStart;

    private final Paint paint = new Paint();

    private static final int[] BUTTONS = { Emulator.GAMEPAD_B, Emulator.GAMEPAD_A };
    private static final int[] EXTRA_BUTTONS = { Emulator.GAMEPAD_B_TURBO, Emulator.GAMEPAD_A_TURBO };

    public VirtualKeypad(View v, GameKeyListener l) {
        view = v;
        context = view.getContext();
        gameKeyListener = l;

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        dpad = createControl(R.drawable.dpad);
        buttons = createControl(R.drawable.buttons);
        extraButtons = createControl(R.drawable.extra_buttons);
        selectStart = createControl(R.drawable.select_start_buttons);
    }

    public int getKeyStates() {
        return keyStates;
    }

    public void reset() {
        keyStates = 0;
    }

    public void destroy() {}

    public void resize(int w, int h) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        vibratorEnabled = prefs.getBoolean("enableVibrator", true);
        dpad4Way = prefs.getBoolean("dpad4Way", false);
        inBetweenPress = prefs.getBoolean("inBetweenPress", false);

        int dz = prefs.getInt("dpadDeadZone", 2);
        dz = Math.max(0, Math.min(dz, 4));
        dpadDeadZone = DPAD_DEADZONE_VALUES[dz];

        pointSizeThreshold = 1.0f;
        if (prefs.getBoolean("pointSizePress", false)) {
            int thr = prefs.getInt("pointSizePressThreshold", 7);
            pointSizeThreshold = (thr / 10.0f) - 0.01f;
        }

        dpad.hide(prefs.getBoolean("hideDpad", false));
        buttons.hide(prefs.getBoolean("hideButtons", false));
        extraButtons.hide(prefs.getBoolean("hideExtraButtons", false));
        extraButtons.disable(prefs.getBoolean("disableExtraButtons", false));
        selectStart.hide(prefs.getBoolean("hideSelectStart", false));

        float controlScale = getControlScale(prefs);

        scaleX = ((float) w / view.getWidth()) * controlScale;
        scaleY = ((float) h / view.getHeight()) * controlScale;

        Resources res = context.getResources();
        for (Control c : controls) {
            c.load(res, scaleX, scaleY);
        }

        int margin = prefs.getInt("layoutMargin", 2) * 10;
        int marginX = (int) (margin * scaleX);
        int marginY = (int) (margin * scaleY);

        reposition(w - marginX, h - marginY, prefs);
        transparency = prefs.getInt("vkeypadTransparency", 50);
    }

    public void draw(Canvas canvas) {
        paint.setAlpha(transparency * 2 + 30);
        for (Control c : controls) {
            c.draw(canvas, paint);
        }
    }

    private float getControlScale(SharedPreferences prefs) {
        String s = prefs.getString("vkeypadSize", "medium");
        if ("large".equals(s)) return 1.33333f;
        if ("small".equals(s)) return 1.0f;
        return 1.2f;
    }

    private Control createControl(int resId) {
        Control c = new Control(resId);
        controls.add(c);
        return c;
    }

    private void reposition(int w, int h, SharedPreferences prefs) {
        String layout = prefs.getString("vkeypadLayout", "top_bottom");
        switch (layout) {
            case "top_bottom":      makeTopBottom(w, h); break;
            case "bottom_top":      makeBottomTop(w, h); break;
            case "top_top":         makeTopTop(w, h);    break;
            default:                makeBottomBottom(w, h); break;
        }
    }

    private void makeBottomBottom(int w, int h) {
        dpad.move(0, h - dpad.getHeight());
        buttons.move(w - buttons.getWidth(), h - buttons.getHeight());
        if (extraButtons.isEnabled())
            extraButtons.move(w - buttons.getWidth(), h - buttons.getHeight() * 7 / 3);
        int x = (w - selectStart.getWidth()) / 2;
        selectStart.move(x, h - selectStart.getHeight());
    }

    private void makeTopTop(int w, int h) {
        dpad.move(0, 0);
        int y = extraButtons.isEnabled() ? buttons.getHeight() * 4 / 3 : 0;
        buttons.move(w - buttons.getWidth(), y);
        if (extraButtons.isEnabled()) extraButtons.move(w - extraButtons.getWidth(), 0);
        selectStart.move((w - selectStart.getWidth()) / 2, h - selectStart.getHeight());
    }

    private void makeTopBottom(int w, int h) {
        dpad.move(0, h - dpad.getHeight());
        buttons.move(w - buttons.getWidth(), h - buttons.getHeight());
        if (extraButtons.isEnabled())
            extraButtons.move(w - buttons.getWidth(), h - buttons.getHeight() * 7 / 3);
        selectStart.move(w / 2 - selectStart.getWidth() / 2, h - selectStart.getHeight());
    }

    private void makeBottomTop(int w, int h) {
        dpad.move(0, h - dpad.getHeight());
        int y = extraButtons.isEnabled() ? buttons.getHeight() * 4 / 3 : 0;
        buttons.move(w - buttons.getWidth(), y);
        if (extraButtons.isEnabled()) extraButtons.move(w - extraButtons.getWidth(), 0);
        int x = (w + dpad.getWidth() - selectStart.getWidth()) / 2;
        selectStart.move(x, h - selectStart.getHeight());
    }

    private boolean shouldVibrate(int oldStates, int newStates) {
        return ((oldStates ^ newStates) & newStates) != 0;
    }

    private void setKeyStates(int newStates) {
        if (keyStates == newStates) return;

        if (vibratorEnabled && shouldVibrate(keyStates, newStates))
            vibrator.vibrate(33);

        keyStates = newStates;
        gameKeyListener.onGameKeyChanged();
    }

    private int get4WayDirection(float x, float y) {
        x -= 0.5f; y -= 0.5f;
        return Math.abs(x) >= Math.abs(y)
                ? (x < 0 ? 0 : 2)
                : (y < 0 ? 1 : 3);
    }

    private int getDpadStates(float x, float y) {
        if (dpad4Way) return DPAD_4WAY[get4WayDirection(x, y)];

        float cx = 0.5f, cy = 0.5f;
        int s = 0;
        if (x < cx - dpadDeadZone) s |= Emulator.GAMEPAD_LEFT;
        else if (x > cx + dpadDeadZone) s |= Emulator.GAMEPAD_RIGHT;
        if (y < cy - dpadDeadZone) s |= Emulator.GAMEPAD_UP;
        else if (y > cy + dpadDeadZone) s |= Emulator.GAMEPAD_DOWN;
        return s;
    }

    private int getButtonsStates(int[] btns, float x, float y, float size) {
        if (size > pointSizeThreshold) return btns[0] | btns[1];
        if (inBetweenPress) {
            int s = 0;
            if (x < 0.58f) s |= btns[0];
            if (x > 0.42f) s |= btns[1];
            return s;
        }
        return x < 0.5f ? btns[0] : btns[1];
    }

    private int getSelectStartStates(float x, float y) {
        return x < 0.5f ? Emulator.GAMEPAD_SELECT : Emulator.GAMEPAD_START;
    }

    private float getEventX(MotionEvent ev, int idx, boolean flip) {
        float x = ev.getX(idx);
        if (flip) x = view.getWidth() - x;
        return x * scaleX;
    }

    private float getEventY(MotionEvent ev, int idx, boolean flip) {
        float y = ev.getY(idx);
        if (flip) y = view.getHeight() - y;
        return y * scaleY;
    }

    private Control findControl(float x, float y) {
        for (Control c : controls) {
            if (c.hitTest(x, y)) return c;
        }
        return null;
    }

    private int getControlStates(Control c, float x, float y, float size) {
        x = (x - c.getX()) / c.getWidth();
        y = (y - c.getY()) / c.getHeight();

        if (c == dpad)        return getDpadStates(x, y);
        if (c == buttons)      return getButtonsStates(BUTTONS, x, y, size);
        if (c == extraButtons) return getButtonsStates(EXTRA_BUTTONS, x, y, size);
        if (c == selectStart) return getSelectStartStates(x, y);
        return 0;
    }

    public boolean onTouch(MotionEvent event, boolean flip) {
        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && pointerCount <= 1) {
            setKeyStates(0);
            return true;
        }

        int states = 0;
        for (int i = 0; i < pointerCount; i++) {
            float x = getEventX(event, i, flip);
            float y = getEventY(event, i, flip);

            Control c = findControl(x, y);
            if (c != null && c.isEnabled() && !c.hidden) {
                float size = event.getSize(i);
                states |= getControlStates(c, x, y, size);
            }
        }

        setKeyStates(states);
        return true;
    }

    private static class Control {
        private final int resId;
        private boolean hidden = false;
        private boolean disabled = false;
        private Bitmap bitmap;
        private final RectF bounds = new RectF();

        Control(int r) { resId = r; }

        float getX() { return bounds.left; }
        float getY() { return bounds.top; }
        int getWidth()  { return bitmap.getWidth(); }
        int getHeight() { return bitmap.getHeight(); }
        boolean isEnabled() { return !disabled; }

        void hide(boolean b)    { hidden = b; }
        void disable(boolean b) { disabled = b; }

        boolean hitTest(float x, float y) { return bounds.contains(x, y); }

        void move(float x, float y) {
            bounds.set(x, y, x + bitmap.getWidth(), y + bitmap.getHeight());
        }

        void load(Resources res, float sx, float sy) {
            bitmap = ((BitmapDrawable) res.getDrawable(resId)).getBitmap();
            bitmap = Bitmap.createScaledBitmap(bitmap,
                    (int)(bitmap.getWidth()  * sx),
                    (int)(bitmap.getHeight() * sy), true);
        }

        void draw(Canvas canvas, Paint paint) {
            if (!hidden && !disabled)
                canvas.drawBitmap(bitmap, bounds.left, bounds.top, paint);
        }
    }
}

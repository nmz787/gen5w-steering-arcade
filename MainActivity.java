package com.codex.steeringlandscape;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Camera;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler();
    private HandlerThread thread;
    private Handler reader;
    private LandscapeView view;
    private ArtSurface art;
    private volatile int generation;
    private SharedPreferences settings;
    private PopupWindow menu;
    private static final String[] MODES = {"Mountains", "Neon terrain", "Silk waves", "Star tunnel", "Kaleidoscope", "Aurora", "Plasma", "Stained glass", "Squish face", "Blob carnival", "Ink garden", "Arcade highway", "Toon kart world", "Steering face"};
    private static final String[] PALETTES = {"Alpine", "Sunset", "Ocean", "Monochrome"};
    private static final String[] CONTRASTS = {"Soft", "Normal", "High"};

    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        settings = getSharedPreferences("visuals", MODE_PRIVATE);
        art = new ArtSurface(this);
        art.setVisibility(View.GONE);
        view = new LandscapeView();
        FrameLayout frame = new FrameLayout(this);
        frame.addView(art);
        frame.addView(view);
        setContentView(frame);
        view.syncArt();
    }

    @Override protected void onResume() {
        super.onResume();
        final int run = ++generation;
        final VehicleReader vehicle = new VehicleReader();
        thread = new HandlerThread("LandscapeVehicleReader");
        thread.start();
        final Handler worker = new Handler(thread.getLooper());
        reader = worker;
        worker.post(new Runnable() {
            @Override public void run() {
                float a = 0, p = 0, b = 0;
                boolean aOk = false, pOk = false, bOk = false;
                try { a = vehicle.angle(); aOk = true; } catch (Exception ignored) { }
                try { p = vehicle.pedal(); pOk = true; } catch (Exception ignored) { }
                try { b = vehicle.brakePressure(); bOk = true; } catch (Exception ignored) { }
                final float angle = a, pedal = p, brake = b;
                final boolean angleOk = aOk, pedalOk = pOk, brakeOk = bOk;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (run == generation) view.setVehicle(angle, angleOk, pedal, pedalOk, brake, brakeOk);
                    }
                });
                if (run == generation) worker.postDelayed(this, 100);
            }
        });
        art.onResume();
        view.start();
    }

    @Override protected void onPause() {
        ++generation;
        if (menu != null) menu.dismiss();
        view.stop();
        art.onPause();
        if (reader != null) reader.removeCallbacksAndMessages(null);
        if (thread != null) thread.quit();
        reader = null;
        thread = null;
        super.onPause();
    }

    private void showSettings() {
        final String[] entries = {"Visual mode  ·  " + MODES[view.mode],
                "Palette  ·  " + PALETTES[view.palette],
                "Contrast  ·  " + CONTRASTS[view.contrast]};
        CheckBox showInputs = new CheckBox(this);
        showInputs.setText("Show input values onscreen");
        showInputs.setChecked(settings.getBoolean("show_inputs", true));
        int padding = (int)(20 * getResources().getDisplayMetrics().density);
        showInputs.setPadding(padding, 0, padding, 0);
        showInputs.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                settings.edit().putBoolean("show_inputs", checked).apply();
                view.invalidate();
            }
        });
        new AlertDialog.Builder(this).setTitle("Landscape settings")
                .setItems(entries, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) chooseSetting("Visual mode", MODES, "mode", view.mode);
                        else if (which == 1) chooseSetting("Palette", PALETTES, "palette", view.palette);
                        else if (which == 2) chooseSetting("Contrast", CONTRASTS, "contrast", view.contrast);
                    }
                }).setView(showInputs).setNegativeButton("Close", null).show();
    }

    private void showMenu() {
        final float density = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xee18202b);
        TextView close = new TextView(this);
        close.setText("×");
        close.setContentDescription("Exit to home screen");
        close.setTextColor(Color.WHITE);
        close.setTextSize(22);
        close.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        close.setPadding((int)(20 * density), 0, 0, 0);
        layout.addView(close, new LinearLayout.LayoutParams(-1, (int)(48 * density)));
        TextView settingsItem = new TextView(this);
        settingsItem.setText("Settings");
        settingsItem.setTextColor(Color.WHITE);
        settingsItem.setTextSize(18);
        settingsItem.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        settingsItem.setPadding((int)(20 * density), 0, 0, 0);
        layout.addView(settingsItem, new LinearLayout.LayoutParams(-1, (int)(48 * density)));
        menu = new PopupWindow(layout, (int)(180 * density), -2, true);
        menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        menu.setOutsideTouchable(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                menu.dismiss();
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
                finish();
            }
        });
        settingsItem.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { menu.dismiss(); showSettings(); }
        });
        menu.showAtLocation(view, Gravity.TOP | Gravity.LEFT, 0, 0);
    }

    private void chooseSetting(String title, final String[] choices, final String key, int selected) {
        new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(choices, selected, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int choice) {
                        settings.edit().putInt(key, choice).apply();
                        view.loadSettings();
                        dialog.dismiss();
                        showSettings();
                    }
                }).setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showSettings(); }
                }).show();
    }

    private final class LandscapeView extends View {
        // A voxel-space horizon renderer: front-to-back columns with early occlusion.
        private static final int W = 240, H = 120, TABLE = 65536;
        private final Bitmap image = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        private final int[] pixels = new int[W * H];
        private final int[] horizon = new int[W];
        private final long[] keys = new long[TABLE];
        private final float[] heights = new float[TABLE];
        private final boolean[] used = new boolean[TABLE];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect dst = new Rect();
        private final Path wave = new Path();
        private final Path mouth = new Path();
        private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint sidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix frontMatrix = new Matrix(), backMatrix = new Matrix();
        private final Path carSide = new Path();
        private final float[] carOutline = { -72,36, -62,-10, -37,-41, 37,-41,
                62,-10, 72,36, 60,51, -60,51 };
        private final float[] frontPoints = new float[16], backPoints = new float[16];
        private final Camera spriteCamera = new Camera();
        private final RectF shadow = new RectF();
        private final Bitmap carSprite = makeCarSprite();
        private final Bitmap carUnderside = makeCarUnderside();
        private float flipAngle;
        private boolean flipping;
        private final int[] landColors = new int[256];
        private final int[] skyColors = new int[H];
        private int mode, palette, contrast;
        private int accent, background;
        private boolean titlePressed;
        private float cameraHeight;
        private boolean cameraReady;
        private float phase;
        private float brakePressure;
        private boolean brakeOk;
        private float brakeStrength() {
            // Artistic response curve; this is not a calibrated pedal percentage.
            return brakeOk ? brakePressure / (brakePressure + 10f) : 0f;
        }
        private float steering, pedal, x, z, heading;
        private boolean angleOk, pedalOk, running;
        private long lastFrame;
        private int entries;
        private final Runnable frame = new Runnable() {
            @Override public void run() {
                if (!running) return;
                if (usesArt()) art.requestRender();
                invalidate();
                postDelayed(this, 40);
            }
        };

        LandscapeView() {
            super(MainActivity.this);
            setClickable(true);
            setContentDescription("Open menu");
            loadSettings();
        }
        void loadSettings() {
            mode = Math.max(0, Math.min(MODES.length - 1, settings.getInt("mode", 0)));
            palette = Math.max(0, Math.min(PALETTES.length - 1, settings.getInt("palette", 0)));
            contrast = Math.max(0, Math.min(CONTRASTS.length - 1, settings.getInt("contrast", 1)));
            int[][] colors = {
                    {0x102137, 0x827b91, 0x203f3f, 0x9ac78e, 0x74f7c6},
                    {0x211a3c, 0xe49d85, 0x4e314b, 0xf2ba77, 0xffd28c},
                    {0x071a38, 0x719db8, 0x123e59, 0x79d4c5, 0x80f5ee},
                    {0x171923, 0xaaaeb9, 0x343844, 0xced4d8, 0xffffff}};
            int[] c = colors[palette];
            background = 0xff000000 | c[0];
            accent = 0xff000000 | c[4];
            for (int i = 0; i < landColors.length; i++) {
                float t = i / 255f;
                float factor = contrast == 0 ? .65f : contrast == 2 ? 1.45f : 1f;
                t = clamp(.5f + (t - .5f) * factor, 0, 1);
                landColors[i] = mix(c[2], c[3], t);
            }
            for (int y = 0; y < H; y++) skyColors[y] = mix(c[0], c[1], Math.min(1f, y / 72f));
            syncArt();
            invalidate();
        }
        private boolean usesArt() { return mode >= 4 && mode <= 12; }
        void syncArt() {
            if (art == null) return;
            art.setVisibility(usesArt() ? View.VISIBLE : View.GONE);
            if (usesArt()) {
                art.setArt(mode, palette, contrast, steering, pedalOk ? pedal : 0f, brakeStrength());
                art.requestRender();
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            float density = getResources().getDisplayMetrics().density;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                titlePressed = event.getX() <= 56 * density && event.getY() <= 52 * density;
                return titlePressed;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                boolean click = titlePressed && event.getX() <= 56 * density
                        && event.getY() <= 52 * density;
                titlePressed = false;
                if (click) performClick();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) { titlePressed = false; return true; }
            return titlePressed;
        }
        @Override public boolean performClick() {
            super.performClick();
            showMenu();
            return true;
        }
        void start() { running = true; lastFrame = SystemClock.uptimeMillis(); post(frame); }
        void stop() { running = false; removeCallbacks(frame); }
        void setVehicle(float angle, boolean aOk, float value, boolean pOk, float pressure, boolean bOk) {
            steering = aOk ? angle : 0;
            if (pOk) pedal = value;
            if (bOk) brakePressure = pressure;
            angleOk = aOk; pedalOk = pOk; brakeOk = bOk;
            if (usesArt()) art.setArt(mode, palette, contrast, steering, pedalOk ? pedal : 0f, brakeStrength());
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long now = SystemClock.uptimeMillis();
            float dt = Math.min((now - lastFrame) / 1000f, .1f);
            lastFrame = now;
            heading -= clamp(steering / 450f, -1f, 1f) * 1.1f * dt;
            float brake = brakeStrength();
            float flow = 1f - .7f * brake;
            x += (float)Math.sin(heading) * 45f * dt * flow;
            z += (float)Math.cos(heading) * 45f * dt * flow;
            phase += dt * flow;
            if (mode == 11 || mode == 12) updateFlip(dt, brake);
            if (mode < 2) {
                if (entries >= TABLE * 3 / 4) {
                    java.util.Arrays.fill(used, false);
                    entries = 0;
                }
                float surface = heightAt(x, z);
                float minimum = surface + 35f;
                if (!cameraReady) { cameraHeight = minimum; cameraReady = true; }
                // Smooth descents, but rise immediately when the ground climbs.
                cameraHeight = Math.max(minimum, cameraHeight + (minimum - cameraHeight) * Math.min(1, dt * 1.5f));
                render();
                image.setPixels(pixels, 0, W, 0, 0, W, H);
                dst.set(0, 0, getWidth(), getHeight());
                canvas.drawBitmap(image, null, dst, paint);
            } else if (mode == 2) {
                drawWaves(canvas);
            } else if (mode == 3) {
                drawStars(canvas);
            } else if (mode == 11) {
                drawCar(canvas);
            } else if (mode == 13) {
                drawFace(canvas, brake);
            }
            float density = getResources().getDisplayMetrics().density;
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setTextSize(18 * density);
            paint.setShadowLayer(3 * density, 0, density, Color.BLACK);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.LEFT);
            // Draw the indicator directly: Android 4.4 fonts may lack the glyph.
            wave.reset();
            wave.moveTo(20 * density, 18 * density);
            wave.lineTo(36 * density, 18 * density);
            wave.lineTo(28 * density, 29 * density);
            wave.close();
            canvas.drawPath(wave, paint);
            if (settings.getBoolean("show_inputs", true)) {
                paint.setTextAlign(Paint.Align.RIGHT);
                String angleText = angleOk ? String.format(Locale.US, "%+.1f°", steering) : "--.-°";
                String pedalText = pedalOk ? String.format(Locale.US, "%.1f%%", pedal) : "--.-%";
                String brakeText = brakeOk ? String.format(Locale.US, "BRAKE %.1f", brakePressure) : "BRAKE --";
                canvas.drawText(angleText + "   PEDAL " + pedalText + "   " + brakeText,
                        getWidth() - 20 * density, getHeight() - 22 * density, paint);
            }
            if (usesArt() && art.error() != null) {
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(art.error(), getWidth() * .5f, getHeight() * .5f, paint);
            }
            paint.clearShadowLayer();
        }

        private void drawFace(Canvas canvas, float brake) {
            float w = getWidth(), h = getHeight();
            float cx = w * .5f, cy = h * .43f;
            float radius = Math.min(w * .29f, h * .34f);
            float density = getResources().getDisplayMetrics().density;
            canvas.drawColor(Color.rgb(15, 20, 31));
            canvas.save();
            canvas.rotate(-steering, cx, cy);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 202, 64));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4 * density);
            paint.setColor(Color.rgb(255, 238, 184));
            canvas.drawCircle(cx, cy, radius - 2 * density, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(55, 42, 35));
            float eyeRadius = radius * (.075f + (pedalOk ? pedal : 0f) * .00115f);
            canvas.drawCircle(cx - radius * .31f, cy - radius * .19f, eyeRadius, paint);
            canvas.drawCircle(cx + radius * .31f, cy - radius * .19f, eyeRadius, paint);

            // Both parabolas share the original smile's endpoints and control point
            // at rest; braking separates them into a filled mouth.
            float endY = cy + radius * .10f;
            float controlY = cy + radius * .66f;
            float opening = radius * .42f * clamp(brake, 0f, 1f);
            mouth.reset();
            mouth.moveTo(cx - radius * .46f, endY);
            mouth.quadTo(cx, controlY - opening, cx + radius * .46f, endY);
            mouth.quadTo(cx, controlY + opening, cx - radius * .46f, endY);
            mouth.close();
            canvas.drawPath(mouth, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(radius * .075f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(mouth, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(239, 96, 87));
            canvas.drawCircle(cx, cy - radius * .91f, radius * .045f, paint);
            canvas.restore();
        }

        private void updateFlip(float dt, float brake) {
            if (brake > .03f) {
                flipping = true;
                flipAngle = (flipAngle + dt * (110f + 650f * brake)) % 360f;
            } else if (flipping) {
                // Finish the current rotation after release, so the car lands upright.
                flipAngle += dt * 280f;
                if (flipAngle >= 360f || flipAngle < .01f) { flipAngle = 0f; flipping = false; }
            }
        }

        private void drawCar(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float width = Math.min(w * .19f, h * .48f);
            float cx = w * .5f - clamp(steering / 450f, -1f, 1f) * w * .035f;
            float lift = flipping ? (float)Math.abs(Math.sin(Math.toRadians(flipAngle) * .5)) * h * .12f : 0f;
            float cy = h * .80f - lift;
            spritePaint.setColor(Color.argb(100, 0, 0, 0));
            shadow.set(cx - width*.42f, h*.85f, cx + width*.42f, h*.885f);
            canvas.drawOval(shadow, spritePaint);
            spritePaint.setColor(Color.WHITE);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(width / carSprite.getWidth(), width / carSprite.getWidth());
            // Project both ends of an extruded body, then bridge every outline
            // edge with a filled quad. The roof and underside are solid planes.
            projectCarFace(frontMatrix, 40f);
            projectCarFace(backMatrix, -40f);
            System.arraycopy(carOutline, 0, frontPoints, 0, carOutline.length);
            System.arraycopy(carOutline, 0, backPoints, 0, carOutline.length);
            frontMatrix.mapPoints(frontPoints);
            backMatrix.mapPoints(backPoints);
            for (int i = 0; i < 8; i++) {
                int j = (i + 1) % 8;
                carSide.reset();
                carSide.moveTo(frontPoints[i*2], frontPoints[i*2+1]);
                carSide.lineTo(frontPoints[j*2], frontPoints[j*2+1]);
                carSide.lineTo(backPoints[j*2], backPoints[j*2+1]);
                carSide.lineTo(backPoints[i*2], backPoints[i*2+1]);
                carSide.close();
                sidePaint.setColor(i == 2 ? Color.rgb(198, 45, 38)
                        : i == 6 ? Color.rgb(39, 39, 49) : Color.rgb(119, 28, 32));
                canvas.drawPath(carSide, sidePaint);
            }
            boolean frontTowardsCamera = Math.cos(Math.toRadians(flipAngle)) >= 0;
            canvas.save();
            canvas.concat(frontTowardsCamera ? frontMatrix : backMatrix);
            canvas.drawBitmap(frontTowardsCamera ? carSprite : carUnderside,
                    -carSprite.getWidth()*.5f, -carSprite.getHeight()*.5f, spritePaint);
            canvas.restore();
            canvas.restore();
        }

        private void projectCarFace(Matrix matrix, float z) {
            spriteCamera.save();
            spriteCamera.setLocation(0, 0, -18);
            spriteCamera.rotateX(-flipAngle);
            spriteCamera.translate(0, 0, z);
            spriteCamera.getMatrix(matrix);
            spriteCamera.restore();
        }

        private Bitmap makeCarUnderside() {
            Bitmap image = Bitmap.createBitmap(220, 130, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(image);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path body = new Path();
            p.setColor(Color.rgb(22, 24, 32));
            c.drawRect(12, 62, 39, 120, p); c.drawRect(181, 62, 208, 120, p);
            body.moveTo(38, 101); body.lineTo(48, 55); body.lineTo(73, 24);
            body.lineTo(147, 24); body.lineTo(172, 55); body.lineTo(182, 101);
            body.lineTo(170, 116); body.lineTo(50, 116); body.close();
            p.setColor(Color.rgb(48, 50, 59)); c.drawPath(body, p);
            p.setColor(Color.rgb(100, 104, 115));
            c.drawRect(55, 50, 165, 57, p); c.drawRect(55, 96, 165, 103, p);
            p.setColor(Color.rgb(172, 172, 175));
            c.drawRect(100, 32, 120, 106, p);
            return image;
        }

        private Bitmap makeCarSprite() {
            Bitmap sprite = Bitmap.createBitmap(220, 130, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(sprite);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path shape = new Path();
            p.setColor(Color.rgb(20, 23, 34));
            c.drawRect(12, 62, 39, 120, p); c.drawRect(181, 62, 208, 120, p);
            p.setColor(Color.rgb(55, 57, 70));
            c.drawRect(16, 73, 32, 105, p); c.drawRect(188, 73, 204, 105, p);
            shape.moveTo(38, 101); shape.lineTo(48, 55); shape.lineTo(73, 24);
            shape.lineTo(147, 24); shape.lineTo(172, 55); shape.lineTo(182, 101);
            shape.lineTo(170, 116); shape.lineTo(50, 116); shape.close();
            p.setColor(Color.rgb(229, 50, 42)); c.drawPath(shape, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.rgb(42, 30, 42)); c.drawPath(shape, p); p.setStyle(Paint.Style.FILL);
            shape.reset(); shape.moveTo(66, 57); shape.lineTo(80, 31); shape.lineTo(140, 31);
            shape.lineTo(154, 57); shape.close();
            p.setColor(Color.rgb(86, 201, 222)); c.drawPath(shape, p);
            p.setColor(Color.rgb(177, 243, 241)); c.drawRect(84, 34, 96, 39, p);
            p.setColor(Color.rgb(255, 204, 72)); c.drawRect(102, 65, 118, 110, p);
            p.setColor(Color.rgb(255, 220, 154)); c.drawRect(46, 84, 75, 94, p); c.drawRect(145, 84, 174, 94, p);
            p.setColor(Color.rgb(54, 40, 49)); c.drawRect(78, 97, 142, 106, p);
            p.setColor(Color.rgb(255, 244, 210)); c.drawRect(88, 99, 132, 102, p);
            return sprite;
        }

        private void render() {
            for (int y = 0; y < H; y++) {
                java.util.Arrays.fill(pixels, y * W, (y + 1) * W, skyColors[y]);
            }
            java.util.Arrays.fill(horizon, H);
            float sn = (float)Math.sin(heading), cs = (float)Math.cos(heading);
            for (float d = 5; d < 650; d += 2f + d * .035f) {
                float fog = Math.min(1f, d / 650f);
                float span = d * .9f;
                float leftX = x + sn*d - cs*span;
                float leftZ = z + cs*d + sn*span;
                float dx = 2f*cs*span/W, dz = -2f*sn*span/W;
                for (int col = 0; col < W; col++) {
                    if (horizon[col] <= 0) continue;
                    float wx = leftX + dx*col, wz = leftZ + dz*col;
                    float h = heightAt(wx, wz);
                    int top = (int)(68 - (h - cameraHeight) * 82f / d);
                    top = Math.max(0, Math.min(H, top));
                    if (top >= horizon[col]) continue;
                    float light = clamp((h + 110f) / 220f, 0, 1);
                    int color = mix(landColors[(int)(light * 255)], skyColors[68], fog * .72f);
                    if (mode == 1) color = mix(background, color, .3f);
                    for (int row = top; row < horizon[col]; row++) pixels[row*W + col] = color;
                    if (mode == 1 && (nearGrid(wx, 32f, d) || nearGrid(wz, 32f, d)))
                        pixels[top*W + col] = mix(accent, skyColors[68], fog * .65f);
                    horizon[col] = top;
                }
            }
        }

        private boolean nearGrid(float v, float spacing, float distance) {
            float remainder = v - (float)Math.floor(v / spacing) * spacing;
            return remainder < Math.min(3f, .6f + distance * .004f);
        }

        private void drawWaves(Canvas canvas) {
            canvas.drawColor(background);
            float w = getWidth(), h = getHeight();
            float energy = pedalOk ? pedal / 100f : 0f;
            energy *= 1f - .6f * (brakeStrength());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, h * .004f));
            paint.setShadowLayer(h * .012f, 0, 0, accent);
            for (int line = 0; line < 20; line++) {
                float shade = line / 19f;
                paint.setColor(mix(landColors[(int)(shade * 255)], accent, .25f));
                wave.reset();
                for (int i = 0; i <= 40; i++) {
                    float px = i * w / 40f;
                    float a = i * .17f + phase * .45f + line * .24f + heading;
                    float b = i * .085f - phase * .3f - line * .33f;
                    float y = h * (.22f + line * .029f)
                            + (float)(Math.sin(a) + .5 * Math.sin(b)) * h * (.025f + .045f * energy);
                    if (i == 0) wave.moveTo(px, y); else wave.lineTo(px, y);
                }
                canvas.drawPath(wave, paint);
            }
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawStars(Canvas canvas) {
            canvas.drawColor(background);
            float w = getWidth(), h = getHeight();
            float energy = pedalOk ? pedal / 100f : 0f;
            float centerX = w * (.5f + .15f * (float)Math.sin(heading));
            float centerY = h * .5f;
            for (int i = 0; i < 130; i++) {
                int seed = i * 1103515245 + 12345;
                float angle = ((seed >>> 8) & 65535) * (6.2831853f / 65535f) + heading * .3f;
                float depth = (float)((((seed >>> 2) & 1023) / 1024f + phase * (.07f + .10f * energy)) % 1f);
                float radius = depth * depth * Math.max(w, h) * .8f;
                float sx = centerX + (float)Math.cos(angle) * radius;
                float sy = centerY + (float)Math.sin(angle) * radius * h / w;
                paint.setColor(mix(landColors[(i * 43) & 255], accent, depth));
                canvas.drawCircle(sx, sy, Math.max(1f, depth * depth * h * .012f), paint);
            }
        }

        private int mix(int a, int b, float t) {
            t = clamp(t, 0f, 1f);
            int r = (int)(((a >> 16) & 255) * (1-t) + ((b >> 16) & 255) * t);
            int g = (int)(((a >> 8) & 255) * (1-t) + ((b >> 8) & 255) * t);
            int bl = (int)((a & 255) * (1-t) + (b & 255) * t);
            return Color.rgb(r, g, bl);
        }

        private float heightAt(float wx, float wz) {
            float gx = wx / 100f, gz = wz / 100f;
            int ix = (int)Math.floor(gx), iz = (int)Math.floor(gz);
            float fx = gx - ix, fz = gz - iz;
            fx = fx*fx*(3 - 2*fx); fz = fz*fz*(3 - 2*fz);
            float a = node(ix, iz), b = node(ix+1, iz);
            float c = node(ix, iz+1), d = node(ix+1, iz+1);
            return (a + (b-a)*fx) * (1-fz) + (c + (d-c)*fx)*fz;
        }

        private float node(int gx, int gz) {
            long key = ((long)gx << 32) | (gz & 0xffffffffL);
            long mixed = key ^ (key >>> 33);
            mixed *= 0xff51afd7ed558ccdL;
            mixed ^= mixed >>> 33;
            int slot = (int)mixed & (TABLE - 1);
            while (used[slot] && keys[slot] != key) slot = (slot + 1) & (TABLE - 1);
            if (!used[slot]) {
                used[slot] = true; keys[slot] = key; entries++;
                float random = ((mixed >>> 40) & 0xffff) / 32767.5f - 1f;
                heights[slot] = random * (18f + (pedalOk ? pedal : 0f) * 1.7f);
            }
            return heights[slot];
        }
        private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    }
}

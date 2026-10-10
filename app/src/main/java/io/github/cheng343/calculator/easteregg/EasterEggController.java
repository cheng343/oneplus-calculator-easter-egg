package io.github.cheng343.calculator.easteregg;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieListener;
import com.airbnb.lottie.LottieTask;
import com.airbnb.lottie.RenderMode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class EasterEggController {
    interface Logger { void write(String message, Throwable error); }
    private static final String BUNDLED_ASSET_DIR = "assets/";
    private final ClassLoader targetLoader;
    private final List<String> moduleApks;
    private final Logger logger;
    private final Map<Object, Session> sessions = new IdentityHashMap<>();

    EasterEggController(ClassLoader targetLoader, List<String> moduleApks, Logger logger) {
        this.targetLoader = targetLoader;
        this.moduleApks = moduleApks;
        this.logger = logger;
    }

    /**
     * Reads an animation out of the module APK itself. 17.2.16 deleted all three
     * never_settle assets from the calculator, so the module carries its own copy.
     * Returns null when the module copy is unavailable; the caller then falls back
     * to the host assets, which still exist on 16.4.2 and 17.2.14.
     */
    private byte[] bundledAsset(String name) {
        for (String apk : moduleApks) {
            try (ZipFile zip = new ZipFile(apk)) {
                ZipEntry entry = zip.getEntry(BUNDLED_ASSET_DIR + name);
                if (entry == null) continue;
                try (InputStream input = zip.getInputStream(entry)) {
                    ByteArrayOutputStream output =
                            new ByteArrayOutputStream((int) Math.max(1024L, entry.getSize()));
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    return output.toByteArray();
                }
            } catch (IOException ignored) {
                // Try the next split path; the caller falls back to the host assets.
            }
        }
        return null;
    }

    boolean onClick(Object fragment, View button) {
        if (fragment == null || Looper.myLooper() != Looper.getMainLooper()) return false;
        try {
            Session current = sessions.get(fragment);
            if (current != null) {
                // Old h3.g.onClick blocks input until both intro and Lottie finish.
                if (current.blocksInput()) return true;
                current.exit();
                return false;
            }
            View candidate = (View) Reflect.field(fragment, "d0");
            if (!(candidate instanceof ViewGroup)) return false;
            ViewGroup root = (ViewGroup) candidate;
            Context context = root.getContext();
            int equalId = resource(context, "eq", "id");
            TextView formula = formula(fragment, root, context);
            boolean trigger = equalId != 0 && button.getId() == equalId
                    && formula != null && TriggerMatcher.matches(formula.getText());
            if (!trigger || !root.isAttachedToWindow()) return false;
            Session session = new Session(fragment, root, formula);
            sessions.put(fragment, session);
            try {
                session.start();
                return true;
            } catch (Throwable error) {
                dismiss(fragment);
                throw error;
            }
        } catch (Throwable error) {
            logger.write("Could not display Easter egg; keeping original keypad behavior", error);
            return false;
        }
    }

    void dismiss(Object fragment) {
        Session session = sessions.remove(fragment);
        if (session != null) session.close();
    }

    private TextView formula(Object fragment, ViewGroup root, Context context) {
        int id = resource(context, "formula", "id");
        View view = id == 0 ? null : root.findViewById(id);
        if (view instanceof TextView) return (TextView) view;
        try {
            Object field = Reflect.field(fragment, "K");
            return field instanceof TextView ? (TextView) field : null;
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    @SuppressWarnings("DiscouragedApi")
    private static int resource(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    private final class Session {
        private final Object fragment;
        private final ViewGroup root;
        private final Context context;
        private final TextView formula;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final FrameLayout viewport;
        private final ImageView animation;
        private final boolean oos16;
        private final boolean landscape;
        private final String asset;
        private final View.OnLayoutChangeListener layoutListener;
        private final View.OnAttachStateChangeListener attachListener;
        private LottieTask<LottieComposition> task;
        private LottieListener<LottieComposition> successListener;
        private LottieListener<Throwable> failureListener;
        private LottieDrawable drawable;
        private AnimatorSet intro;
        private ObjectAnimator exitAnimator;
        private Runnable loadTimeout;
        private Runnable playbackTimeout;
        private String stage = "loading";
        private String assetSource = "unknown";
        private int generation;
        private boolean shown;
        private boolean interactionDisabled;
        private boolean closed;

        // Host Context has host IDs; AppCompat would resolve module style IDs against it.
        @SuppressLint("AppCompatCustomView")
        Session(Object fragment, ViewGroup root, TextView formula) {
            this.fragment = fragment;
            this.root = root;
            this.formula = formula;
            context = root.getContext();
            // The classic/OOS16 split is the old t3.k1.H0() (16.4.2), which 17.2.14
            // still exposes as c3.i1.H0(): Build.VERSION.SDK_INT > 35 AND the brand
            // flag, and that flag is "oneplus".equalsIgnoreCase(Build.BRAND).
            // 17.2.16 deleted c3.i1.H0() and hard-coded the flag to false, so the
            // condition is evaluated from the platform rather than from a class name
            // that the obfuscator reassigns every release. K0 is the old N0
            // "confidential" flag and j is the landscape flag.
            oos16 = Build.VERSION.SDK_INT > 35
                    && "oneplus".equalsIgnoreCase(Build.BRAND)
                    && !flag(fragment, "K0", false);
            landscape = flag(fragment, "j", false);
            boolean night = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            asset = oos16 ? (night ? "never_settle_animation_oos16_dark.json"
                    : "never_settle_animation_oos16_light.json") : "never_settle_animation.json";
            viewport = new FrameLayout(context);
            copyHostPadding();
            viewport.setClipChildren(true);
            viewport.setClipToPadding(true);
            viewport.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            animation = new ImageView(context) {
                private boolean drawFailed;
                @Override protected void onDraw(Canvas canvas) {
                    if (drawFailed || closed) return;
                    try { super.onDraw(canvas); }
                    catch (Throwable error) {
                        drawFailed = true;
                        handler.post(() -> fail("draw_failed", error));
                    }
                }
            };
            animation.setScaleType(ImageView.ScaleType.FIT_CENTER);
            animation.setContentDescription("Never Settle");
            animation.setVisibility(View.INVISIBLE);
            // Old I1 uses MATCH_PARENT x WRAP_CONTENT, with default top gravity.
            animation.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));
            layoutListener = (view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                try { position(); }
                catch (Throwable error) { fail("layout_failed", error); }
            };
            attachListener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) { dismiss(Session.this.fragment); }
            };
        }

        private boolean flag(Object owner, String name, boolean fallback) {
            try {
                return Boolean.TRUE.equals(Reflect.field(owner, name));
            } catch (ReflectiveOperationException error) {
                logger.write("Optional fragment flag " + name + " is unavailable; assuming "
                        + fallback + " for this host", null);
                return fallback;
            }
        }

        private void copyHostPadding() {
            int stubId = resource(context, "easter_egg_layout_stub", "id");
            View stub = stubId == 0 ? null : root.findViewById(stubId);
            View template = null;
            if (stub instanceof ViewStub) {
                // Inflate a detached native template; leave the fragment's stub untouched.
                template = LayoutInflater.from(context).inflate(((ViewStub) stub).getLayoutResource(), root, false);
            } else {
                int containerId = resource(context, "easter_egg_layout", "id");
                if (containerId != 0) template = root.findViewById(containerId);
            }
            if (template == null) throw new IllegalStateException("Missing host Easter egg layout template");
            viewport.setPadding(template.getPaddingLeft(), template.getPaddingTop(),
                    template.getPaddingRight(), template.getPaddingBottom());
        }

        void start() {
            root.addOnLayoutChangeListener(layoutListener);
            root.addOnAttachStateChangeListener(attachListener);
            final int token = ++generation;
            // Old z2() calls U1() as its very first step, before it touches the egg
            // container, so the formula is cleared on the key press itself instead of
            // waiting for the animation to be parsed. The original closes formula
            // editing when the intro starts; doing it in the same step removes the
            // window in which the user could type into the already-cleared formula.
            // finish(), exit() and close() all restore it.
            try {
                // 17.2.14 and 17.2.16 keep U1() as the private method L1().
                Reflect.call(fragment, "L1");
                setInteraction(false);
            } catch (ReflectiveOperationException error) {
                fail("clear_failed", error);
                return;
            }
            InputStream stream = null;
            try {
                // Prefer the module's own copy: 17.2.16 ships no never_settle asset
                // at all. 16.4.2 and 17.2.14 still carry byte-identical files, so
                // the host fallback keeps those versions working unchanged.
                byte[] bundled = bundledAsset(asset);
                assetSource = bundled != null ? "module" : "host";
                stream = bundled != null ? new ByteArrayInputStream(bundled)
                        : context.getAssets().open(asset);
                logger.write("16.4.2 profile=" + (oos16 ? "OOS16" : "classic")
                        + "; landscape=" + landscape + "; asset=" + asset
                        + "; source=" + assetSource
                        + "; independent renderer="
                        + (LottieDrawable.class.getClassLoader() != targetLoader), null);
                task = LottieCompositionFactory.fromJsonInputStream(stream,
                        "oneplus-easter-egg:" + assetSource + ":" + asset);
                // Factory owns and closes the input stream, including cache hits.
                stream = null;
                successListener = composition -> handler.post(() -> {
                    if (!closed && generation == token) loaded(composition);
                });
                failureListener = error -> handler.post(() -> {
                    if (!closed && generation == token) fail("parse_failed", error);
                });
                loadTimeout = () -> {
                    if (!closed && generation == token && stage.equals("loading"))
                        fail("parse_timeout", new IllegalStateException("Asset load exceeded 10 seconds"));
                };
                handler.postDelayed(loadTimeout, 10000);
                task.addListener(successListener);
                task.addFailureListener(failureListener);
            } catch (Throwable error) {
                if (stream != null) {
                    try { stream.close(); } catch (Exception ignored) {}
                }
                fail("asset_failed", error);
            }
        }

        private void loaded(LottieComposition composition) {
            if (closed || !stage.equals("loading")) return;
            handler.removeCallbacks(loadTimeout);
            detachTask();
            try {
                drawable = new LottieDrawable();
                drawable.setRenderMode(RenderMode.SOFTWARE);
                drawable.setIgnoreDisabledSystemAnimations(true);
                drawable.setRepeatCount(0);
                drawable.setComposition(composition);
                drawable.addAnimatorListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animator) {
                        handler.post(() -> {
                            if (!closed && stage.equals("playing")) finish();
                        });
                    }
                });
                animation.setImageDrawable(drawable);
                stage = "entering";
                root.getOverlay().add(viewport);
                shown = true;
                position();
                logger.write("Composition ready: " + asset + "; duration=" + composition.getDuration()
                        + "ms; viewport=" + viewport.getWidth() + "x" + viewport.getHeight(), null);
                if (oos16) startOos16();
                else startClassic();
            } catch (Throwable error) { fail("prepare_failed", error); }
        }

        private void startClassic() {
            int red = color("op_never_settle_bg_color", 0xffdb382c);
            View bar = new View(context);
            bar.setBackgroundColor(red);
            bar.setTranslationX(-root.getWidth());
            viewport.addView(bar, new FrameLayout.LayoutParams(-1, dp(4), Gravity.CENTER));
            View background = new View(context);
            background.setLayoutParams(new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            background.setScaleY(0);
            if (landscape) background.setBackgroundColor(red);
            else background.setBackground(context.getDrawable(resource(context, "round_bg", "drawable")));
            ObjectAnimator slide = ObjectAnimator.ofFloat(bar, View.TRANSLATION_X, -root.getWidth(), 0);
            slide.setDuration(367);
            slide.setInterpolator(new PathInterpolator(0.17f, 0f, 0.1f, 1f));
            slide.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animator) {
                    if (closed) return;
                    viewport.addView(background);
                    viewport.addView(animation);
                    play();
                }
            });
            ObjectAnimator expand = ObjectAnimator.ofFloat(background, View.SCALE_Y, 0, 1);
            expand.setDuration(800);
            expand.setInterpolator(new PathInterpolator(0.17f, 0f, 0.1f, 1f));
            intro = new AnimatorSet();
            intro.playSequentially(slide, expand);
            position();
            intro.start();
        }

        private void startOos16() {
            RoundedFrame clip = new RoundedFrame(context);
            clip.setForceDarkAllowed(false);
            viewport.addView(clip, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            position();
            int w = clip.getMeasuredWidth();
            int h = clip.getMeasuredHeight();
            int diameter = ((int) Math.sqrt((double) (((w / 2) * w) / 2)
                    + (double) (((h / 2) * h) / 2))) * 2;
            View circle = new View(context);
            GradientDrawable circleShape = new GradientDrawable();
            circleShape.setShape(GradientDrawable.OVAL);
            int dark = color("op_never_settle_bg_color_oos_16_1", isNight() ? 0xff3e3e3e : 0xff171717);
            circleShape.setColor(dark);
            circle.setBackground(circleShape);
            circle.setScaleX(0);
            circle.setScaleY(0);
            clip.addView(circle, new FrameLayout.LayoutParams(diameter, diameter, Gravity.CENTER));
            viewport.addView(animation);
            ObjectAnimator x = ObjectAnimator.ofFloat(circle, View.SCALE_X, 0, 1);
            ObjectAnimator y = ObjectAnimator.ofFloat(circle, View.SCALE_Y, 0, 1);
            for (ObjectAnimator scale : new ObjectAnimator[]{x, y}) {
                scale.setDuration(300);
                scale.setStartDelay(1216);
                scale.setInterpolator(new PathInterpolator(0.3f, 0f, 0.1f, 1f));
            }
            AnimatorSet expansion = new AnimatorSet();
            expansion.playTogether(x, y);
            int blue = color("op_never_settle_bg_color_oos_16_2", 0xff00a1ff);
            int cyan = color("op_never_settle_bg_color_oos_16_3", 0xff1dbacc);
            int orange = color("op_never_settle_bg_color_oos_16_4", 0xfff89832);
            int red = color("op_never_settle_bg_color_oos_16_5", 0xffda382b);
            AnimatorSet colors = new AnimatorSet();
            // Preserve the overlapping delays/order of old e2/p1, including their default interpolator.
            colors.playTogether(colorTransition(circle, circleShape, dark, blue, 217, 0),
                    colorTransition(circle, circleShape, blue, cyan, 200, 0),
                    colorTransition(circle, circleShape, cyan, orange, 416, 217),
                    colorTransition(circle, circleShape, orange, red, 500, 200));
            intro = new AnimatorSet();
            intro.play(expansion).before(colors);
            play();
            if (!closed) intro.start();
        }

        private ValueAnimator colorTransition(View view, GradientDrawable shape, int from, int to,
                                               long duration, long delay) {
            ValueAnimator animator;
            if (landscape) animator = ObjectAnimator.ofArgb(view, "backgroundColor", from, to);
            else {
                animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
                animator.addUpdateListener(value -> shape.setColor((Integer) value.getAnimatedValue()));
            }
            animator.setDuration(duration);
            animator.setStartDelay(delay);
            return animator;
        }

        private void play() {
            if (closed || !stage.equals("entering")) return;
            try {
                animation.setVisibility(View.VISIBLE);
                position();
                drawable.setProgress(0);
                drawable.setVisible(true, false);
                stage = "playing";
                drawable.playAnimation();
                logger.write("Animation started: " + asset + "; image="
                        + animation.getWidth() + "x" + animation.getHeight(), null);
                playbackTimeout = () -> {
                    if (closed || !stage.equals("playing")) return;
                    if (drawable.getProgress() >= 0.99f) finish();
                    else fail("playback_timeout", new IllegalStateException("progress="
                            + drawable.getProgress() + "; running=" + drawable.isAnimating()));
                };
                handler.postDelayed(playbackTimeout, Math.max(4000,
                        (long) drawable.getComposition().getDuration() + 3000));
            } catch (Throwable error) { fail("play_failed", error); }
        }

        private void finish() {
            if (closed || !stage.equals("playing")) return;
            stage = "finished";
            if (playbackTimeout != null) handler.removeCallbacks(playbackTimeout);
            try { setInteraction(true); }
            catch (ReflectiveOperationException error) { fail("restore_input_failed", error); return; }
            // Old animator listener f leaves the last frame visible; s1 fades on the next key.
            logger.write("Animation ended: " + asset + "; retaining original final frame", null);
        }

        boolean blocksInput() {
            return !stage.equals("exiting") && (stage.equals("loading") || stage.equals("entering")
                    || stage.equals("playing") || (intro != null && intro.isRunning()));
        }

        void exit() {
            if (closed || stage.equals("exiting")) return;
            stage = "exiting";
            exitAnimator = ObjectAnimator.ofFloat(viewport, View.ALPHA, 1, 0);
            exitAnimator.setDuration(275);
            exitAnimator.setInterpolator(new PathInterpolator(0.33f, 0f, 0.67f, 1f));
            exitAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animator) {
                    if (closed) return;
                    sessions.remove(fragment, Session.this);
                    close();
                }
            });
            exitAnimator.start();
        }

        private void setInteraction(boolean enabled) throws ReflectiveOperationException {
            Reflect.call(formula, "setUserInteractionEnabled", new Class<?>[]{boolean.class}, enabled);
            interactionDisabled = !enabled;
        }

        private void fail(String reason, Throwable error) {
            if (closed) return;
            logger.write(reason + ": asset=" + asset + "; source=" + assetSource
                    + "; stage=" + stage, error);
            dismiss(fragment);
            Toast.makeText(context, "彩蛋播放失败（" + reason + "），请查看框架日志", Toast.LENGTH_LONG).show();
        }

        private void position() {
            if (closed || !shown || root.getWidth() <= 0 || root.getHeight() <= 0) return;
            int id = resource(context, "drawer_layout", "id");
            View bottomView = id == 0 ? null : root.findViewById(id);
            if (bottomView == null) {
                id = resource(context, "pad_numeric", "id");
                if (id != 0) bottomView = root.findViewById(id);
            }
            if (bottomView == null) throw new IllegalStateException("Missing host keypad boundary");
            int[] rootLocation = new int[2];
            int[] boundaryLocation = new int[2];
            root.getLocationInWindow(rootLocation);
            bottomView.getLocationInWindow(boundaryLocation);
            int height = Math.min(root.getHeight(), boundaryLocation[1] - rootLocation[1]);
            if (height <= 0) throw new IllegalStateException("Animation viewport has zero height");
            viewport.forceLayout();
            viewport.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            // Let FrameLayout measure/layout its children with the host padding and WRAP_CONTENT.
            viewport.layout(0, 0, root.getWidth(), height);
        }

        private boolean isNight() {
            return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        }

        private int color(String name, int fallback) {
            int id = resource(context, name, "color");
            return id == 0 ? fallback : context.getColor(id);
        }

        private int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }

        private void detachTask() {
            if (task == null) return;
            if (successListener != null) task.removeListener(successListener);
            if (failureListener != null) task.removeFailureListener(failureListener);
            task = null;
            successListener = null;
            failureListener = null;
        }

        void close() {
            if (closed) return;
            closed = true;
            ++generation;
            handler.removeCallbacksAndMessages(null);
            detachTask();
            if (intro != null) intro.cancel();
            if (exitAnimator != null) exitAnimator.cancel();
            root.removeOnLayoutChangeListener(layoutListener);
            root.removeOnAttachStateChangeListener(attachListener);
            if (drawable != null) {
                drawable.removeAllAnimatorListeners();
                drawable.cancelAnimation();
                drawable.setCallback(null);
            }
            if (interactionDisabled) {
                try { setInteraction(true); }
                catch (ReflectiveOperationException error) { logger.write("Could not restore formula input", error); }
            }
            if (shown) root.getOverlay().remove(viewport);
            animation.setImageDrawable(null);
            viewport.removeAllViews();
        }

        private final class RoundedFrame extends FrameLayout {
            private final Path clip = new Path();
            private boolean reportedFallback;

            RoundedFrame(Context context) {
                super(context);
                // FrameLayout otherwise skips draw(Canvas) when it has no background.
                setWillNotDraw(false);
            }

            @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
                super.onSizeChanged(w, h, oldW, oldH);
                clip.reset();
                int radiusId = resource(context, "dimens_9dp", "dimen");
                float radius = radiusId == 0 ? dp(9)
                        : context.getResources().getDimensionPixelOffset(radiusId);
                try {
                    Class<?> oplusPath = Class.forName("com.oplus.graphics.OplusPath", false, targetLoader);
                    Object smooth = oplusPath.getConstructor(Path.class).newInstance(clip);
                    oplusPath.getMethod("addSmoothRoundRect", float.class, float.class, float.class,
                            float.class, float.class, float.class, float.class, Path.Direction.class)
                            .invoke(smooth, 0f, 0f, (float) w, (float) h, radius, radius, 0.99f, Path.Direction.CW);
                } catch (ReflectiveOperationException | LinkageError error) {
                    clip.reset();
                    clip.addRoundRect(0, 0, w, h, radius, radius, Path.Direction.CW);
                    if (!reportedFallback) {
                        reportedFallback = true;
                        logger.write("Oplus smooth corners unavailable; using platform 9dp corners", error);
                    }
                }
            }

            @Override public void draw(Canvas canvas) {
                int save = canvas.save();
                canvas.clipPath(clip);
                super.draw(canvas);
                canvas.restoreToCount(save);
            }
        }
    }
}

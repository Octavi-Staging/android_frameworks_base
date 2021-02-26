/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics.drawable;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Canvas;
import android.graphics.CanvasProperty;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.animation.RenderNodeAnimator;
import android.util.ArraySet;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import java.util.function.Consumer;

/**
 * @hide
 */
public final class RippleAnimationSession {
    private static final String TAG = "RippleAnimationSession";
    private static final int ENTER_ANIM_DURATION = 300;
    private static final int SLIDE_ANIM_DURATION = 450;
    private static final int EXIT_ANIM_DURATION = 300;
    private static final TimeInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();
    private static final TimeInterpolator PATH_INTERPOLATOR =
            new PathInterpolator(.2f, 0, 0, 1f);
    private Consumer<RippleAnimationSession> mOnSessionEnd;
    private final AnimationProperties<Float, Paint> mProperties;
    private AnimationProperties<CanvasProperty<Float>, CanvasProperty<Paint>> mCanvasProperties;
    private Runnable mOnUpdate;
    private long mStartTime;
    private boolean mForceSoftware;
    private final float mWidth, mHeight;
    private final ValueAnimator mSparkle = ValueAnimator.ofFloat(0, 1);
    private final ArraySet<Animator> mActiveAnimations = new ArraySet<>(3);

    RippleAnimationSession(@NonNull AnimationProperties<Float, Paint> properties,
            boolean forceSoftware, float width, float height) {
        mProperties = properties;
        mForceSoftware = forceSoftware;
        mWidth = width;
        mHeight = height;

        mSparkle.addUpdateListener(anim -> {
            final long now = AnimationUtils.currentAnimationTimeMillis();
            final long elapsed = now - mStartTime - ENTER_ANIM_DURATION;
            final float phase = (float) elapsed / 1000f;
            mProperties.getShader().setSecondsOffset(phase);
            notifyUpdate();
        });
        mSparkle.setDuration(ENTER_ANIM_DURATION);
        mSparkle.setStartDelay(ENTER_ANIM_DURATION);
        mSparkle.setInterpolator(LINEAR_INTERPOLATOR);
        mSparkle.setRepeatCount(ValueAnimator.INFINITE);
    }

    @NonNull RippleAnimationSession enter(Canvas canvas) {
        if (isHwAccelerated(canvas)) {
            enterHardware((RecordingCanvas) canvas);
        } else {
            enterSoftware();
        }
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        return this;
    }

    @NonNull RippleAnimationSession exit(Canvas canvas) {
        mSparkle.end();
        if (isHwAccelerated(canvas)) exitHardware((RecordingCanvas) canvas);
        else exitSoftware();
        return this;
    }

    private void onAnimationEnd(Animator anim) {
        notifyUpdate();
        mActiveAnimations.remove(anim);
    }

    @NonNull RippleAnimationSession setOnSessionEnd(
            @Nullable Consumer<RippleAnimationSession> onSessionEnd) {
        mOnSessionEnd = onSessionEnd;
        return this;
    }

    RippleAnimationSession setOnAnimationUpdated(@Nullable Runnable run) {
        mOnUpdate = run;
        return this;
    }

    private boolean isHwAccelerated(Canvas canvas) {
        return canvas.isHardwareAccelerated() && !mForceSoftware;
    }

    private void exitSoftware() {
        ValueAnimator expand = ValueAnimator.ofFloat(.5f, 1f);
        expand.setDuration(EXIT_ANIM_DURATION);
        expand.setStartDelay(computeDelay());
        expand.addUpdateListener(updatedAnimation -> {
            notifyUpdate();
            mProperties.getShader().setProgress((Float) expand.getAnimatedValue());
        });
        expand.addListener(new AnimatorListener(this) {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                Consumer<RippleAnimationSession> onEnd = mOnSessionEnd;
                if (onEnd != null) onEnd.accept(RippleAnimationSession.this);
            }
        });
        expand.setInterpolator(LINEAR_INTERPOLATOR);
        expand.start();
        mActiveAnimations.add(expand);
    }

    private long computeDelay() {
        final long timePassed =  AnimationUtils.currentAnimationTimeMillis() - mStartTime;
        return Math.max((long) SLIDE_ANIM_DURATION - timePassed, 0);
    }

    private void notifyUpdate() {
        if (mOnUpdate != null) mOnUpdate.run();
    }

    RippleAnimationSession setForceSoftwareAnimation(boolean forceSw) {
        mForceSoftware = forceSw;
        return this;
    }


    private void exitHardware(RecordingCanvas canvas) {
        AnimationProperties<CanvasProperty<Float>, CanvasProperty<Paint>>
                props = getCanvasProperties();
        RenderNodeAnimator exit =
                new RenderNodeAnimator(props.getProgress(), 1f);
        exit.setDuration(EXIT_ANIM_DURATION);
        exit.addListener(new AnimatorListener(this) {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                Consumer<RippleAnimationSession> onEnd = mOnSessionEnd;
                if (onEnd != null) onEnd.accept(RippleAnimationSession.this);
            }
        });
        exit.setTarget(canvas);
        exit.setInterpolator(LINEAR_INTERPOLATOR);

        long delay = computeDelay();
        exit.setStartDelay(delay);
        exit.start();
        mActiveAnimations.add(exit);
    }

    private void enterHardware(RecordingCanvas canvas) {
        AnimationProperties<CanvasProperty<Float>, CanvasProperty<Paint>>
                props = getCanvasProperties();
        RenderNodeAnimator expand =
                new RenderNodeAnimator(props.getProgress(), .5f);
        RenderNodeAnimator slideX =
                new RenderNodeAnimator(props.getX(), mWidth / 2);
        RenderNodeAnimator slideY =
                new RenderNodeAnimator(props.getY(), mHeight / 2);
        expand.setTarget(canvas);
        slideX.setTarget(canvas);
        slideY.setTarget(canvas);
        startAnimation(expand, slideX, slideY);
    }

    private void startAnimation(Animator expand,
            Animator slideX, Animator slideY) {
        expand.setDuration(SLIDE_ANIM_DURATION);
        slideX.setDuration(SLIDE_ANIM_DURATION);
        slideY.setDuration(SLIDE_ANIM_DURATION);
        slideX.addListener(new AnimatorListener(this));
        expand.setInterpolator(LINEAR_INTERPOLATOR);
        slideX.setInterpolator(PATH_INTERPOLATOR);
        slideY.setInterpolator(PATH_INTERPOLATOR);
        expand.start();
        slideX.start();
        slideY.start();
        if (!mSparkle.isRunning()) {
            mSparkle.start();
            mActiveAnimations.add(mSparkle);
        }
        mActiveAnimations.add(expand);
        mActiveAnimations.add(slideX);
        mActiveAnimations.add(slideY);
    }

    private void enterSoftware() {
        ValueAnimator expand = ValueAnimator.ofFloat(0f, 0.5f);
        ValueAnimator slideX = ValueAnimator.ofFloat(
                mProperties.getX(), mWidth / 2);
        ValueAnimator slideY = ValueAnimator.ofFloat(
                mProperties.getY(), mHeight / 2);
        expand.addUpdateListener(updatedAnimation -> {
            notifyUpdate();
            mProperties.getShader().setProgress((Float) expand.getAnimatedValue());
        });
        slideX.addUpdateListener(anim -> {
            float x = (float) slideX.getAnimatedValue();
            float y = (float) slideY.getAnimatedValue();
            mProperties.setOrigin(x, y);
            mProperties.getShader().setOrigin(x, y);
        });
        startAnimation(expand, slideX, slideY);
    }

    @NonNull AnimationProperties<Float, Paint> getProperties() {
        return mProperties;
    }

    @NonNull
    AnimationProperties<CanvasProperty<Float>, CanvasProperty<Paint>> getCanvasProperties() {
        if (mCanvasProperties == null) {
            mCanvasProperties = new AnimationProperties<>(
                    CanvasProperty.createFloat(mProperties.getX()),
                    CanvasProperty.createFloat(mProperties.getY()),
                    CanvasProperty.createFloat(mProperties.getMaxRadius()),
                    CanvasProperty.createPaint(mProperties.getPaint()),
                    CanvasProperty.createFloat(mProperties.getProgress()),
                    mProperties.getShader());
        }
        return mCanvasProperties;
    }

    private static class AnimatorListener implements Animator.AnimatorListener {
        private final RippleAnimationSession mSession;

        AnimatorListener(RippleAnimationSession session) {
            mSession = session;
        }

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mSession.onAnimationEnd(animation);
        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    static class AnimationProperties<FloatType, PaintType> {
        private final FloatType mProgress;
        private final FloatType mMaxRadius;
        private final PaintType mPaint;
        private final RippleShader mShader;
        private FloatType mX;
        private FloatType mY;

        AnimationProperties(FloatType x, FloatType y, FloatType maxRadius,
                PaintType paint, FloatType progress, RippleShader shader) {
            mY = y;
            mX = x;
            mMaxRadius = maxRadius;
            mPaint = paint;
            mShader = shader;
            mProgress = progress;
        }

        FloatType getProgress() {
            return mProgress;
        }

        void setOrigin(FloatType x, FloatType y) {
            mX = x;
            mY = y;
        }

        FloatType getX() {
            return mX;
        }

        FloatType getY() {
            return mY;
        }

        FloatType getMaxRadius() {
            return mMaxRadius;
        }

        PaintType getPaint() {
            return mPaint;
        }

        RippleShader getShader() {
            return mShader;
        }
    }
}

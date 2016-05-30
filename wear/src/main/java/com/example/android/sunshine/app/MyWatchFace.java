/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mRegisteredDataReceiver = false;
        Paint mBackgroundPaint;

        Paint mBackgroundPaintAmbient;
        Paint mBackgroundPaintInteractive;


        Paint mTextPaint;
        Paint mTemperaturePaintHigh;
        Paint mTemperaturePaintLow;
        Paint mDatePaint;
        boolean mAmbient;
        Time mTime;

        float mLowTemp;
        float mHighTemp;
        Bitmap mWeatherImage;
        Calendar mCalendar;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        final BroadcastReceiver mDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//                mTime.clear(intent.getStringExtra("temp"));
//                mTime.setToNow();
                mLowTemp = intent.getFloatExtra("low-temp", -1);
                mHighTemp = intent.getFloatExtra("high-temp", -2);

                Asset asset = intent.getParcelableExtra("weather-image");

                LoadImageAsyncTask loadImageAsyncTask = new LoadImageAsyncTask();
                loadImageAsyncTask.execute(asset);
//                mWeatherImage = loadBitmapFromAsset(asset);
            }

            class LoadImageAsyncTask extends AsyncTask<Asset, Void, Bitmap> {
                @Override
                protected Bitmap doInBackground(Asset... params) {
                    Asset asset = params[0];
                    Bitmap bitmap;
                    bitmap = loadBitmapFromAsset(asset);
                    return bitmap;
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
//                    super.onPostExecute(aVoid);
                    mWeatherImage = bitmap;
                }
            }

            public Bitmap loadBitmapFromAsset(Asset asset) {

                final int TIMEOUT_MS = 10000;
                final String TAG = "loadBitmapFromAsset";

                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }
                ConnectionResult result =
                        mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    return null;
                }
                // convert asset into a file descriptor and block until it's ready
                InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                        mGoogleApiClient, asset).await().getInputStream();
                mGoogleApiClient.disconnect();

                if (assetInputStream == null) {
                    Log.w(TAG, "Requested an unknown Asset.");
                    return null;
                }
                // decode the stream into a bitmap
                return BitmapFactory.decodeStream(assetInputStream);
            }

        };

        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(com.example.android.sunshine.app.R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(com.example.android.sunshine.app.R.color.background));

            // TODO
            mBackgroundPaintAmbient = new Paint();
            mBackgroundPaintAmbient.setColor(resources.getColor(R.color.background));

            mBackgroundPaintInteractive = new Paint();
            mBackgroundPaintInteractive.setColor(resources.getColor(R.color.udacity_blue));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(com.example.android.sunshine.app.R.color.digital_text));

            mTemperaturePaintHigh = new Paint();
            mTemperaturePaintHigh = createTemperaturePaint(resources.getColor(R.color.udacity_white));

            mTemperaturePaintLow = new Paint();
            mTemperaturePaintLow = createTemperaturePaint(resources.getColor(R.color.udacity_light_blue));

            mDatePaint = new Paint();
            mDatePaint = createDatePaint(resources.getColor(R.color.udacity_light_blue));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createTemperaturePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(40.0f);
            return paint;
        }

        private Paint createDatePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(20.0f);
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                registerDataReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
                unregisterDataReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void registerDataReceiver() {
            if (mRegisteredDataReceiver) {
                return;
            }
            mRegisteredDataReceiver = true;
            IntentFilter filter = new IntentFilter("com.sheajohn.wear.DATA_UPDATED");
            MyWatchFace.this.registerReceiver(mDataReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void unregisterDataReceiver() {
            if (!mRegisteredDataReceiver) {
                return;
            }
            mRegisteredDataReceiver = false;
            MyWatchFace.this.unregisterReceiver(mDataReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? com.example.android.sunshine.app.R.dimen.digital_x_offset_round : com.example.android.sunshine.app.R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? com.example.android.sunshine.app.R.dimen.digital_text_size_round : com.example.android.sunshine.app.R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            com.example.android.sunshine.app.R.color.background : com.example.android.sunshine.app.R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaintInteractive);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, (int)(canvas.getWidth() * 0.50f), mYOffset, mTextPaint);

            if (!mAmbient) {

                SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE, MMM dd yyyy");
                String date = shortenedDateFormat.format(System.currentTimeMillis());
                date = date.toUpperCase();
                canvas.drawText(date, (int)(canvas.getWidth() * 0.50f), (int)(canvas.getHeight() * 0.50f), mDatePaint);

                // Draw low and high temp
                if (mLowTemp != 0 && mHighTemp != 0) {

                    String suffix = "\u00B0";

                    String lowTemp = String.valueOf((int)mLowTemp) + suffix;
                    String highTemp = String.valueOf((int) mHighTemp) + suffix;

                    mTemperaturePaintHigh.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(highTemp, (int) (canvas.getWidth() * 0.50f), canvas.getHeight() * 0.75f, mTemperaturePaintHigh);

                    mTemperaturePaintLow.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(lowTemp, (int) (canvas.getWidth() * 0.75f), canvas.getHeight() * 0.75f, mTemperaturePaintLow);
                }

                if (mWeatherImage != null) {

                    int sideLength = (int)(canvas.getWidth() * 0.20f);

                    int left = (int)(canvas.getWidth() * 0.25f) - (int)(sideLength / 2);
                    int top = (int)(canvas.getHeight() * 0.60f);
                    int right = left + sideLength;
                    int bottom = top + sideLength;

                    Rect dst = new Rect(left, top, right, bottom);

                    canvas.drawBitmap(mWeatherImage, null, dst, mTextPaint);
                }
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}

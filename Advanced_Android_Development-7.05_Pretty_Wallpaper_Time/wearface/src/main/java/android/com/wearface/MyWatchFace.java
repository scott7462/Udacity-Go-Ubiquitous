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

package android.com.wearface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import org.joda.time.DateTime;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
/**
 * Copyright (C) 2015 The Android Open Source Project
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
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

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> weakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            weakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = weakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    /**
     * Add the
     * GoogleApiClient.ConnectionCallbacks and GoogleApiClient.OnConnectionFailedListener
     * to manager the Callbacks in google apis.
     */
    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        /**
         * Add static values to manager the data of tge wear.
         */
        private static final String WEATHER_PATH = "/weather";
        private static final String HIGH_TEMPERATURE = "high_temperature";
        private static final String LOW_TEMPERATURE = "low_temperature";
        private static final String WEATHER_CONDITION = "weather_condition";
        private static final int SPACE_BETWEEN_TEMPERATURES = 10;

        private GoogleApiClient googleApiClient;
        final Handler updateTimeHandler = new EngineHandler(this);
        boolean isRegisteredTimeZoneReceiver = false;

        Paint backgroundPaint;
        Paint timeTextPaint;
        Paint linePaint;
        Paint dateTextPaint;
        Paint highTemperatureTextPaint;
        Paint lowTemperatureTextPaint;

        float timeOffset;
        float dateOffset;
        float weatherOffset;

        boolean isAmbient;

        int digitalTextColor;
        int digitalTextBackColor;

        Bitmap wearIcon;
        String highTemperature;
        String lowTemperature;


        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                invalidate();
            }
        };


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean lowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            /**
             * Create the api client form google.
             */
            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            timeOffset = MyWatchFace.this.getResources().getDimension(R.dimen.time_top_margin);
            dateOffset = MyWatchFace.this.getResources().getDimension(R.dimen.date_top_margin);
            weatherOffset = MyWatchFace.this.getResources().getDimension(R.dimen.weather_top_margin);

            backgroundPaint = new Paint();
            backgroundPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.primary));

            digitalTextColor = ContextCompat.getColor(getBaseContext(), R.color.digital_text_white);
            digitalTextBackColor = ContextCompat.getColor(getBaseContext(), R.color.digital_text_white);


            linePaint = new Paint();
            linePaint.setColor(digitalTextBackColor);

            timeTextPaint = createTextPaint(digitalTextColor);
            dateTextPaint = createTextPaint(digitalTextBackColor);
            highTemperatureTextPaint = createTextPaint(digitalTextColor);
            lowTemperatureTextPaint = createTextPaint(digitalTextBackColor);
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                googleApiClient.connect();
                invalidate();
            } else {
                if (googleApiClient != null && googleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(googleApiClient, this);
                    googleApiClient.disconnect();
                }
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (isRegisteredTimeZoneReceiver) {
                return;
            }
            isRegisteredTimeZoneReceiver = true;
            MyWatchFace.this.registerReceiver(timeZoneReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
        }

        private void unregisterReceiver() {
            if (!isRegisteredTimeZoneReceiver) {
                return;
            }
            isRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(timeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            timeTextPaint.setTextSize(
                    MyWatchFace.this.getResources().getDimension(R.dimen.time_text_size));
            dateTextPaint.setTextSize(
                    MyWatchFace.this.getResources().getDimension(R.dimen.date_text_size));
            highTemperatureTextPaint.setTextSize(
                    MyWatchFace.this.getResources().getDimension(R.dimen.temperature_text_size));
            lowTemperatureTextPaint.setTextSize(
                    MyWatchFace.this.getResources().getDimension(R.dimen.temperature_text_size));


//            boolean isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//            float textSize = resources.getDimension(isRound
//                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (isAmbient != inAmbientMode) {
                linePaint.setColor(inAmbientMode ? digitalTextColor : digitalTextBackColor);
                dateTextPaint.setColor(inAmbientMode ? digitalTextColor : digitalTextBackColor);
                lowTemperatureTextPaint.setColor(inAmbientMode ? digitalTextColor : digitalTextBackColor);
                isAmbient = inAmbientMode;
                if (lowBitAmbient) {
                    timeTextPaint.setAntiAlias(!inAmbientMode);
                    dateTextPaint.setAntiAlias(!inAmbientMode);
                    highTemperatureTextPaint.setAntiAlias(!inAmbientMode);
                    lowTemperatureTextPaint.setAntiAlias(!inAmbientMode);
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
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
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
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);
            }

            String time = isRegisteredTimeZoneReceiver
                    ? Utils.getStringPatternFromDateTime(getString(R.string.hh_mm_formatter), DateTime.now())
                    : Utils.getStringPatternFromDateTime(getString(R.string.hh_mm_ss_formatter), DateTime.now());

            // Draw time text in x-center of screen
            float timeTextWidth = timeTextPaint.measureText(time);
            float halfTimeTextWidth = timeTextWidth / 2;
            float xOffsetTime = bounds.centerX() - halfTimeTextWidth;
            canvas.drawText(time, xOffsetTime, timeOffset, timeTextPaint);

            String date = Utils.getStringPatternFromDateTime(getString(R.string.date_with_day_formatter), DateTime.now())
                    .toUpperCase(Locale.US);

            canvas.drawText(date, bounds.centerX() - dateTextPaint.measureText(date) / 2
                    , dateOffset, dateTextPaint);

            // Draw high and low temperature, icon for weather condition
            if (wearIcon != null && highTemperature != null && lowTemperature != null) {
                float highTemperatureTextWidth = highTemperatureTextPaint.measureText(highTemperature);
                float lowTemperatureTextWidth = lowTemperatureTextPaint.measureText(lowTemperature);

                Rect temperatureBounds = new Rect();
                highTemperatureTextPaint.getTextBounds(highTemperature, 0, highTemperature.length(), temperatureBounds);

                canvas.drawLine(
                        bounds.centerX() - 4 * SPACE_BETWEEN_TEMPERATURES,
                        (dateOffset + weatherOffset) / 2 - (temperatureBounds.height() / 2),
                        bounds.centerX() + 4 * SPACE_BETWEEN_TEMPERATURES,
                        (dateOffset + weatherOffset) / 2 - (temperatureBounds.height() / 2),
                        linePaint);

                float xOffsetHighTemperature;
                if (isAmbient) {
                    xOffsetHighTemperature = bounds.centerX() - ((highTemperatureTextWidth + lowTemperatureTextWidth + SPACE_BETWEEN_TEMPERATURES) / 2);
                } else {
                    xOffsetHighTemperature = bounds.centerX() - (highTemperatureTextWidth / 2);
                    canvas.drawBitmap(wearIcon, xOffsetHighTemperature - wearIcon.getWidth() - 2 * SPACE_BETWEEN_TEMPERATURES,
                            weatherOffset - (temperatureBounds.height() / 2) - (wearIcon.getHeight() / 2), null);
                }

                canvas.drawText(highTemperature,
                        xOffsetHighTemperature,
                        weatherOffset,
                        highTemperatureTextPaint);
                canvas.drawText(lowTemperature,
                        xOffsetHighTemperature + highTemperatureTextWidth + SPACE_BETWEEN_TEMPERATURES,
                        weatherOffset,
                        lowTemperatureTextPaint);
            }
        }

        /**
         * Google api methods
         */
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(googleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            if (connectionResult.getErrorCode() == 2) {
                Toast.makeText(getBaseContext(), R.string.update_google_play, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(WEATHER_PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        setWeatherData(dataMap.getString(HIGH_TEMPERATURE),
                                dataMap.getString(LOW_TEMPERATURE), dataMap.getInt(WEATHER_CONDITION));
                        invalidate();
                    }
                }
            }
        }

        private void setWeatherData(String highTemperature, String lowTemperature, int weatherCondition) {
            this.highTemperature = highTemperature;
            this.lowTemperature = lowTemperature;
            this.wearIcon = BitmapFactory.decodeResource(MyWatchFace.this.getResources(), Utils.getIconResourceForWeatherCondition(weatherCondition));
        }


        /**
         * Starts the {@link #updateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer should
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
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

    }
}

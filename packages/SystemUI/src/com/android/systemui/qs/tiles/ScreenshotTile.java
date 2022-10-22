/*
 * Copyright (C) 2017 ABC rom
 * Copyright (C) 2020 The OmniROM Project
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.view.View;
import android.view.WindowManager;
import static android.view.WindowManager.TAKE_SCREENSHOT_FULLSCREEN;
import static android.view.WindowManager.TAKE_SCREENSHOT_SELECTED_REGION;

import androidx.annotation.Nullable;

import com.android.internal.util.ScreenshotHelper;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.internal.R;

import javax.inject.Inject;

/** Quick settings tile: Screenshot **/
public class ScreenshotTile extends QSTileImpl<BooleanState> {

    @Inject
    public ScreenshotTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.OCTAVI;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void handleClick(@Nullable View view) {
        mHost.collapsePanels();
        final ScreenshotHelper screenshotHelper = new ScreenshotHelper(mContext);
        mHandler.postDelayed(() -> {
            screenshotHelper.takeScreenshot(TAKE_SCREENSHOT_FULLSCREEN,
                    WindowManager.ScreenshotSource.SCREENSHOT_OTHER, mHandler, null);
        }, 1000);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public void handleLongClick(@Nullable View view) {
        mHost.collapsePanels();
        final ScreenshotHelper screenshotHelper = new ScreenshotHelper(mContext);
        mHandler.postDelayed(() -> {
            screenshotHelper.takeScreenshot(TAKE_SCREENSHOT_SELECTED_REGION,
                    WindowManager.ScreenshotSource.SCREENSHOT_OTHER, mHandler, null);
        }, 1000);
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.global_action_screenshot);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(
                R.string.global_action_screenshot);
        state.contentDescription =  mContext.getString(
                R.string.global_action_screenshot);
        state.secondaryLabel = mContext.getString(
                com.android.systemui.R.string.screenshot_long_press);
        state.icon = ResourceIcon.get(R.drawable.ic_screenshot);
        state.value = true;
        state.state = Tile.STATE_INACTIVE;
    }
}

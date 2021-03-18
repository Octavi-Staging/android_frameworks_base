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

package com.android.internal.os;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.NetworkStats;
import android.os.BatteryStats;
import android.os.BatteryUsageStats;
import android.os.BatteryUsageStatsQuery;
import android.os.SystemBatteryConsumer;
import android.os.UidBatteryConsumer;
import android.os.UserBatteryConsumer;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;

import com.android.internal.power.MeasuredEnergyStats;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.stubbing.Answer;

import java.util.Arrays;

public class BatteryUsageStatsRule implements TestRule {
    private final PowerProfile mPowerProfile;
    private final MockClocks mMockClocks = new MockClocks();
    private final MockBatteryStatsImpl mBatteryStats = new MockBatteryStatsImpl(mMockClocks) {
        @Override
        public boolean hasBluetoothActivityReporting() {
            return true;
        }
    };

    private BatteryUsageStats mBatteryUsageStats;
    private boolean mScreenOn;

    public BatteryUsageStatsRule() {
        Context context = InstrumentationRegistry.getContext();
        mPowerProfile = spy(new PowerProfile(context, true /* forTest */));
        mBatteryStats.setPowerProfile(mPowerProfile);
    }

    public BatteryUsageStatsRule setAveragePower(String key, double value) {
        when(mPowerProfile.getAveragePower(key)).thenReturn(value);
        when(mPowerProfile.getAveragePowerOrDefault(eq(key), anyDouble())).thenReturn(value);
        return this;
    }

    public BatteryUsageStatsRule setAveragePowerUnspecified(String key) {
        when(mPowerProfile.getAveragePower(key)).thenReturn(0.0);
        when(mPowerProfile.getAveragePowerOrDefault(eq(key), anyDouble()))
                .thenAnswer((Answer<Double>) invocation -> (Double) invocation.getArguments()[1]);
        return this;
    }

    public BatteryUsageStatsRule setAveragePower(String key, double[] values) {
        when(mPowerProfile.getNumElements(key)).thenReturn(values.length);
        for (int i = 0; i < values.length; i++) {
            when(mPowerProfile.getAveragePower(key, i)).thenReturn(values[i]);
        }
        return this;
    }

    public BatteryUsageStatsRule setNumCpuClusters(int number) {
        when(mPowerProfile.getNumCpuClusters()).thenReturn(number);
        return this;
    }

    public BatteryUsageStatsRule setNumSpeedStepsInCpuCluster(int cluster, int speeds) {
        when(mPowerProfile.getNumSpeedStepsInCpuCluster(cluster)).thenReturn(speeds);
        return this;
    }

    public BatteryUsageStatsRule setAveragePowerForCpuCluster(int cluster, double value) {
        when(mPowerProfile.getAveragePowerForCpuCluster(cluster)).thenReturn(value);
        return this;
    }

    public BatteryUsageStatsRule setAveragePowerForCpuCore(int cluster, int step, double value) {
        when(mPowerProfile.getAveragePowerForCpuCore(cluster, step)).thenReturn(value);
        return this;
    }

    /** Call only after setting the power profile information. */
    public BatteryUsageStatsRule initMeasuredEnergyStatsLocked(int numCustom) {
        final boolean[] supportedStandardBuckets =
                new boolean[MeasuredEnergyStats.NUMBER_STANDARD_POWER_BUCKETS];
        Arrays.fill(supportedStandardBuckets, true);
        mBatteryStats.initMeasuredEnergyStatsLocked(supportedStandardBuckets, numCustom);
        mBatteryStats.informThatAllExternalStatsAreFlushed();
        return this;
    }

    public BatteryUsageStatsRule startWithScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        return this;
    }

    public void setNetworkStats(NetworkStats networkStats) {
        mBatteryStats.setNetworkStats(networkStats);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                noteOnBattery();
                base.evaluate();
            }
        };
    }

    private void noteOnBattery() {
        mBatteryStats.setOnBatteryInternal(true);
        mBatteryStats.getOnBatteryTimeBase().setRunning(true, 0, 0);
        mBatteryStats.getOnBatteryScreenOffTimeBase().setRunning(!mScreenOn, 0, 0);
    }

    public PowerProfile getPowerProfile() {
        return mPowerProfile;
    }

    public MockBatteryStatsImpl getBatteryStats() {
        return mBatteryStats;
    }

    public BatteryStatsImpl.Uid getUidStats(int uid) {
        return mBatteryStats.getUidStatsLocked(uid);
    }

    public void setTime(long realtimeMs, long uptimeMs) {
        mMockClocks.realtime = realtimeMs;
        mMockClocks.uptime = uptimeMs;
    }

    BatteryUsageStats apply(PowerCalculator... calculators) {
        return apply(BatteryUsageStatsQuery.DEFAULT, calculators);
    }

    BatteryUsageStats apply(BatteryUsageStatsQuery query, PowerCalculator... calculators) {
        final long[] customMeasuredEnergiesMicroJoules =
                mBatteryStats.getCustomConsumerMeasuredBatteryConsumptionUC();
        final int customMeasuredEnergiesCount = customMeasuredEnergiesMicroJoules != null
                ? customMeasuredEnergiesMicroJoules.length
                : 0;
        BatteryUsageStats.Builder builder = new BatteryUsageStats.Builder(
                customMeasuredEnergiesCount, 0);
        SparseArray<? extends BatteryStats.Uid> uidStats = mBatteryStats.getUidStats();
        for (int i = 0; i < uidStats.size(); i++) {
            builder.getOrCreateUidBatteryConsumerBuilder(uidStats.valueAt(i));
        }

        for (PowerCalculator calculator : calculators) {
            calculator.calculate(builder, mBatteryStats, mMockClocks.realtime, mMockClocks.uptime,
                    query);
        }

        mBatteryUsageStats = builder.build();
        return mBatteryUsageStats;
    }

    public UidBatteryConsumer getUidBatteryConsumer(int uid) {
        for (UidBatteryConsumer ubc : mBatteryUsageStats.getUidBatteryConsumers()) {
            if (ubc.getUid() == uid) {
                return ubc;
            }
        }
        return null;
    }

    public SystemBatteryConsumer getSystemBatteryConsumer(
            @SystemBatteryConsumer.DrainType int drainType) {
        for (SystemBatteryConsumer sbc : mBatteryUsageStats.getSystemBatteryConsumers()) {
            if (sbc.getDrainType() == drainType) {
                return sbc;
            }
        }
        return null;
    }

    public UserBatteryConsumer getUserBatteryConsumer(int userId) {
        for (UserBatteryConsumer ubc : mBatteryUsageStats.getUserBatteryConsumers()) {
            if (ubc.getUserId() == userId) {
                return ubc;
            }
        }
        return null;
    }
}

/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.flags

import android.test.suitebuilder.annotation.SmallTest
import android.testing.AndroidTestingRunner
import com.android.systemui.SysuiTestCase
import com.android.systemui.util.DeviceConfigProxyFake
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidTestingRunner::class)
class ServerFlagReaderImplTest : SysuiTestCase() {

    private val NAMESPACE = "test"

    @Mock private lateinit var changeListener: ServerFlagReader.ChangeListener

    private lateinit var serverFlagReader: ServerFlagReaderImpl
    private val deviceConfig = DeviceConfigProxyFake()
    private val executor = FakeExecutor(FakeSystemClock())

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        serverFlagReader = ServerFlagReaderImpl(NAMESPACE, deviceConfig, executor)
    }

    @Test
    fun testChange_alertsListener() {
        val flag = ReleasedFlag(1)
        serverFlagReader.listenForChanges(listOf(flag), changeListener)

        deviceConfig.setProperty(NAMESPACE, "flag_override_1", "1", false)
        executor.runAllReady()

        verify(changeListener).onChange()
    }
}

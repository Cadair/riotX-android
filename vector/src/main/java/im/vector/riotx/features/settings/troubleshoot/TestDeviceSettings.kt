/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.riotx.features.settings.troubleshoot

import androidx.appcompat.app.AppCompatActivity
import im.vector.riotx.R
import im.vector.riotx.core.resources.StringProvider
import im.vector.riotx.features.settings.VectorPreferences
import javax.inject.Inject

/**
 * Checks if notifications are enable in the system settings for this app.
 */
class TestDeviceSettings @Inject constructor(private val context: AppCompatActivity,
                                             private val stringProvider: StringProvider)
    : TroubleshootTest(R.string.settings_troubleshoot_test_device_settings_title) {

    override fun perform() {

        if (VectorPreferences.areNotificationEnabledForDevice(context)) {
            description = stringProvider.getString(R.string.settings_troubleshoot_test_device_settings_success)
            quickFix = null
            status = TestStatus.SUCCESS
        } else {
            quickFix = object : TroubleshootQuickFix(R.string.settings_troubleshoot_test_device_settings_quickfix) {
                override fun doFix() {
                    VectorPreferences.setNotificationEnabledForDevice(context, true)
                    manager?.retry()
                }
            }
            description = stringProvider.getString(R.string.settings_troubleshoot_test_device_settings_failed)
            status = TestStatus.FAILED
        }
    }
}
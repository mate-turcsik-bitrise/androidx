/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.integration.avsync

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.integration.avsync.model.CameraHelper.Companion.CameraImplementation
import androidx.camera.lifecycle.ExperimentalCameraProviderConfiguration
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.core.util.Preconditions

private const val KEY_BEEP_FREQUENCY = "beep_frequency"
private const val KEY_BEEP_ENABLED = "beep_enabled"
private const val KEY_CAMERA_IMPLEMENTATION = "camera_implementation"
private const val DEFAULT_BEEP_FREQUENCY = 1500
private const val DEFAULT_BEEP_ENABLED = true
private const val CAMERA_IMPLEMENTATION_CAMERA2 = "camera2"
private const val CAMERA_IMPLEMENTATION_CAMERA_PIPE = "camera_pipe"
private const val MIN_SCREEN_BRIGHTNESS = 0F
private const val MAX_SCREEN_BRIGHTNESS = 1F
private const val DEFAULT_SCREEN_BRIGHTNESS = 0.8F
private val DEFAULT_CAMERA_IMPLEMENTATION = CameraImplementation.CAMERA2

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleScreenLock()
        setScreenBrightness()

        // Config CameraX.
        val cameraImplementation = getCameraImplementation()
        if (!isCameraXConfigured) {
            isCameraXConfigured = true
            configureCameraProvider(cameraImplementation)
        }

        setContent { App(getBeepFrequency(), getBeepEnabled(), cameraImplementation) }
    }

    private fun handleScreenLock() {
        if (Build.VERSION.SDK_INT >= 27) {
            Api27Impl.setShowWhenLocked(this, true)
            Api27Impl.setTurnScreenOn(this, true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun getBeepFrequency(): Int {
        val frequency = intent.getStringExtra(KEY_BEEP_FREQUENCY)

        if (frequency != null) {
            try {
                return Integer.parseInt(frequency)
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        }

        return DEFAULT_BEEP_FREQUENCY
    }

    private fun getBeepEnabled(): Boolean {
        return intent.getBooleanExtra(KEY_BEEP_ENABLED, DEFAULT_BEEP_ENABLED)
    }

    private fun getCameraImplementation(): CameraImplementation {
        val implementation = intent.getStringExtra(KEY_CAMERA_IMPLEMENTATION)
        return if (CAMERA_IMPLEMENTATION_CAMERA2.equals(implementation, true)) {
            CameraImplementation.CAMERA2
        } else if (CAMERA_IMPLEMENTATION_CAMERA_PIPE.equals(implementation, true)) {
            CameraImplementation.CAMERA_PIPE
        } else {
            DEFAULT_CAMERA_IMPLEMENTATION
        }
    }

    @SuppressLint("NullAnnotationGroup")
    @OptIn(ExperimentalCameraProviderConfiguration::class)
    private fun configureCameraProvider(cameraImplementation: CameraImplementation) {
        val config =
            when (cameraImplementation) {
                CameraImplementation.CAMERA2 -> Camera2Config.defaultConfig()
                CameraImplementation.CAMERA_PIPE -> CameraPipeConfig.defaultConfig()
            }
        ProcessCameraProvider.configureInstance(config)
    }

    private fun setScreenBrightness(brightness: Float = DEFAULT_SCREEN_BRIGHTNESS) {
        Preconditions.checkArgument(brightness in MIN_SCREEN_BRIGHTNESS..MAX_SCREEN_BRIGHTNESS)

        val layoutParam = window.attributes
        layoutParam.screenBrightness = brightness
        window.attributes = layoutParam
    }

    @RequiresApi(27)
    private object Api27Impl {
        fun setShowWhenLocked(activity: ComponentActivity, showWhenLocked: Boolean) =
            activity.setShowWhenLocked(showWhenLocked)

        fun setTurnScreenOn(activity: ComponentActivity, turnScreenOn: Boolean) =
            activity.setTurnScreenOn(turnScreenOn)
    }

    companion object {
        private var isCameraXConfigured: Boolean = false
    }
}

@Composable
fun App(beepFrequency: Int, beepEnabled: Boolean, cameraImplementation: CameraImplementation) {
    MaterialTheme { SignalGeneratorScreen(beepFrequency, beepEnabled, cameraImplementation) }
}

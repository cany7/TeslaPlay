package io.github.cany7.teslaplay

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.test.*
import io.github.cany7.teslaplay.airplay.AirPlayConfig
import io.github.cany7.teslaplay.airplay.AirPlayInfoPlist
import io.github.cany7.teslaplay.orchestration.CarPlayRuntimeConfig
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OemSettingsTest : ComposeSettingsTest() {
    private val context
        get() = RuntimeEnvironment.getApplication()

    private fun views(view: View): List<View> =
        listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
            else emptyList()

    private fun button(activity: Activity, title: String) =
        views(activity.window.decorView).filterIsInstance<Button>().single {
            it.text.toString() == title
        }

    private fun openSettings(activity: TeslaPlayActivity) {
        SettingsNavigation.open(activity, "定位设置", compose)
    }

    private fun png(width: Int = 64, height: Int = 64): ByteArray {
        val bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }

    @Test
    fun oldManufacturerOverrideIsPreservedButNeverAdvertised() {
        val preferences = context.getSharedPreferences("teslaplay_airplay", 0)
        preferences.edit().putString("oem_label", "BYD").commit()
        assertEquals("Tesla", AirPlayPersistence.loadSimulatedManufacturerName(context))
        AirPlayPersistence.saveSimulatedManufacturerName(context, "Custom car")
        assertEquals("Tesla", AirPlayPersistence.loadSimulatedManufacturerName(context))
    }

    @Test
    fun phoneConfigurationUsesTeslaAndPackagedIconDespiteOldOverrides() {
        val original = png()
        context.getSharedPreferences("teslaplay_airplay", 0).edit().putInt("display_scale_tenths", 6).commit()
        AirPlayPersistence.saveManualHotspotSsid(context, "TeslaPlay test hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "testpass123")
        AirPlayPersistence.saveSimulatedManufacturerName(context, "比亚迪")
        AirPlayPersistence.saveCustomAirPlayIcon(context, original)
        // Build the real phone configuration without starting Bluetooth/USB or a session.
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val type = CarPlayHostActivity::class.java
        type.getDeclaredMethod("loadPersistedSettings").apply { isAccessible = true }.invoke(host)
        type
            .getDeclaredField("airPlayIdentity")
            .apply { isAccessible = true }
            .set(host, AirPlayPersistence.loadIdentity(context))
        val sizeType = type.declaredClasses.single { it.simpleName == "DisplaySize" }
        val size =
            sizeType
                .getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .newInstance(1280, 720)
        val config =
            type
                .getDeclaredMethod("createAirPlayConfig", sizeType)
                .apply { isAccessible = true }
                .invoke(host, size) as AirPlayConfig
        assertEquals(1280, config.main.widthPixels)
        assertEquals(720, config.main.heightPixels)
        val runtime =
            type.getDeclaredMethod("createRuntimeConfig").apply { isAccessible = true }.invoke(host)
                as CarPlayRuntimeConfig
        assertEquals("Tesla", runtime.identification.manufacturer)
        val info = AirPlayInfoPlist.build(config)
        assertEquals("Tesla", info["oemIconLabel"])
        val icon = (info["oemIcons"] as List<*>).single() as Map<*, *>
        assertEquals(512, icon["widthPixels"])
        assertEquals(512, icon["heightPixels"])
        assertFalse(original.contentEquals(icon["imageData"] as ByteArray))
    }

    @Test
    fun vehiclePageKeepsLocationAndRemovesBrandEditors() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            openSettings(controller.get())
            compose.onNodeWithText("OEM 车厂名称与图标").assertDoesNotExist()
            compose.onNodeWithTag("toggle:NAVIGATION").assertDoesNotExist()
            compose.onNodeWithTag("choice:LOCATION").assertExists()
            compose.onNodeWithTag("text:OEM_NAME").assertDoesNotExist()
            compose.onNodeWithTag("button:settings_select_icon").assertDoesNotExist()
            compose.onNodeWithTag("button:settings_reset_branding").assertDoesNotExist()
        } finally { controller.pause().stop().destroy() }
    }
}

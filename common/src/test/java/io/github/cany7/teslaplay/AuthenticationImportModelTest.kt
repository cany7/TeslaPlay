package io.github.cany7.teslaplay

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.*
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class AuthenticationImportModelTest : ComposeSettingsTest() {
    private val app
        get() = RuntimeEnvironment.getApplication()

    private val store
        get() = MfiAssetStore(app.noBackupFilesDir)

    private val keyUri = Uri.parse("content://documents/key")
    private val certificateUri = Uri.parse("content://documents/certificate")
    private val apkUri = Uri.parse("content://documents/DiPlay.apk")

    @After
    fun cleanup() {
        io.github.cany7.teslaplay.web.WebSession.stop()
        CarPlayBackgroundSession.clear()
        assertFalse(TeslaPlayBootstrap.importing)
    }

    private fun waitForCompletion(model: AuthenticationImportModel) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (model.state.value?.busy == false && !TeslaPlayBootstrap.importing) return
            Thread.sleep(5)
        } while (System.nanoTime() < deadline)
        fail("Import did not complete")
    }

    @Test
    fun manualImportEnablesBootstrapWithoutBundledAssets() {
        assertThrows(Exception::class.java) { TeslaPlayBootstrap.ensure(app) }
        val identity = SyntheticMfiIdentity.create()
        shadowOf(app.contentResolver).registerInputStreamSupplier(keyUri) {
            identity.key.inputStream()
        }
        shadowOf(app.contentResolver).registerInputStreamSupplier(certificateUri) {
            identity.certificate.inputStream()
        }
        val model = AuthenticationImportModel(app)
        model.import(key = keyUri, certificate = certificateUri)
        waitForCompletion(model)
        assertTrue(model.state.value!!.result!!.startsWith("认证资源已导入"))
        TeslaPlayBootstrap.ensure(app)
        assertArrayEquals(identity.certificate, store.load().readCertificate())
    }

    @Test
    fun failedImportLeavesReadyConfigurationAvailable() {
        val current = SyntheticMfiIdentity.create()
        store.installFiles({ current.key.inputStream() }, { current.certificate.inputStream() })
        shadowOf(app.contentResolver).registerInputStreamSupplier(apkUri) {
            "invalid APK".byteInputStream()
        }
        val model = AuthenticationImportModel(app)
        model.import(apk = apkUri)
        waitForCompletion(model)
        assertTrue(model.state.value!!.result!!.startsWith("导入失败"))
        TeslaPlayBootstrap.ensure(app)
        assertArrayEquals(current.certificate, store.load().readCertificate())
    }

    @Test
    fun externalApkShareDoesNotOfferOrStartAuthenticationImport() {
        val controller =
            Robolectric.buildActivity(
                    TeslaPlayActivity::class.java,
                    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, apkUri),
                )
                .setup()
        compose.onNodeWithText("从此 APK 导入认证资源？").assertDoesNotExist()
        assertFalse(TeslaPlayBootstrap.importing)
        assertFalse(store.exists())
        SettingsNavigation.open(controller.get(), "帮助与关于", compose)
        compose.onNodeWithTag("settings:licenses").assertExists()
        compose.onNodeWithText("从 DiPlay APK 导入").assertDoesNotExist()
        assertFalse(TeslaPlayBootstrap.importing)
        assertFalse(store.exists())
        controller.pause().stop().destroy()
    }
}

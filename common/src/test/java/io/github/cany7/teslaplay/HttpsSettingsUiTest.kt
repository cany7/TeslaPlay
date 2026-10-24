package io.github.cany7.teslaplay

import android.content.Intent
import androidx.compose.ui.test.*
import io.github.cany7.teslaplay.network.TeslaHttpMode
import io.github.cany7.teslaplay.network.TeslaHttpConfig
import io.github.cany7.teslaplay.web.LanTls
import io.github.cany7.teslaplay.web.AcmeClient
import io.github.cany7.teslaplay.web.RootAccess
import io.github.cany7.teslaplay.web.TeslaHttpCompatibility
import io.github.cany7.teslaplay.web.WebListenSettings
import io.github.cany7.teslaplay.web.WebSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Before
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
class HttpsSettingsUiTest : ComposeSettingsTest() {
    @Before
    fun startWithoutRootAuthorization() {
        val original = RootAccess.authorize
        try {
            RootAccess.authorize = { throw java.io.IOException("Permission denied") }
            RootAccess.request()
        } finally {
            RootAccess.authorize = original
        }
    }

    private fun openSettings(activity: TeslaPlayActivity) =
        SettingsNavigation.open(activity, "Tesla 网络与 HTTPS", compose)

    private fun requestRoot() {
        compose.onNodeWithText("请求 Root 权限").performScrollTo().performClick()
    }

    private fun hasTag(tag: String) =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitForUi(predicate: () -> Boolean) {
        compose.waitUntil(8_000) {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            predicate() && !RootAccess.checking
        }
    }

    @Test
    fun startupChecksRootInBackgroundAndShowsSettingsWithoutPressingTheButton() {
        val original = RootAccess.authorize
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        RootAccess.startupChecked = false
        RootAccess.authorize = {
            requests.incrementAndGet()
            entered.countDown()
            check(release.await(8, TimeUnit.SECONDS))
        }
        TeslaHttpCompatibility.save(
            RuntimeEnvironment.getApplication(),
            TeslaHttpConfig(mode = TeslaHttpMode.ROOT),
        )
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            openSettings(controller.get())
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            compose.onNodeWithText("请求 Root 权限").assertIsNotEnabled()
            assertFalse(hasTag("text:HTTP_MAPPING"))
            assertFalse(hasTag("text:HTTP_PORT"))
            release.countDown()
            waitForUi { RootAccess.granted && hasTag("text:HTTP_MAPPING") }
            RootAccess.checkOnStartup()
            assertEquals(1, requests.get())
            requestRoot()
            waitForUi { requests.get() == 2 }
        } finally {
            release.countDown()
            RootAccess.authorize = original
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test
    fun deniedStartupCheckDoesNotRepeatUntilManualRetry() {
        val original = RootAccess.authorize
        val requests = AtomicInteger()
        RootAccess.startupChecked = false
        RootAccess.authorize = {
            requests.incrementAndGet()
            throw java.io.IOException("Permission denied")
        }
        TeslaHttpCompatibility.save(
            RuntimeEnvironment.getApplication(),
            TeslaHttpConfig(mode = TeslaHttpMode.ROOT),
        )
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            openSettings(controller.get())
            waitForUi { requests.get() == 1 }
            RootAccess.checkOnStartup()
            assertEquals(1, requests.get())
            assertFalse(RootAccess.granted)
            assertFalse(hasTag("text:HTTP_MAPPING"))
            requestRoot()
            waitForUi { requests.get() == 2 }
        } finally {
            RootAccess.authorize = original
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test
    fun deniedRootKeepsMappingControlsHiddenAndUnifiedWebSettingsAvailable() {
        val original = RootAccess.authorize
        RootAccess.authorize = { throw java.io.IOException("Permission denied") }
        TeslaHttpCompatibility.save(RuntimeEnvironment.getApplication(), TeslaHttpConfig(mode = TeslaHttpMode.ROOT))
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            openSettings(controller.get())
            requestRoot()
            waitForUi { !RootAccess.checking }
            assertFalse(hasTag("text:HTTP_MAPPING"))
            assertFalse(hasTag("text:TESLA_ADDRESS"))
            compose.onNodeWithTag("text:HOSTNAME").assertExists()
            compose.onNodeWithTag("text:MDNS_HOSTNAME").assertDoesNotExist()
            compose.onNodeWithTag("toggle:TESLA").assertDoesNotExist()
            compose.onNodeWithTag("text:HTTP_PORT").assertDoesNotExist()
            compose.onNodeWithText("高级：HTTP／HTTPS 端口").performScrollTo().performClick()
            compose.onNodeWithTag("text:HTTP_PORT").assertExists()
            compose.onNodeWithTag("text:HTTPS_PORT").assertExists()
        } finally {
            RootAccess.authorize = original
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test
    fun certificateTypesOpenTheSystemDocumentPickerWithoutRoot() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            val activity = controller.get()
            SettingsNavigation.open(activity, "Tesla 网络与 HTTPS", compose)
            for ((type, title) in listOf("PEM" to "选择 PEM 证书与私钥", "PKCS12" to "选择 PKCS#12 文件")) {
                clickTag("certificate:$type")
                compose.onNodeWithText(title).performScrollTo().performClick()
                var intent = shadowOf(activity).nextStartedActivity
                while (intent != null && intent.action != Intent.ACTION_OPEN_DOCUMENT)
                    intent = shadowOf(activity).nextStartedActivity
                assertNotNull(intent)
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
                assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
                assertEquals("*/*", intent.type)
                assertFalse(RootAccess.granted)
                assertEquals(WebListenSettings.CertificateType.DEFAULT, WebListenSettings.certificateType(activity))
                shadowOf(activity).receiveResult(intent, android.app.Activity.RESULT_CANCELED, null)
                compose.waitForIdle()
                assertEquals(WebListenSettings.CertificateType.DEFAULT, WebListenSettings.certificateType(activity))
            }
        } finally {
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test
    fun httpsSwitchKeepsCertificateSetupAvailableAndPortsStayAdvanced() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            val activity = controller.get()
            openSettings(activity)
            compose.onNodeWithText("请求 Root 权限").assertDoesNotExist()
            clickTag("toggle:HTTPS")
            waitForUi { !WebListenSettings.httpsEnabled(activity) }
            compose.onNodeWithTag("toggle:HTTPS").assertIsOff()
            compose.onNodeWithTag("text:HTTPS_PORT").assertDoesNotExist()
            compose.onNodeWithTag("certificate:DUCKDNS").assertExists()
            compose.onNodeWithTag("text:HTTP_PORT").assertDoesNotExist()
            clickTag("certificate:DUCKDNS")
            compose.onNodeWithTag("text:HOSTNAME").assertDoesNotExist()
            compose.onNodeWithText("DuckDNS 域名").assertExists()
            assertEquals(WebListenSettings.CertificateType.DEFAULT, WebListenSettings.certificateType(activity))
        } finally {
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test
    fun choosingLocalCertificateIsOnlyADraftUntilExplicitApply() {
        val context = RuntimeEnvironment.getApplication()
        val keys = AcmeClient.newRsaKey()
        val name = org.bouncycastle.asn1.x500.X500Name("CN=car.example.com")
        val now = System.currentTimeMillis()
        val builder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(name,
            java.math.BigInteger.ONE, java.util.Date(now - 60_000), java.util.Date(now + 86_400_000), name, keys.public)
        val certificate = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .setProvider(org.bouncycastle.jce.provider.BouncyCastleProvider()).getCertificate(builder.build(
                org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(keys.private)))
        LanTls.preparePem(AcmeClient.pem("CERTIFICATE", certificate.encoded),
            AcmeClient.pem("PRIVATE KEY", keys.private.encoded)).use { prepared ->
            assertNull(WebSession.installCertificate(context, prepared, type = WebListenSettings.CertificateType.PKCS12))
        }
        val fingerprint = LanTls.installedCertificate(context)!!.fingerprint
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            openSettings(controller.get())
            clickTag("certificate:DEFAULT")
            compose.waitForIdle()
            compose.onNodeWithTag("text:HOSTNAME").assertDoesNotExist()
            assertEquals(WebListenSettings.CertificateType.PKCS12, WebListenSettings.certificateType(context))
            assertEquals(fingerprint, LanTls.installedCertificate(context)!!.fingerprint)
            clickTag("settings:back")
            openSettings(controller.get())
            assertEquals(WebListenSettings.CertificateType.PKCS12, WebListenSettings.certificateType(context))
            clickTag("certificate:DEFAULT")
            compose.onNodeWithText("改用本地证书").performScrollTo().performClick()
            waitForUi { WebListenSettings.certificateType(context) == WebListenSettings.CertificateType.DEFAULT }
            assertFalse(LanTls.hasCustom(context))
        } finally {
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

    @Test
    fun httpsPortFieldShowsItsPersistedDefaultAndRejectsInvalidInput() {
        val controller = Robolectric.buildActivity(TeslaPlayActivity::class.java).setup()
        try {
            SettingsNavigation.open(controller.get(), "Tesla 网络与 HTTPS", compose)
            compose.onNodeWithText("高级：HTTP／HTTPS 端口").performScrollTo().performClick()
            clickTag("text:HTTPS_PORT")
            compose.onNodeWithTag("settings:text-input").assertTextContains("8443")
            input("443")
            saveInput()
            assertEquals(8443, WebListenSettings.httpsPort(controller.get()))
            compose.onNodeWithTag("settings:text-input").assertExists()
            cancelInput()
        } finally {
            controller.pause().stop().destroy()
            WebSession.stop()
        }
    }

}

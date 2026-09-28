package io.github.miuzarte.scrcpyforandroid

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.github.miuzarte.scrcpyforandroid.ui.CoverDisplayContent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastIntents
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastScreen
import androidx.core.content.ContextCompat
import io.github.miuzarte.scrcpyforandroid.i18n.LocalizedActivity
import io.github.miuzarte.scrcpyforandroid.pages.MainScreen
import io.github.miuzarte.scrcpyforandroid.password.BiometricGate
import io.github.miuzarte.scrcpyforandroid.password.PasswordRepository
import io.github.miuzarte.scrcpyforandroid.password.hasAuthenticatedOrigin
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import kotlinx.coroutines.runBlocking

// 生物认证需要 FragmentActivity
class MainActivity: LocalizedActivity() {
    private var autoCastRequested by mutableStateOf(false)
    private var autoCastRequestId by mutableStateOf(0)
    private var autoCastStartApp by mutableStateOf<String?>(null)
    private var externalInnerRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyMainOrientationPolicy()

        // no logEvent before context init
        AppRuntime.init(applicationContext)
        autoCastRequested = savedInstanceState?.getBoolean("autoCast") ?: AutoCastIntents.isAutoCast(intent)
        autoCastStartApp = savedInstanceState?.getString("autoCastStartApp") ?: intent.getStringExtra(AutoCastIntents.EXTRA_START_APP)
        externalInnerRequest = savedInstanceState?.getBoolean("externalInnerRequest") ?: AutoCastIntents.isExternalInnerRequest(intent)
        AppScreenOn.register(window)

        runBlocking {
            PasswordRepository.refresh()
            // 认证不可用时, 清除经认证创建的密码
            if (!BiometricGate.canAuthenticate()) {
                PasswordRepository.getAll()
                    .filter { it.createdWithAuth.hasAuthenticatedOrigin && it.cipherText != null }
                    .forEach { PasswordRepository.markInvalid(it.id) }
            }
        }

        // 请求附近设备/局域网 mDNS 发现所需的运行时权限
        requestNearbyDevicePermissions()

        enableEdgeToEdge()

        setContent {
            CoverDisplayContent {
                if (autoCastRequested) AutoCastScreen(requestId = autoCastRequestId, startApp = autoCastStartApp, externalInnerRequest = externalInnerRequest, onClose = {
                    autoCastRequested = false
                }, onOpenApp = { autoCastRequested = false })
                else MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (AutoCastIntents.isAutoCast(intent)) {
            if (!autoCastRequested || intent.hasExtra(AutoCastIntents.EXTRA_START_APP)) {
                autoCastStartApp = intent.getStringExtra(AutoCastIntents.EXTRA_START_APP)
                externalInnerRequest = AutoCastIntents.isExternalInnerRequest(intent)
            }
            if (AutoCastIntents.isExternalInnerRequest(intent)) externalInnerRequest = true
            autoCastRequested = true
            autoCastRequestId++
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("autoCast", autoCastRequested)
        outState.putString("autoCastStartApp", autoCastStartApp)
        outState.putBoolean("externalInnerRequest", externalInnerRequest)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        applyMainOrientationPolicy()
        StreamActivity.dismissActivePictureInPicture()
    }

    override fun onDestroy() {
        AppScreenOn.unregister(window)
        if (isFinishing && autoCastRequested) AppRuntime.autoCast?.onWindowRemoved()
        // Activity 重建 (配置变更) 不能收尾会话, 只有真正退出才释放
        if (isFinishing) AppRuntime.releaseSession()
        super.onDestroy()
    }

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w(
                    "MainActivity",
                    "本地网络权限被拒绝，局域网设备发现可能不可用",
                )
            }
        }

    /**
     * 请求 Android 17+ 本地网络访问运行时权限
     * 授权后 NsdManager 才能正常扫描局域网 ADB 设备
     */
    private fun requestNearbyDevicePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return

        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return

        localNetworkPermissionLauncher.launch(permission)
    }

    private fun applyMainOrientationPolicy() {
        val aspectRatio = currentDisplayAspectRatio()
        requestedOrientation =
            if (aspectRatio > PHONE_LANDSCAPE_LOCK_ASPECT_RATIO)
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun currentDisplayAspectRatio(): Float {
        val bounds =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                windowManager.maximumWindowMetrics.bounds
            else resources.displayMetrics.let { metrics ->
                Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            }

        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        return maxOf(width, height).toFloat() / minOf(width, height).toFloat()
    }

    private companion object {
        private const val PHONE_LANDSCAPE_LOCK_ASPECT_RATIO = 16f / 9f
    }
}

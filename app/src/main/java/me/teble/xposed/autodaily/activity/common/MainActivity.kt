package me.teble.xposed.autodaily.activity.common

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import me.teble.xposed.autodaily.BuildConfig
import me.teble.xposed.autodaily.IUserService
import me.teble.xposed.autodaily.activity.module.colors
import me.teble.xposed.autodaily.application.xaApp
import me.teble.xposed.autodaily.config.Constants.PACKAGE_NAME_QQ
import me.teble.xposed.autodaily.config.Constants.PACKAGE_NAME_TIM
import me.teble.xposed.autodaily.config.QQClasses.Companion.DataMigrationService
import me.teble.xposed.autodaily.config.QQClasses.Companion.KernelService
import me.teble.xposed.autodaily.hook.CoreServiceHook.Companion.CORE_SERVICE_FLAG
import me.teble.xposed.autodaily.hook.CoreServiceHook.Companion.CORE_SERVICE_TOAST_FLAG
import me.teble.xposed.autodaily.shizuku.ShizukuApi
import me.teble.xposed.autodaily.shizuku.ShizukuConf
import me.teble.xposed.autodaily.shizuku.UserService
import me.teble.xposed.autodaily.ui.ActivityView
import me.teble.xposed.autodaily.ui.LineCheckBox
import me.teble.xposed.autodaily.ui.LineSwitch
import me.teble.xposed.autodaily.utils.toJsonString
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.math.expm1
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    companion object {
        var shizukuErrInfo by mutableStateOf("")
        var shizukuDaemonRunning by mutableStateOf(false)

        const val PEEK_SERVICE = 1
        const val LOOP_PEEK_SERVICE = 2
        private val handlerThread by lazy {
            HandlerThread("CoreServiceHookThread").apply { start() }
        }
        val handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    PEEK_SERVICE -> {
                        peekUserService()
                    }
                }
            }
        }

        private val peekServiceRunnable = object : Runnable {
            override fun run() {
                handler.postDelayed(this, 1000)
                shizukuDaemonRunning = peekUserService()
            }
        }

        private val userServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {

                if (binder != null && binder.pingBinder()) {
                    val service: IUserService = IUserService.Stub.asInterface(binder)
                    try {
                        if (service.isRunning) {
                            shizukuErrInfo = ""
                            shizukuDaemonRunning = true
                        }
                    } catch (e: RemoteException) {
                        Log.e("XALog", Log.getStackTraceString(e))
                        shizukuErrInfo = "守护进程连接失败"
                    }
                } else {
                    shizukuErrInfo = "invalid binder for $name received"
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shizukuErrInfo = ""
                shizukuDaemonRunning = false
            }
        }

        private val userServiceArgs by lazy {
            Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
                .daemon(true)
                .processNameSuffix("service")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
        }

        fun rebindUserService() {
            unbindUserService() && bindUserService()
        }

        fun bindUserService(): Boolean {
            try {
                Shizuku.bindUserService(userServiceArgs, userServiceConnection)
                return true
            } catch (e: Throwable) {
                Log.e("XALog", Log.getStackTraceString(e))
            }
            return false
        }

        fun unbindUserService(): Boolean {
            try {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
                shizukuDaemonRunning = false
                return true
            } catch (e: Throwable) {
                Log.e("XALog", Log.getStackTraceString(e))
            }
            return false
        }

        fun peekUserService(): Boolean {
            runCatching {
                return Shizuku.peekUserService(userServiceArgs, userServiceConnection)
            }.onFailure {
                Log.e("XALog", Log.getStackTraceString(it))
            }
            return false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler.post(peekServiceRunnable)
        setContent {
            MaterialTheme(colors = colors()) {
                ProvideWindowInsets {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        ModuleView()
                    }
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        sqrt(1.0)
        Math.random()
        expm1(0.001)
        return false
    }
}

private val listener: (Int, Int) -> Unit = { _, grantResult ->
    ShizukuApi.isPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
}

@Composable
fun ShizukuCard() {
    LaunchedEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(listener)
    }
    DisposableEffect(Unit) {
        onDispose {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.White, shape = RoundedCornerShape(6.dp))
            .clickable {
                if (ShizukuApi.isBinderAvailable && !ShizukuApi.isPermissionGranted) {
                    Shizuku.requestPermission(1101)
                    return@clickable
                }
                if (!MainActivity.shizukuDaemonRunning) {
                    MainActivity.bindUserService()
                } else {
                    MainActivity.unbindUserService()
                }
            }
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ShizukuApi.isBinderAvailable && !ShizukuApi.isPermissionGranted) {
            Icon(Icons.Outlined.CheckCircle, "Shizuku 服务正在运行")
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "Shizuku 服务正在运行（未授权）",
                    fontFamily = FontFamily.Serif,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Shizuku Api Version: ${Shizuku.getVersion()}")
                Text(text = "点击此卡片进行授权", color = Color.Red)
            }
        } else if (ShizukuApi.isPermissionGranted) {
            Icon(Icons.Outlined.CheckCircle, "Shizuku 服务正在运行")
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "Shizuku 服务正在运行",
                    fontFamily = FontFamily.Serif,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Shizuku Api Version: ${Shizuku.getVersion()}")
                if (!MainActivity.shizukuDaemonRunning) {
                    if (MainActivity.shizukuErrInfo.isNotEmpty()) {
                        Text(text = "守护进程启动失败: ${MainActivity.shizukuErrInfo}", color = Color.Red)
                    } else {
                        Text(text = "守护进程未在运行，点击运行", color = Color.Red)
                    }
                } else {
                    Text(text = "守护进程正在运行，点击停止运行", color = Color.Green)
                }
            }
        } else {
            Icon(Icons.Outlined.Warning, "Shizuku 服务未在运行")
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "Shizuku 服务未在运行",
                    fontFamily = FontFamily.Serif,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "部分功能无法运行")
            }
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun ModuleView() {
    ActivityView(title = "XAutoDaily") {
        var infoList by remember { mutableStateOf(listOf<String>()) }
        LaunchedEffect(ShizukuApi.isPermissionGranted) {
            infoList = if (!ShizukuApi.isPermissionGranted) {
                if (ShizukuApi.isBinderAvailable) {
                    listOf("shizuku正在运行，但是未授权，无法勾选")
                } else {
                    listOf("shizuku没有在运行，无法勾选")
                }
            } else {
                listOf()
            }
        }
        LazyColumn(
            modifier = Modifier
                .padding(top = 13.dp)
                .padding(horizontal = 13.dp),
            contentPadding = rememberInsetsPaddingValues(LocalWindowInsets.current.navigationBars),
            // 绘制间隔
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ShizukuCard()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    val checked = remember { mutableStateOf(xaApp.prefs.getBoolean("UntrustedTouchEvents", false)) }
                    LineSwitch(
                        title = "取消安卓12不受信触摸",
                        desc = "安卓12后启用对toast弹窗等事件触摸不可穿透，勾选此项可关闭",
                        checked = checked,
                        enabled = ShizukuApi.isPermissionGranted,
                        onChange = {
                            ShizukuApi.setUntrustedTouchEvents(it)
                            checked.value = it
                            xaApp.prefs.edit()
                                .putBoolean("UntrustedTouchEvents", it)
                                .apply()
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                        otherInfoList = infoList
                    )
                }
            }
            item {
                val keepAliveChecked = remember {
                    mutableStateOf(xaApp.prefs.getBoolean("KeepAlive", false))
                }
                val qqKeepAlive = remember {
                    mutableStateOf(xaApp.prefs.getBoolean("QKeepAlive", false))
                }
                val timKeepAlive = remember {
                    mutableStateOf(xaApp.prefs.getBoolean("TimKeepAlive", false))
                }
                LaunchedEffect(qqKeepAlive.value, timKeepAlive.value, keepAliveChecked.value) {
                    val confDir = File(xaApp.getExternalFilesDir(null), "conf")
                    if (confDir.isFile) {
                        confDir.delete()
                    }
                    if (!confDir.exists()) {
                        confDir.mkdirs()
                    }
                    val confFile = File(confDir, "conf.json")
                    val conf = ShizukuConf(keepAliveChecked.value,
                        mutableMapOf<String, Boolean>().apply {
                            put(PACKAGE_NAME_QQ, qqKeepAlive.value)
                            put(PACKAGE_NAME_TIM, timKeepAlive.value)
                        })
                    val confString = conf.toJsonString()
                    Log.d("XALog", confString)
                    confFile.writeText(confString)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = Color.White, shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    LineSwitch(
                        checked = keepAliveChecked,
                        title = "是否启用shizuku保活机制",
                        desc = "通过shizuku运行一个service，当监测到qq/tim被杀死后重新拉起进程",
                        enabled = ShizukuApi.isPermissionGranted,
                        onChange = {
                            keepAliveChecked.value = it
                            xaApp.prefs.edit()
                                .putBoolean("KeepAlive", it)
                                .apply()
                        },
                        otherInfoList = infoList
                    )
                    if (keepAliveChecked.value) {
                        if (xaApp.qPackageState.containsKey(PACKAGE_NAME_QQ)) {
                            LineCheckBox(
                                checked = qqKeepAlive,
                                title = "启用qq保活",
                                desc = "单击此处尝试后台唤醒qq，如果模块hook生效将会显示一个气泡",
                                onClick = {
                                    ShizukuApi.startService(PACKAGE_NAME_QQ, DataMigrationService,
                                        arrayOf(
                                            "-e", CORE_SERVICE_FLAG, "$",
                                            "-e", CORE_SERVICE_TOAST_FLAG, "$"
                                        ))
                                },
                                onChange = {
                                    qqKeepAlive.value = it
                                    xaApp.prefs.edit()
                                        .putBoolean("QKeepAlive", it)
                                        .apply()
                                }
                            )
                        }
                        if (xaApp.qPackageState.containsKey(PACKAGE_NAME_TIM)) {
                            LineCheckBox(
                                checked = timKeepAlive,
                                title = "启用tim保活",
                                desc = "单击此处尝试后台唤醒tim，如果模块hook生效将会显示一个气泡",
                                onClick = {
                                    ShizukuApi.startService(PACKAGE_NAME_TIM, KernelService,
                                        arrayOf(
                                            "-e", CORE_SERVICE_FLAG, "$",
                                            "-e", CORE_SERVICE_TOAST_FLAG, "$"
                                        ))
                                },
                                onChange = {
                                    timKeepAlive.value = it
                                    xaApp.prefs.edit()
                                        .putBoolean("TimKeepAlive", it)
                                        .apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
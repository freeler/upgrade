package com.freeler.upgrade.download

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.freeler.upgrade.R
import com.freeler.upgrade.utils.requestPermission
import com.freeler.upgrade.utils.startForResult
import java.io.File


/**
 * 下载的网络请求封装
 *
 * @author: xuzeyang
 * @Date: 2020/4/10
 */
object DownloadManager {

    private var broadcastReceiver: BroadcastReceiver? = null

    /**
     * 此方式注册下载完成后会自动安装apk，安装前会校验设置中开启安装未知应用权限是否开启,
     * 如果开启了直接执行安装步骤，
     * 如未开启则会先跳转到设置中引导用户开启（context不是Activity的话会跳过该步骤）
     */
    fun registerWithAutoInstall(context: Context, progressCallBack: (Long, String) -> Unit) {
        registerReceiver(context, progressCallBack, null, true)
    }

    /**
     * 此方式注册下载需要自己去监听回调方法来执行apk安装
     */
    fun register(
        context: Context,
        progressCallBack: (Long, String) -> Unit,
        installCallBack: (String) -> Unit
    ) {
        registerReceiver(context, progressCallBack, installCallBack, false)
    }

    private fun registerReceiver(
        context: Context,
        progressCallBack: ((Long, String) -> Unit)? = null,
        installCallBack: ((String) -> Unit)? = null,
        autoInstall: Boolean = true
    ) {
        broadcastReceiver = object : BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    DownloadIntentService.DOWNLOAD_ACTION_INSTALL -> {
                        // 文件名
                        val name = intent.getStringExtra(DownloadIntentService.EXTRA_FILENAME) ?: ""
                        if (autoInstall) {
                            installWithPermission(context, name)
                        }
                        installCallBack?.invoke(name)
                    }
                    DownloadIntentService.DOWNLOAD_ACTION_PROGRESS -> {
                        // 下载进度 0-100L
                        val progress = intent.getLongExtra(DownloadIntentService.EXTRA_PROGRESS, 0)
                        // 下载速度
                        val speed = intent.getStringExtra(DownloadIntentService.EXTRA_SPEED) ?: ""
                        progressCallBack?.invoke(progress, speed)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            // 下载Service返回的进度
            this.addAction(DownloadIntentService.DOWNLOAD_ACTION_PROGRESS)
            // 下载Service返回的安装动作
            this.addAction(DownloadIntentService.DOWNLOAD_ACTION_INSTALL)
        }
        context.registerReceiver(broadcastReceiver, filter)
    }


    /**
     * 取消注册
     */
    fun unregister(context: Context) {
        broadcastReceiver?.let { context.unregisterReceiver(it) }
    }

    /**
     * 下载APK
     *
     * @param activity 上下文
     * @param downloadURL 下载链接地址
     * @param downloadMultiple 下载速度 1-10,默认10
     * @param apkName 下载后保存的apk名称
     */
    fun downloadApk(
        activity: Activity,
        downloadURL: String,
        downloadMultiple: Int = 10,
        apkName: String = downloadURL.substringAfterLast("/")
    ) {
        if (isServiceRunning(activity, DownloadIntentService::class.java.name)) {
            return
        }
        activity.requestPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) {
            if (it) {
                val intent = Intent(activity, DownloadIntentService::class.java).apply {
                    this.putExtra(DownloadIntentService.EXTRA_DOWNLOAD_URL, downloadURL)
                    this.putExtra(DownloadIntentService.EXTRA_DOWNLOAD_NAME, apkName)
                    this.putExtra(DownloadIntentService.EXTRA_DOWNLOAD_MULTIPLE, downloadMultiple)
                }
                activity.startService(intent)
            }
        }
    }

    /**
     * 用来判断服务是否运行.
     *
     * @param className 判断的服务名字
     * @return true:在运行 false:不在运行
     */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(activity: Activity, className: String): Boolean {
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceList = activityManager.getRunningServices(Integer.MAX_VALUE)
        if (serviceList.size <= 0) {
            return false
        }
        for (i in serviceList.indices) {
            if (serviceList[i].service.className == className) {
                return true
            }
        }
        return false
    }


    /**
     * 检查权限并安装
     */
    fun installWithPermission(context: Context, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                if (context !is Activity) {
                    installApk(context, fileName)
                    return
                }
                showSettingDialog(context, fileName)
            } else {
                installApk(context, fileName)
            }
        } else {
            installApk(context, fileName)
        }
    }

    /**
     * 文案与微信一致，可替换为自定义dialog
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun showSettingDialog(activity: Activity, fileName: String) {
        AlertDialog.Builder(activity, R.style.MyDialogStyle)
            .setTitle("权限申请")
            .setMessage("在设置中开启安装未知应用权限，以正常使用该功能")
            .setCancelable(false)
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("去设置") { dialog, _ ->
                dialog.dismiss()
                val url = Uri.parse("package:${activity.packageName}")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, url)
                activity.startForResult(intent) { resultCode, _ ->
                    if (resultCode == Activity.RESULT_OK) {
                        if (activity.packageManager.canRequestPackageInstalls()) {
                            installApk(activity, fileName)
                        }
                    }
                }
            }
            .show()
    }

    /**
     * 安装APK
     */
    private fun installApk(context: Context, fileName: String) {
        val filePath = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
        val apk = File(Environment.getExternalStorageDirectory().path, filePath)

        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val authority = "${context.applicationInfo.packageName}.fileProvider"
                FileProvider.getUriForFile(context, authority, apk)
            } else {
                Uri.fromFile(apk)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                this.addCategory(Intent.CATEGORY_DEFAULT)
                this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.setDataAndType(uri, "application/vnd.android.package-archive")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "如安装失败，请前往应用市场下载最新版本，感谢您的支持！", Toast.LENGTH_SHORT).show()
        }
    }

}
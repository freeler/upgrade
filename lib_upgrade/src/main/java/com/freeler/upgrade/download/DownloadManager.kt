package com.freeler.upgrade.download

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
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
                val intent = Intent(activity, DownloadIntentService::class.java)
                val bundle = Bundle()
                bundle.putString("download_url", downloadURL)
                bundle.putString("download_apkName", apkName)
                bundle.putInt("download_multiple", downloadMultiple)
                intent.putExtras(bundle)
                activity.startService(intent)
            }
        }
    }

    /**
     * 用来判断服务是否运行.
     *
     * @param className 判断的服务名字
     * @return true 在运行 false 不在运行
     */
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
    fun checkInstallPermission(context: Activity, fileName: String) {
        val filePath = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
        val apk = File(Environment.getExternalStorageDirectory().path, filePath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                // 文案与微信一致，可替换为自定义dialog
                AlertDialog.Builder(context)
                    .setTitle("权限申请")
                    .setMessage("在设置中开启安装未知应用权限，以正常使用该功能")
                    .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton("去设置") { dialog, _ ->
                        dialog.dismiss()
                        val url = Uri.parse("package:${context.packageName}")
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, url)
                        context.startForResult(intent) { resultCode, _ ->
                            if (resultCode == Activity.RESULT_OK) {
                                if (context.packageManager.canRequestPackageInstalls()) {
                                    installApk(context, apk)
                                }
                            }
                        }
                    }
                    .show()
            } else {
                installApk(context, apk)
            }
        } else {
            installApk(context, apk)
        }
    }


    /**
     * 安装APK
     */
    private fun installApk(context: Context, apk: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.applicationInfo.packageName + ".fileProvider",
                    apk
                )
            } else {
                Uri.fromFile(apk)
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "如安装失败，请前往应用市场下载最新版本，感谢您的支持！", Toast.LENGTH_SHORT).show()
        }
    }

}
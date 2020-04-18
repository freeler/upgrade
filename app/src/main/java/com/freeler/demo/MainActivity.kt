package com.freeler.demo

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.freeler.upgrade.download.DownloadIntentService
import com.freeler.upgrade.download.DownloadManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        register()

        mBtnStartDownload.setOnClickListener {
            // 开启下载，下载会在IntentService中执行，如果当前正在下载中，不会重复执行下载任务
            DownloadManager.downloadApk(
                this, "https://static.yuxiaor.com/Yuxiaor_3.3.4_6350.apk"
            )
        }

    }

    private fun register() {
        val filter = IntentFilter()
        // 下载Service返回的进度
        filter.addAction(DownloadIntentService.DOWNLOAD_ACTION_PROGRESS)
        // 下载Service返回的安装动作
        filter.addAction(DownloadIntentService.DOWNLOAD_ACTION_INSTALL)
        registerReceiver(broadcastReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }


    private val broadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadIntentService.DOWNLOAD_ACTION_INSTALL -> {
                    DownloadManager.checkInstallPermission(
                        this@MainActivity,
                        intent.getStringExtra("fileName") ?: ""
                    )
                }
                DownloadIntentService.DOWNLOAD_ACTION_PROGRESS -> {
                    val progress = intent.getLongExtra("progress", 0)
                    val speed = intent.getStringExtra("speed")
                    mTvProgress.text = "current progress: ${}，current speed${}"
                }
            }
        }
    }

}

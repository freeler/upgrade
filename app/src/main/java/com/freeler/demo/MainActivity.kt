package com.freeler.demo

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.freeler.upgrade.download.DownloadManager
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBtnStartDownload.setOnClickListener {
            // 开启下载，下载会在IntentService中执行，如果当前正在下载中，不会重复执行下载任务
            DownloadManager.downloadApk(
                this, "https://static.yuxiaor.com/Yuxiaor_3.3.4_6350.apk"
            )
        }

        register()
    }

    @SuppressLint("SetTextI18n")
    private fun register() {
        DownloadManager.registerWithAutoInstall(this) { progress, speed ->
            mTvProgress.text = "${progress}%"
            mTvSpeed.text = speed
        }

        // or auto manual install
//        DownloadManager.register(this, { fileName ->
//            // there is a dialog in install method,if we register context is not activity,
//            // wo must manual install by this method.
//            DownloadManager.installWithPermission(this, fileName)
//        }, { progress, speed ->
//            mTvProgress.text = "${progress}%"
//            mTvSpeed.text = speed
//        })
    }


    override fun onDestroy() {
        super.onDestroy()
        DownloadManager.unregister(this)
    }

}

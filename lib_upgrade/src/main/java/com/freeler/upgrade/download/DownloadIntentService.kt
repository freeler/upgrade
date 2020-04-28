package com.freeler.upgrade.download

import android.app.IntentService
import android.content.Intent
import android.os.Environment
import com.freeler.upgrade.utils.DownloadCache
import com.freeler.upgrade.utils.SpeedCalculator
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载Service
 *
 * @author: xuzeyang
 * @Date: 2020/4/9
 */
class DownloadIntentService : IntentService("DownloadIntentService") {

    companion object {
        const val DOWNLOAD_ACTION_PROGRESS = "DOWNLOAD_ACTION_PROGRESS"
        const val DOWNLOAD_ACTION_INSTALL = "DOWNLOAD_ACTION_INSTALL"

        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_DOWNLOAD_NAME = "download_apkName"
        const val EXTRA_DOWNLOAD_MULTIPLE = "download_multiple"

        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_FILENAME = "fileName"
        private const val CONNECT_TIMEOUT = 5000
    }

    /** 计算下载速度辅助工具类*/
    private val speedCalculator by lazy { SpeedCalculator() }


    override fun onHandleIntent(intent: Intent?) {
        val downloadUrl = intent?.getStringExtra(EXTRA_DOWNLOAD_URL) ?: ""
        val downloadApkName = intent?.getStringExtra(EXTRA_DOWNLOAD_NAME) ?: ""
        val downloadMultiple = intent?.getIntExtra(EXTRA_DOWNLOAD_MULTIPLE, 10) ?: 10
        downloadApk(downloadUrl, downloadApkName, downloadMultiple)
    }

    private fun downloadApk(downloadUrl: String, fileName: String, downloadMultiple: Int) {
        // 下载文件存放在DownLoad文件夹中
        val filePath = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
        // 创建file
        val file = File(Environment.getExternalStorageDirectory().path, filePath)
        // 上一次下载的进度，用于断点续传
        var range = DownloadCache.getProgress(this, downloadUrl)
        var totalLength = "-"
        if (file.exists()) {
            if (range == file.length()) {
                eventOnInstall(fileName)
                return
            }
            totalLength += file.length()
        } else {
            range = 0
        }

        // 上一次的下载量
        var lastOffset = 0L
        downloadFile(range, totalLength, downloadUrl, file, downloadMultiple,
            object : DownloadCallBack {
                override fun onProgress(loadSize: Long, totalSize: Long) {
                    // 记录当前的下载进度
                    DownloadCache.saveProgress(this@DownloadIntentService, downloadUrl, loadSize)
                    // 本次的下载量
                    val increase = if (lastOffset == 0L) 0L else loadSize - lastOffset
                    lastOffset = loadSize
                    speedCalculator.downloading(increase)
                    // 下载速度
                    val speed = speedCalculator.speed()
                    // 下载百分比 0..100
                    val percent = loadSize * 100 / totalSize
                    eventOnProgress(percent, speed)
                    if (percent == 100L) {
                        eventOnInstall(fileName)
                    }
                }

                override fun onCompleted() {}

                override fun onError(msg: String?) {}
            })

    }

    private fun downloadFile(
        range: Long, totalLength: String, downloadUrl: String, targetFile: File,
        downloadMultiple: Int, callBack: DownloadCallBack
    ) {
        var inputStream: InputStream? = null
        var randomAccessFile: RandomAccessFile? = null
        try {
            val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                this.connectTimeout = CONNECT_TIMEOUT
                this.setRequestProperty("Range", "bytes=${range}${totalLength}")
                this.setRequestProperty("Connection", "Keep-Alive")
                this.connect()
            }

            inputStream = connection.inputStream
            randomAccessFile = RandomAccessFile(targetFile, "rwd").apply {
                if (range == 0L) {
                    this.setLength(connection.contentLength.toLong())
                }
                this.seek(range)
            }

            var loadSize = range
            val totalSize = randomAccessFile.length()
            val bytes = ByteArray(1024 * downloadMultiple)
            while (true) {
                val read = inputStream.read(bytes)
                if (read == -1) {
                    break
                }
                randomAccessFile.write(bytes, 0, read)
                loadSize += read
                callBack.onProgress(loadSize, totalSize)
            }
            callBack.onCompleted()
        } catch (e: IOException) {
            e.printStackTrace()
            callBack.onError(e.message)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            callBack.onError(e.message)
        } finally {
            try {
                inputStream?.close()
                randomAccessFile?.close()
            } catch (e: IOException) {
                e.printStackTrace()
                callBack.onError(e.message)
            }
        }

    }

    /**
     * 发送下载进度广播
     */
    private fun eventOnProgress(percent: Long, speed: String) {
        sendBroadcast(Intent().apply {
            action = DOWNLOAD_ACTION_PROGRESS
            putExtra(EXTRA_PROGRESS, percent)
            putExtra(EXTRA_SPEED, speed)
        })
    }

    /**
     * 发送安装广播
     */
    private fun eventOnInstall(fileName: String) {
        sendBroadcast(Intent().apply {
            action = DOWNLOAD_ACTION_INSTALL
            putExtra(EXTRA_FILENAME, fileName)
        })
    }

    /**
     * 下载回调
     */
    interface DownloadCallBack {
        fun onProgress(loadSize: Long, totalSize: Long)
        fun onCompleted()
        fun onError(msg: String?)
    }


}

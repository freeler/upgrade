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
    }

    /** 计算下载速度辅助工具类*/
    private val speedCalculator by lazy { SpeedCalculator() }


    override fun onHandleIntent(intent: Intent?) {
        val downloadUrl = intent?.getStringExtra("download_url") ?: ""
        downloadApk(downloadUrl)
    }

    private fun downloadApk(downloadUrl: String) {
        // 直接截取下载地址最后一个"/"后面的字符串作为下载的文件名
        val fileName = downloadUrl.substringAfterLast("/")
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
        downloadFile(range, totalLength, downloadUrl, file, object : DownloadCallBack {
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
        range: Long,
        totalLength: String,
        downloadUrl: String,
        targetFile: File,
        callBack: DownloadCallBack
    ) {
        var inputStream: InputStream? = null
        var randomAccessFile: RandomAccessFile? = null
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.setRequestProperty("Range", "bytes=${range}${totalLength}")
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.connect()

            inputStream = connection.inputStream
            randomAccessFile = RandomAccessFile(targetFile, "rwd")
            if (range == 0L) {
                val responseLength = connection.contentLength
                randomAccessFile.setLength(responseLength.toLong())
            }
            randomAccessFile.seek(range)

            var loadSize: Long = range
            val totalSize = randomAccessFile.length()
            val fileReader = ByteArray(1024 * 10)
            while (true) {
                val read = inputStream.read(fileReader)
                if (read == -1) {
                    break
                }
                randomAccessFile.write(fileReader, 0, read)
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
            putExtra("progress", percent)
            putExtra("speed", speed)
        })
    }

    /**
     * 发送安装广播
     */
    private fun eventOnInstall(fileName: String) {
        sendBroadcast(Intent().apply {
            action = DOWNLOAD_ACTION_INSTALL
            putExtra("fileName", fileName)
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

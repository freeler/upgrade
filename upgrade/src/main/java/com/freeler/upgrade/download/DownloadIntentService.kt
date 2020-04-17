package com.freeler.upgrade.download

import android.app.IntentService
import android.content.Intent
import android.os.Environment
import com.freeler.upgrade.utils.DownloadCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*

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

    override fun onHandleIntent(intent: Intent?) {
        val downloadUrl = intent?.getStringExtra("download_url") ?: ""
        downloadApk(downloadUrl)
    }

    private fun downloadApk(downloadUrl: String) {
        val fileName = downloadUrl.substringAfterLast("/")
        val filePath = "${Environment.DIRECTORY_DOWNLOADS}/$fileName"
        val file = File(Environment.getExternalStorageDirectory().path, filePath)

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

        downloadFile(range, totalLength, downloadUrl, file, object : DownloadCallBack {
            override fun onProgress(loadSize: Long, totalSize: Long) {
                DownloadCache.saveProgress(this@DownloadIntentService, downloadUrl, loadSize)
                val percent = loadSize * 100 / totalSize
                eventOnProgress(percent)
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
        val request = Request.Builder()
            .addHeader("Range", "bytes=${range}${totalLength}")
            .url(downloadUrl)
            .build()
        val call = OkHttpClient().newCall(request)
        val response = call.execute()
        if (response.isSuccessful) {

            val body = response.body ?: return
            var inputStream: InputStream? = null
            var randomAccessFile: RandomAccessFile? = null
            try {
                inputStream = body.byteStream()
                randomAccessFile = RandomAccessFile(targetFile, "rwd")
                if (range == 0L) {
                    val responseLength = body.contentLength()
                    randomAccessFile.setLength(responseLength)
                }
                randomAccessFile.seek(range)

                var loadSize: Long = range
                val totalSize = randomAccessFile.length()
                val fileReader = ByteArray(1024 * 4)
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
                }
            }

        }

    }


    private fun eventOnProgress(percent: Long) {
        sendBroadcast(Intent().apply {
            action = DOWNLOAD_ACTION_PROGRESS
            putExtra("progress", percent)
        })
    }

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

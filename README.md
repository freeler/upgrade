# Upgrade

Android 版本升级
- 支持断点续传;
- 支持下载速度计算;
- 仅可同时下载一个文件（防止重复下载）;

## Screenshot

![](https://github.com/freeler/upgrade/blob/master/screenshot/1.png)
![](https://github.com/freeler/upgrade/blob/master/screenshot/2.png)
![](https://github.com/freeler/upgrade/blob/master/screenshot/3.png)


## 使用
- 方式 1

```java
```

- 方式 2. 拷贝Libs工程里面的lib_upgrade到自己的工程里面

## 注意点
下载是通过开启IntentService在后台下载，任务完成后自动停止；

## 范例

```java

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    mBtnStartDownload.setOnClickListener {
        // 开启下载，下载会在IntentService中执行，如果当前正在下载中，不会重复执行下载任务
        DownloadManager.downloadApk(
            this, "https://XXX/XXX.apk"
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



```



- DownloadManager 方法说明

| Attribute                  | 方法含义                                     |
|:---------------------------|:--------------------------------------------|
| downloadApk           | 下载APK  |  
| installApk          | 安装APK     |  
| installWithPermission | 检查权限并安装 |

- DownloadIntentService 说明

| Attribute                  | 方法含义                                     |
|:---------------------------|:--------------------------------------------|
| DOWNLOAD_ACTION_PROGRESS           | 下载进度广播  |  
| DOWNLOAD_ACTION_INSTALL          | 安装APK广播     |  

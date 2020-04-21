package com.freeler.upgrade.utils

import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleObserver


private val TAG = "UpgradeServiceManager"


fun Activity.requestPermission(vararg permissions: String, listener: (isGrant: Boolean) -> Unit) {
    getFragment().getPermissions(permissions.asList().toTypedArray(), listener)
}

fun Activity.startForResult(intent: Intent, listener: (Int, Intent?) -> Unit) {
    getFragment().startForResult(intent, listener)
}


/**
 * 获取/创建 ForResultFragment
 */
private fun Activity.getFragment(): UpgradeForResultFragment {
    var fragment = fragmentManager.findFragmentByTag(TAG)
    if (fragment == null) {
        fragment = UpgradeForResultFragment()
        fragmentManager.beginTransaction().add(fragment, TAG).commitAllowingStateLoss()
        fragmentManager.executePendingTransactions()
    }
    return fragment as UpgradeForResultFragment
}

/**
 * ForResultFragment
 */
class UpgradeForResultFragment : Fragment(), LifecycleObserver {

    private val permissionMap = mutableMapOf<Int, (Boolean) -> Unit>()
    private val resultMap = mutableMapOf<Int, (Int, Intent?) -> Unit>()
    private var requestCode = 0
    private var resultCode = 0
    private var data: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    /**
     * 是否有权限
     */
    private fun isGranted(permissions: Array<String>): Boolean {
        val checkResult = permissions.map { ActivityCompat.checkSelfPermission(context!!, it) }
        return !checkResult.contains(PackageManager.PERMISSION_DENIED)
    }

    /**
     * 请求权限
     */
    fun getPermissions(permissions: Array<String>, listener: (Boolean) -> Unit) {
        //已有权限
        if (isGranted(permissions)) {
            listener.invoke(true)
            return
        }
        //申请权限
        val requestCode = permissionMap.size + 1
        permissionMap[requestCode] = listener
        requestPermissions(permissions, requestCode)
    }

    /**
     * 权限返回
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val listener = permissionMap.remove(requestCode)
        val isGrant = !grantResults.contains(PackageManager.PERMISSION_DENIED)
        listener?.invoke(isGrant)
    }


    /***
     * 跳转Activity
     */
    fun startForResult(intent: Intent, listener: (Int, Intent?) -> Unit) {
        val requestCode = resultMap.size + 1
        resultMap[requestCode] = listener
        startActivityForResult(intent, requestCode)
    }

    /**
     * Activity 返回结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        this.requestCode = requestCode
        this.resultCode = resultCode
        this.data = data
        val listener = resultMap.remove(requestCode)
        listener?.invoke(resultCode, data)
    }

}
package com.example.camerademo.util

import android.Manifest
import android.app.DownloadManager
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException


//  1)  (首先 /sdcard/、/storage/self/primary/ 真正指向的是/storage/emulated/0/，所以用x表示这三者)  x/[DCIM, Alarm]

inline fun <T> sdk29AndUp(onSdk29: () -> T): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onSdk29()
    } else null
}

fun askPermissionForExternalPublicStorage(activity: FragmentActivity) {
    val requestList = ArrayList<String>()
    // 需要什么权限的逻辑 参考 https://blog.csdn.net/guolin_blog/article/details/127024559
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requestList.add(Manifest.permission.READ_MEDIA_IMAGES)
        requestList.add(Manifest.permission.READ_MEDIA_AUDIO)
        requestList.add(Manifest.permission.READ_MEDIA_VIDEO)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        requestList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        requestList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        requestList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (requestList.isNotEmpty()) {
        PermissionX.init(activity)
            .permissions(requestList)
            .explainReasonBeforeRequest()
            .onExplainRequestReason { scope, deniedList ->
                val message = "PermissionX需要您同意以下权限才能正常使用"
                scope.showRequestReasonDialog(deniedList, message, "Allow", "Deny")
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "You need to allow necessary permissions in Settings manually",
                    "OK",
                    "Cancel"
                )
            }
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    Toast.makeText(activity, "所有申请的权限都已通过", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "您拒绝了如下权限：$deniedList", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

/**
 * 放在 x/Pictures 文件夹
 */
suspend fun saveImageToExternalPublicStorage(
    contentResolver: ContentResolver,
    filename: String,
    bmp: Bitmap
): Boolean {
    return withContext(Dispatchers.IO) {
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bmp.width)
            put(MediaStore.Images.Media.HEIGHT, bmp.height)
        }
        try {
            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException("Couldn't save bitmap")
                    }
                }
            } ?: throw IOException("Couldn't create MediaStore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * 使用[DownloadManager]下载一张图片到 x/Download 目录
 *
 * setDestinationInExternalFilesDir这一行的等价写法为
 *      setDestinationUri(Uri.fromFile(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "image.jpg"))) 这个api的uri只能是external storage的路径
 */
fun downloadImageToExternalPublic(
    context: Context,
    filename: String,
    url: String = "https://i.pinimg.com/474x/01/96/ab/0196abb1fc39a30271bab3576120454d.jpg"
): Long {
    val downloadManager = context.getSystemService(DownloadManager::class.java)
    val request = DownloadManager.Request(url.toUri())
        .setMimeType("image/jpeg")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setTitle("girl.jpg")
        .addRequestHeader("Authorization", "Bearer <token>")
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
    return downloadManager.enqueue(request)
}

/**
 * 有权限，就包括其他应用创建的图片
 * 没有权限，只能访问自己应用创建的
 */
suspend fun loadImageFromExternalPublicStorage(contentResolver: ContentResolver): List<SharedStoragePhoto> {
    return withContext(Dispatchers.IO) {

        // 要查找的集合 概念类似数据库的表
        val collection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // column
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val photos = mutableListOf<SharedStoragePhoto>()

        // sql
        contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->

            // 列的名称
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(SharedStoragePhoto(id, displayName, width, height, contentUri))
            }
            photos.toList()
        } ?: listOf()
    }
}


suspend fun deleteImageFromExternalPublicStorage(
    contentResolver: ContentResolver,
    intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>,
    photoUri: Uri
) {
    withContext(Dispatchers.IO) {
        try {
            contentResolver.delete(photoUri, null, null)
        } catch (e: SecurityException) {
            // 启用了分区存储，若要删除其他应用的文件，会进来
            val intentSender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    MediaStore.createDeleteRequest(
                        contentResolver,
                        listOf(photoUri)
                    ).intentSender// Android 11才有这个方法
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val recoverableSecurityException =
                        e as? RecoverableSecurityException
                    recoverableSecurityException?.userAction?.actionIntent?.intentSender
                }
                else -> null
            }
            Log.d("deleteImageFromExternalPublicStorage", intentSender.toString())
            intentSender?.let { sender ->
                intentSenderLauncher.launch(
                    IntentSenderRequest.Builder(sender).build()
                )
            }
        }
    }
}

data class SharedStoragePhoto(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val contentUri: Uri
)

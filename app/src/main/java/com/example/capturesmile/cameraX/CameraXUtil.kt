package com.example.camerademo.cameraX

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

fun startCamera(
    context: Context,
    previewView: PreviewView,
    executor: Executor,
    imageCapture: ImageCapture,
    analyzer: Analyzer
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    val runnable = Runnable {
        // 用于将相机的生命周期绑定到生命周期所有者
        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

        // 构建预览用例
        val preview = buildPreviewUseCase(previewView)

        // 构建图片分析用例
        val imageAnalysis = buildSmileAnalyzeUseCase(executor, analyzer)

        // 默认选择前置摄像头
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            // 在重新绑定之前取消绑定用例
            cameraProvider.unbindAll()

            // 将用例绑定到相机
            cameraProvider.bindToLifecycle(
                context as LifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis
            )

        } catch (exc: Exception) {
            Timber.d("bind fail")
        }
    }
    // 在 ProcessCameraProvider 可用之后会执行 runnable
    cameraProviderFuture.addListener(runnable, ContextCompat.getMainExecutor(context))
}

private fun buildSmileAnalyzeUseCase(executor: Executor, analyzer: Analyzer): ImageAnalysis {
    val imageAnalysis =
        ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also {
                it.setAnalyzer(
                    executor, analyzer
                )
            }
    return imageAnalysis
}


fun buildPreviewUseCase(previewView: PreviewView): Preview {
    // 构建预览用例
    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }
    return preview
}

fun takePhoto(context: Context, imageCapture: ImageCapture) {
// Create time stamped name and MediaStore entry.
    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }

    // Create output options object which contains file + metadata
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
    ).build()

    // Set up image capture listener, which is triggered after photo has
    // been taken
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Timber.e("Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val msg = "Photo capture succeeded: ${output.savedUri}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                Timber.d(msg)
            }
        })
}

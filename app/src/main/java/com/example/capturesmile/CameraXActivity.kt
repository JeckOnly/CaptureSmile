package com.example.capturesmile

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentActivity
import com.example.camerademo.cameraX.FaceSmileAnalyzer
import com.example.camerademo.cameraX.startCamera
import com.example.camerademo.cameraX.takePhoto
import com.example.camerademo.util.askPermissionForExternalPublicStorage
import com.jeckonly.capturesmile.R
import com.jeckonly.capturesmile.databinding.ActivityCameraxBinding
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraXActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraxBinding

    private var smilePossibility: MutableState<Float> = mutableStateOf(0f)

    private val maxCaptureNumber = 5

    private var nowCapturedNumber = 0

    /**
     * 起码隔一秒钟拍摄一张照片
     */
    private val captureDelay = 1500L

    /**
     * 上一次拍摄的时间
     */
    private var lastCaptureTime = 0L

    /**
     * 抓拍阈值
     */
    private val captureSmileRate = 0.9

    private val smileAnalyzer by lazy {
        FaceSmileAnalyzer {
            smilePossibility.value = it
            if (startCapture.value and ((System.currentTimeMillis() - lastCaptureTime) > captureDelay) and (nowCapturedNumber < maxCaptureNumber) and (it > captureSmileRate)) {
                takePhoto(this, imageCapture)
                lastCaptureTime = System.currentTimeMillis()
                nowCapturedNumber += 1
            }
        }
    }

    /**
     * 是否启动抓拍
     */
    private var startCapture: MutableState<Boolean> = mutableStateOf(false)

    private val imageCapture by lazy {
        ImageCapture.Builder()
            .build()
    }

    private val cachedExecutor = Executors.newCachedThreadPool()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_camerax)
        binding.cameraxComposeView.setContent {
            val smileValue = smilePossibility.value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = smileValue,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(10.dp),
                        color = if (smileValue > 0.7f) Color.Green else Color.Red,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    Row {
                        Button(onClick = {
                            startCamera(
                                this@CameraXActivity,
                                binding.cameraxPreview,
                                cachedExecutor,
                                imageCapture,
                                smileAnalyzer
                            )
                        }) {
                            Text(text = "cameraX启动")
                        }
                        Spacer(modifier = Modifier.width(20.dp))
//                        Button(onClick = {
//                            startActivity(Intent(this@CameraXActivity, Camera2Activity::class.java))
//                        }) {
//                            Text(text = "跳转到camera2")
//                        }

                        Button(onClick = {
                            startCapture.value = !startCapture.value
                        }) {
                            if (startCapture.value) Text(text = "抓拍已开启") else Text(text = "抓拍已关闭")
                        }
                    }
                }
            }
        }
        askPermission(this)
        askPermissionForExternalPublicStorage(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cachedExecutor.shutdown()
    }
}

fun askPermission(activity: FragmentActivity) {
    val requestList = mutableListOf<String>()
    if (ContextCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        requestList.add(android.Manifest.permission.CAMERA)
    }
    if (requestList.isNotEmpty()) {
        PermissionX.init(activity)
            .permissions(requestList)
            .explainReasonBeforeRequest()
            .onExplainRequestReason { scope, deniedList ->
                val message = "The Application needs permissions below to run"
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
                    Toast.makeText(activity, "All permisssions are allowed", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(activity, "You have denied：$deniedList", Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }
}

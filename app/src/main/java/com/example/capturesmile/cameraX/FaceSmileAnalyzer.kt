package com.example.camerademo.cameraX

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import timber.log.Timber

class FaceSmileAnalyzer(private val onSmilePossibilityCallback: (Float) -> Unit) :
    ImageAnalysis.Analyzer {

    private val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()

    private val detector = FaceDetection.getClient(highAccuracyOpts)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            //  ...
            Timber.d("image: $image")
            detector.process(image).addOnSuccessListener { faces ->
                for (face in faces) {
                    Timber.d("face loop")
                    getSmilePossibilityFromFace(face, onSmilePossibilityCallback)
                }
            }.addOnFailureListener { e ->
                // Task failed with an exception
                // ...

                Timber.e("image analyzer failure ${e.printStackTrace()}")
            }.addOnCompleteListener {
                imageProxy.close()
            }

        }
    }

    private fun getSmilePossibilityFromFace(
        face: Face, onSmilePossibilityCallback: (Float) -> Unit
    ) {
        if (face.smilingProbability != null) {
            val smileProb = face.smilingProbability ?: 0f
            onSmilePossibilityCallback(smileProb)
            Timber.d("smile possibility $smileProb")
        }
    }


}
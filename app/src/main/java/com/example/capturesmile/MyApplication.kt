package com.example.capturesmile

import android.app.Application
import androidx.databinding.ktx.BuildConfig
import com.example.capturesmile.util.FileClassMethodTag
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MyApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber注册
        if (BuildConfig.DEBUG) {
            Timber.plant(FileClassMethodTag())
        }
    }

}
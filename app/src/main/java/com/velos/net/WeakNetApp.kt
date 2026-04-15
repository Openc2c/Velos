package com.velos.net

import android.app.Application

class WeakNetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: WeakNetApp
            private set
    }
}

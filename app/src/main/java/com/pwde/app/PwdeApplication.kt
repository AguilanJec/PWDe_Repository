package com.pwde.app

import android.app.Application
import com.pwde.app.di.AppContainer

class PwdeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

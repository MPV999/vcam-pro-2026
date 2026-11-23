package com.vcampro.ultimate

import android.app.Application
import com.vcampro.ultimate.core.di.AppGraph

class VcamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

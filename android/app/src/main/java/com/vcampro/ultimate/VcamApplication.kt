package com.vcampro.ultimate

import android.app.Application
import com.vcampro.ultimate.core.CrashReporter
import com.vcampro.ultimate.core.di.AppGraph

class VcamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Record any fatal error so the next launch can show what happened
        // instead of the app simply disappearing.
        CrashReporter.installHandler(this)
        AppGraph.init(this)
    }
}

package com.yagay.YSuite

import android.app.Application
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteLog

class SuiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RootManager.configure(this)
        val states = FeatureStateStore(this)
        FeatureRegistry.included().filter(states::isEnabled).forEach { feature ->
            runCatching { feature.initialize(this) }
                .onSuccess { SuiteLog.i(this, feature.id, "feature initialized") }
                .onFailure { SuiteLog.e(this, feature.id, "feature initialization failed", it) }
        }
        SuiteLog.i(this, "suite", "YSuite started; features=${FeatureRegistry.included().size}")
    }
}

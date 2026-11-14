package com.akkageugot.demo

import android.app.Application
import com.akkageugot.demo.contract.EngineProvider
import com.akkageugot.demo.engine.RealDemoEngine
import com.akkageugot.demo.ui.fake.FakeDemoEngine

class AkkaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EngineProvider.engine =
            if (BuildConfig.USE_FAKE_ENGINE) FakeDemoEngine(this) else RealDemoEngine(this)
        EngineProvider.engine.start()
    }
}

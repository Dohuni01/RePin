package com.akkageugot.demo.engine.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 엔진 포그라운드 서비스. 0단계 스텁 — 이후 단계에서 주행 시뮬레이션을 붙인다.
 */
class AkkaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}

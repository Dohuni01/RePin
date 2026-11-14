package com.akkageugot.demo.engine.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 발표용 adb 명령 수신기. 0단계 스텁 — 3단계에서 명령 처리를 붙인다.
 * 호출: adb shell am broadcast -n com.akkageugot.demo/com.akkageugot.demo.engine.demo.DemoCommandReceiver
 *       -a com.akkageugot.demo.DEMO --es cmd say --ei preset 1
 */
class DemoCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AKKA", "DemoCommandReceiver: cmd=${intent.getStringExtra("cmd")}")
    }
}

package com.akkageugot.demo.engine.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.akkageugot.demo.contract.Vec2
import com.akkageugot.demo.engine.core.LatLon
import com.akkageugot.demo.engine.core.LocalProjection
import com.akkageugot.demo.engine.platform.NaviMirror

/**
 * Pleos NaviHelper 미러링 (engine/PROMPT.md 7절, docs/PLEOS_SDK_NOTES.md).
 * 데모 내비와 별개로 실제 내비에도 requestRoute/addWaypoint를 보내고,
 * 콜백 결과를 logcat(AKKA)과 상태 문자열(debug.sdkStatus["NAVI"])로 받는다.
 */
class NaviHelperMirror(
    private val context: Context,
    private val projection: LocalProjection
) : NaviMirror {

    private var navi: NaviHelper? = null
    @Volatile private var ready = false
    @Volatile private var lastEvent = "미연결"

    override val status: String
        get() = if (ready) "Pleos NaviHelper · $lastEvent" else "NaviHelper 미연결 ($lastEvent)"

    private val listener = object : NaviHelperEventListener {
        override fun onRoutingCompleted() { event("onRoutingCompleted") }
        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) { event("onRouteStarted") }
        override fun onRouteCancelled() { event("onRouteCancelled") }
        override fun onWaypointChanged(info: ai.pleos.playground.navi.data.WaypointChangedInfo) { event("onWaypointChanged") }
        override fun onError(code: NaviErrorCode) { event("onError=$code") }
    }

    private fun event(msg: String) {
        lastEvent = msg
        Log.i("AKKA", "NAVI 미러 콜백: $msg")
    }

    override fun start() {
        try {
            val helper = NaviHelper(context)
            navi = helper
            helper.initialize(
                { // onSuccess
                    runCatching {
                        helper.addListener(listener)
                        ready = true
                        lastEvent = "초기화 성공"
                        Log.i("AKKA", "NaviHelper 미러 초기화 성공")
                    }.onFailure { fail(it.message) }
                },
                { t -> fail(t.message) } // onError
            )
        } catch (t: Throwable) {
            fail(t.message)
        }
    }

    private fun fail(msg: String?) {
        ready = false
        lastEvent = "초기화 실패: $msg"
        Log.w("AKKA", "NaviHelper 미러 초기화 실패 → DemoNavigation만 사용: $msg")
    }

    override fun requestRoute(destination: Vec2, name: String) {
        if (!ready) return
        val ll = projection.toLatLon(destination)
        runCatching {
            navi?.requestRoute(routeInfo(ll, name))
            Log.i("AKKA", "NAVI 미러 requestRoute: $name (${"%.5f".format(ll.lat)}, ${"%.5f".format(ll.lon)})")
        }.onFailure { event("requestRoute 실패=${it.message}") }
    }

    override fun addWaypoint(waypoint: Vec2, name: String) {
        if (!ready) return
        val ll = projection.toLatLon(waypoint)
        runCatching {
            navi?.addWaypoint(
                RequestWaypointInfo(ll.lat, ll.lon, name, WaypointIndex.FIRST, "", "", "", "")
            )
            Log.i("AKKA", "NAVI 미러 addWaypoint: $name")
        }.onFailure { event("addWaypoint 실패=${it.message}") }
    }

    private fun routeInfo(ll: LatLon, name: String): RouteInfo =
        RouteInfo(ll.lat, ll.lon, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false)

    override fun stop() {
        runCatching {
            navi?.removeListener(listener)
            navi?.release()
        }
        navi = null
        ready = false
    }
}

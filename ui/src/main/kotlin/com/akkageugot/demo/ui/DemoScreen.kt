package com.akkageugot.demo.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.akkageugot.demo.contract.AppState
import com.akkageugot.demo.contract.Gear
import com.akkageugot.demo.contract.UiAction
import com.akkageugot.demo.ui.common.IconKind
import com.akkageugot.demo.ui.common.LocalAkkaColors
import com.akkageugot.demo.ui.dev.DevScreen
import com.akkageugot.demo.ui.dev.UiFlags
import com.akkageugot.demo.ui.navi.BottomInfo
import com.akkageugot.demo.ui.navi.DemoNaviView
import com.akkageugot.demo.ui.navi.MicButton
import com.akkageugot.demo.ui.navi.RoundIconButton
import com.akkageugot.demo.ui.navi.TbtChip
import com.akkageugot.demo.ui.overlay.HeadsUpCard
import com.akkageugot.demo.ui.overlay.VoiceBubble
import com.akkageugot.demo.ui.parked.ParkedHome
import com.akkageugot.demo.ui.parked.ParkedView
import com.akkageugot.demo.ui.parked.QrDialog
import com.akkageugot.demo.ui.presenter.DebugPanel
import com.akkageugot.demo.ui.presenter.PresenterTag
import com.akkageugot.demo.ui.presenter.WindowStrip

/**
 * 앱 전체 화면. AppState를 그리고 UiAction만 보낸다.
 * - 주행(gear D): 내비 전체 화면 + 카드/말풍선
 * - 정차(gear P, 주행 제한 해제): 화면 전체가 정차 홈(거울 앨범). 아래 막대의 [지도]로 내비를 볼 수 있다
 * 화면 로컬 상태는 "정차 화면에서 무엇을 보는지"와 "QR 대상"뿐이고, 기어가 D로 바뀌면 둘 다 초기화된다.
 */
@Composable
fun DemoScreen(
    state: AppState,
    flags: UiFlags,
    onFlags: (UiFlags) -> Unit,
    dispatch: (UiAction) -> Unit,
    modifier: Modifier = Modifier,
    initialDevOpen: Boolean = false
) {
    val c = LocalAkkaColors.current
    // 정차 화면(홈, 목록, 설정, QR)은 기어 P이면서 주행 제한이 풀렸을 때만 연다
    val parked = state.drive.gear == Gear.P && !state.drive.restricted
    var view by remember { mutableStateOf(if (initialDevOpen) ParkedView.SETTINGS else ParkedView.HOME) }
    var qrSavedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(parked) {
        if (!parked) {
            view = ParkedView.HOME
            qrSavedId = null
        }
    }

    Box(modifier.fillMaxSize().background(c.mapBg)) {
        if (parked && view != ParkedView.MAP) {
            ParkedHome(
                state = state,
                view = view,
                onView = { view = it },
                dispatch = dispatch,
                onShowQr = { qrSavedId = it.id },
                settingsContent = {
                    DevScreen(state, flags, onFlags, dispatch, onClose = { view = ParkedView.HOME })
                }
            )
        } else {
            NaviPane(
                state = state,
                flags = flags,
                dispatch = dispatch,
                parked = parked,
                onOpenHome = { view = ParkedView.HOME },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // QR은 정차 중에만. 기어가 D가 되면 parked 조건으로 즉시 사라진다
    val qrPlace = if (parked) state.saved.firstOrNull { it.id == qrSavedId } else null
    if (qrPlace != null) {
        QrDialog(qrPlace, onDismiss = { qrSavedId = null })
    }
}

/** 내비 지도 + 그 위의 칩/버튼/카드/말풍선 */
@Composable
fun NaviPane(
    state: AppState,
    flags: UiFlags,
    dispatch: (UiAction) -> Unit,
    parked: Boolean,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 디버그 오버레이는 발표자 모드일 때만 (발표자 모드를 끄면 제품 화면만 남는다)
    val debugOn = flags.presenter && flags.debugOverlay
    // 오른쪽 아래 말하기 버튼(116dp) + 여백만큼 왼쪽 아래 영역을 비워 둔다
    val micLaneDp = 24.dp + 116.dp + 16.dp
    BoxWithConstraints(modifier) {
        // 거울 카드는 위쪽 가운데. 넓으면 TBT 칩 옆에, 좁으면(세로 화면) TBT 칩 아래에 둔다
        val wide = maxWidth >= 1000.dp
        val cardWidth = if (wide) min(640.dp, maxWidth - 520.dp) else min(640.dp, maxWidth - 40.dp)
        val cardTop = if (wide) 0.dp else 110.dp
        DemoNaviView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            showNames = flags.presenter,
            showDebug = debugOn
        )

        Column(
            Modifier.align(Alignment.TopStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TbtChip(state.drive)
            if (debugOn) DebugPanel(state.debug.candidates)
        }

        state.headsUp?.let { h ->
            HeadsUpCard(
                headsUp = h,
                onClick = { dispatch(UiAction.HeadsUpClicked(h.id, it)) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = cardTop).width(cardWidth)
            )
        }

        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = micLaneDp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.voiceLines.isNotEmpty()) VoiceBubble(state.voiceLines)
            if (flags.presenter) WindowStrip(state)
            BottomInfo(state.drive)
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (parked) {
                // 정차 중 지도 보기에서 홈(거울 앨범)으로 돌아가기
                RoundIconButton(IconKind.HOME, onOpenHome)
            } else {
                // 주행 중에는 목록을 열지 않는다
                RoundIconButton(IconKind.LIST, onClick = {
                    Toast.makeText(context, "주행 중에는 음성으로 이용해 주세요", Toast.LENGTH_SHORT).show()
                })
            }
            MicButton(
                phase = state.voicePhase,
                onPress = { dispatch(UiAction.MicPressed) },
                onRelease = { dispatch(UiAction.MicReleased) }
            )
        }

        if (flags.presenter) {
            PresenterTag(Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

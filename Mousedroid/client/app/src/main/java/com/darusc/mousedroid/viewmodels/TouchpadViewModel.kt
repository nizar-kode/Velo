package com.darusc.mousedroid.viewmodels

import android.view.View
import com.darusc.mousedroid.R
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.ConnectionManager

class TouchpadViewModel: BaseViewModel<TouchpadViewModel.State, TouchpadViewModel.Event>(State()) {

    class State: BaseViewModel.State()
    sealed class Event: BaseViewModel.Event()

    private val connectionManager = ConnectionManager.getInstance()

    fun sendMouseEvent(event: InputEvent) {
        connectionManager.send(event)
    }

    fun onMediaButtonClick(view: View) {
        com.darusc.mousedroid.utils.HapticHelper.click(view)
        when(view.id) {
            R.id.btnPrev -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.PREVIOUS))
            R.id.btnPlayPause -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.PLAY_PAUSE))
            R.id.btnNext -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.NEXT))
            R.id.btnForward10 -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.FORWARD))
            R.id.btnVolDown -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.VOLUME_DOWN))
            R.id.btnVolUp -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.VOLUME_UP))
            R.id.btnVolOff -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.VOLUME_MUTE))
            R.id.btnReplay10 -> connectionManager.send(InputEvent.MediaEvent(InputEvent.MediaAction.REPLAY))
        }
    }

    fun onMouseButtonClick(view: View) {
        com.darusc.mousedroid.utils.HapticHelper.heavyClick(view)
        when(view.id) {
            R.id.btnLeftClick -> connectionManager.send(InputEvent.MouseClick(InputEvent.MouseButton.LEFT))
            R.id.btnRightClick -> connectionManager.send(InputEvent.MouseClick(InputEvent.MouseButton.RIGHT))
            R.id.btnMiddleClick -> connectionManager.send(InputEvent.MouseClick(InputEvent.MouseButton.MIDDLE))
        }
    }

    fun sendSlideKey(isNext: Boolean) {
        val code = if (isNext) com.darusc.mousedroid.layouts.Keycode.KEY_PAGE_DOWN else com.darusc.mousedroid.layouts.Keycode.KEY_PAGE_UP
        val key = com.darusc.mousedroid.layouts.KeyboardLayout.Key(com.darusc.mousedroid.layouts.Keycode.MOD_NONE, code)
        connectionManager.send(InputEvent.KeyPress(listOf(key)))
    }
}
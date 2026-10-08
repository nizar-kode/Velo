package com.darusc.mousedroid.viewmodels

import com.darusc.mousedroid.layouts.KeyboardLayout
import com.darusc.mousedroid.layouts.Keycode
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.ConnectionManager

class PresenterViewModel : BaseViewModel<PresenterViewModel.State, PresenterViewModel.Event>(State()) {

    class State : BaseViewModel.State()
    sealed class Event : BaseViewModel.Event()

    private val connectionManager = ConnectionManager.getInstance()

    fun nextSlide() {
        sendKey(Keycode.KEY_PAGE_DOWN)
    }

    fun prevSlide() {
        sendKey(Keycode.KEY_PAGE_UP)
    }

    fun startPresentation() {
        sendKey(Keycode.KEY_F5)
    }

    fun blackScreen() {
        sendKey(Keycode.KEY_B)
    }

    fun whiteScreen() {
        sendKey(Keycode.KEY_W)
    }

    fun togglePowerPointLaser() {
        sendKey(Keycode.KEY_L, Keycode.MOD_LEFT_CTRL)
    }

    fun exitPresentation() {
        sendKey(Keycode.KEY_ESC)
    }

    fun sendMouseMove(dx: Int, dy: Int) {
        connectionManager.send(InputEvent.MouseMove(dx, dy))
    }

    private fun sendKey(code: Byte, modifier: Byte = Keycode.MOD_NONE) {
        val key = KeyboardLayout.Key(modifier, code)
        connectionManager.send(InputEvent.KeyPress(listOf(key)))
    }
}

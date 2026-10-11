package com.darusc.mousedroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentShortcutsBinding
import com.darusc.mousedroid.layouts.KeyboardLayout
import com.darusc.mousedroid.layouts.Keycode
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.ConnectionManager
import com.darusc.mousedroid.utils.HapticHelper

/**
 * Modern Windows Shortcuts screen with rounded glass chips.
 * Dispatches key events directly through the existing Mousedroid input pipeline.
 */
class Shortcuts : Fragment() {

    private lateinit var binding: FragmentShortcutsBinding
    private val connectionManager = ConnectionManager.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_shortcuts, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Soft Keyboard
        binding.btnOpenSoftKeyboard.setOnClickListener {
            HapticHelper.click(it)
            (parentFragment as? Input)?.openSoftKeyboard()
        }

        // Clipboard & Edit Shortcuts
        binding.btnCopy.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_LEFT_CTRL, Keycode.KEY_C)
        }

        binding.btnPaste.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_LEFT_CTRL, Keycode.KEY_V)
        }

        binding.btnCut.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_LEFT_CTRL, Keycode.KEY_X)
        }

        binding.btnUndo.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_LEFT_CTRL, Keycode.KEY_Z)
        }

        binding.btnSelectAll.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_LEFT_CTRL, Keycode.KEY_A)
        }

        binding.btnSave.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_LEFT_CTRL, Keycode.KEY_S)
        }

        // Windows & Navigation Shortcuts
        binding.btnAltTab.setOnClickListener {
            HapticHelper.heavyClick(it)
            sendShortcut(Keycode.MOD_LEFT_ALT, Keycode.KEY_TAB)
        }

        binding.btnWinD.setOnClickListener {
            HapticHelper.heavyClick(it)
            sendShortcut(Keycode.MOD_LEFT_GUI, Keycode.KEY_D)
        }

        binding.btnWinTab.setOnClickListener {
            HapticHelper.heavyClick(it)
            sendShortcut(Keycode.MOD_LEFT_GUI, Keycode.KEY_TAB)
        }

        binding.btnEsc.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_NONE, Keycode.KEY_ESC)
        }

        binding.btnEnter.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_NONE, Keycode.KEY_ENTER)
        }

        binding.btnBackspace.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_NONE, Keycode.KEY_BACKSPACE)
        }

        binding.btnDelete.setOnClickListener {
            HapticHelper.click(it)
            sendShortcut(Keycode.MOD_NONE, Keycode.KEY_DELETE)
        }

        // Jump to Numpad
        binding.cardNumpadTrigger.setOnClickListener {
            HapticHelper.click(it)
            (parentFragment as? Input)?.switchToChildFragment(Numpad())
        }
    }

    private fun sendShortcut(modifier: Byte, keycode: Byte) {
        val key = KeyboardLayout.Key(modifier, keycode)
        connectionManager.send(InputEvent.KeyPress(listOf(key)))
    }
}

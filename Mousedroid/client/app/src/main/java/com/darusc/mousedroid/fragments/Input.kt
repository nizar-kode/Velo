package com.darusc.mousedroid.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentInputBinding
import com.darusc.mousedroid.mkinput.KeyboardInputWatcher
import com.darusc.mousedroid.networking.Connection
import com.darusc.mousedroid.utils.HapticHelper
import com.darusc.mousedroid.viewmodels.ConnectionViewModel
import com.darusc.mousedroid.viewmodels.KeyboardViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Modern Input host fragment.
 * Manages floating glass bottom navigation bar, top connection status bar,
 * and child fragment transitions (Trackpad, Presenter, Shortcuts, Media).
 */
class Input: Fragment() {

    private lateinit var binding: FragmentInputBinding

    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private val keyboardViewModel: KeyboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_input, container, false)
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            selectBottomTab(0)
            replaceChildFragment(Touchpad())
        }

        // Hidden input for soft keyboard
        binding.hiddenInput.apply {
            addTextChangedListener(KeyboardInputWatcher(this) { bytes ->
                keyboardViewModel.handleKeypress(bytes)
            })
        }

        // Remove focus when keyboard is dismissed
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if(!isKeyboardVisible && binding.hiddenInput.hasFocus()) {
                binding.hiddenInput.clearFocus()
                binding.hiddenInput.text.clear()
            }
            insets
        }

        // --- Floating Bottom Navigation Tabs ---
        binding.btnNavTouchpad.setOnClickListener {
            HapticHelper.tick(it)
            selectBottomTab(0)
            closeSoftKeyboard()
            replaceChildFragment(Touchpad())
        }

        binding.btnNavPresenter.setOnClickListener {
            HapticHelper.tick(it)
            selectBottomTab(1)
            closeSoftKeyboard()
            replaceChildFragment(Presenter())
        }

        binding.btnNavShortcuts.setOnClickListener {
            HapticHelper.tick(it)
            selectBottomTab(2)
            closeSoftKeyboard()
            replaceChildFragment(Shortcuts())
        }

        binding.btnNavMedia.setOnClickListener {
            HapticHelper.tick(it)
            selectBottomTab(3)
            closeSoftKeyboard()
            replaceChildFragment(Media())
        }

        // Quick Disconnect
        binding.btnQuickDisconnect.setOnClickListener {
            HapticHelper.click(it)
            closeSoftKeyboard()
            connectionViewModel.disconnect()
            findNavController().navigateUp()
        }

        // Side Drawer setup
        binding.navigation.setCheckedItem(R.id.mode_touchpad)
        binding.btnOpenDrawer.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.navigation.setNavigationItemSelectedListener { item ->
            when(item.itemId) {
                R.id.mode_touchpad -> {
                    item.isChecked = true
                    selectBottomTab(0)
                    closeSoftKeyboard()
                    replaceChildFragment(Touchpad())
                }
                R.id.mode_presenter -> {
                    item.isChecked = true
                    selectBottomTab(1)
                    closeSoftKeyboard()
                    replaceChildFragment(Presenter())
                }
                R.id.mode_numpad -> {
                    item.isChecked = true
                    closeSoftKeyboard()
                    replaceChildFragment(Numpad())
                }
                R.id.mode_keyboard -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    openSoftKeyboard()
                    return@setNavigationItemSelectedListener true
                }
                R.id.mode_disconnect -> {
                    closeSoftKeyboard()
                    connectionViewModel.disconnect()
                    findNavController().navigateUp()
                }
                R.id.mode_change_layout -> {
                    showLayoutSelectorDialog(item)
                    return@setNavigationItemSelectedListener true
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Connection State Observer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionViewModel.state.collect {
                        when (it) {
                            is ConnectionViewModel.State.Connected -> {
                                binding.txtHostStatus.text = it.hostName
                                binding.navigation
                                    .getHeaderView(0)
                                    .findViewById<TextView>(R.id.connectionStatus)
                                    .text = "Connected to ${it.hostName}"
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    connectionViewModel.events.collect {
                        when(it) {
                            is ConnectionViewModel.Event.NavigateToInput -> { }
                            is ConnectionViewModel.Event.NavigateToMain -> findNavController().popBackStack(R.id.mainFragment, false)
                            is ConnectionViewModel.Event.ConnectionDisconnected -> {
                                val pview = showPopupDialog(R.layout.connection_disconnected_fragment)
                                pview?.apply {
                                    if(it.connectionMode == Connection.Mode.BLUETOOTH) {
                                        findViewById<TextView>(R.id.subtitle).text = "Bluetooth connection to ${it.hostName} was terminated"
                                        findViewById<TextView>(R.id.description).text = "Host device turned bluetooth off or disconnected this device"
                                    } else {
                                        findViewById<TextView>(R.id.subtitle).text = "${it.connectionMode.name} Connection to Mousedroid server was interrupted."
                                        findViewById<TextView>(R.id.description).text = "Make sure the server is still ON and ADB/WIFI is active."
                                    }
                                }
                            }
                            is ConnectionViewModel.Event.ConnectionFailed -> {
                                val pview = showPopupDialog(R.layout.connection_failed_fragment)
                                pview?.apply {
                                    if(it.connectionMode == Connection.Mode.BLUETOOTH) {
                                        findViewById<TextView>(R.id.subtitle).text = "Bluetooth connection failed"
                                        findViewById<TextView>(R.id.description).text = "Make sure the device is on"
                                    } else {
                                        findViewById<TextView>(R.id.subtitle).text = "Connection to Mousedroid server failed"
                                        findViewById<TextView>(R.id.description).text = "To connect over WIFI make sure you are on the same network as the computer.\nTo connect over USB make sure ADB is on and debugging is enabled.\nMake sure Mousedroid server is allowed through the firewall. "
                                    }
                                }
                            }
                            else -> { }
                        }
                    }
                }
            }
        }
    }

    private fun selectBottomTab(index: Int) {
        val activeColor = 0xFF38BDF8.toInt()
        val inactiveColor = 0xFF94A3B8.toInt()
        val textActive = 0xFFFFFFFF.toInt()

        // Tab 0: Touchpad
        binding.btnNavTouchpad.setBackgroundResource(if (index == 0) R.drawable.glass_nav_item_active else 0)
        binding.iconNavTouchpad.imageTintList = ColorStateList.valueOf(if (index == 0) activeColor else inactiveColor)
        binding.labelNavTouchpad.setTextColor(if (index == 0) textActive else inactiveColor)

        // Tab 1: Presenter
        binding.btnNavPresenter.setBackgroundResource(if (index == 1) R.drawable.glass_nav_item_active else 0)
        binding.iconNavPresenter.imageTintList = ColorStateList.valueOf(if (index == 1) activeColor else inactiveColor)
        binding.labelNavPresenter.setTextColor(if (index == 1) textActive else inactiveColor)

        // Tab 2: Shortcuts
        binding.btnNavShortcuts.setBackgroundResource(if (index == 2) R.drawable.glass_nav_item_active else 0)
        binding.iconNavShortcuts.imageTintList = ColorStateList.valueOf(if (index == 2) activeColor else inactiveColor)
        binding.labelNavShortcuts.setTextColor(if (index == 2) textActive else inactiveColor)

        // Tab 3: Media
        binding.btnNavMedia.setBackgroundResource(if (index == 3) R.drawable.glass_nav_item_active else 0)
        binding.iconNavMedia.imageTintList = ColorStateList.valueOf(if (index == 3) activeColor else inactiveColor)
        binding.labelNavMedia.setTextColor(if (index == 3) textActive else inactiveColor)
    }

    fun switchToChildFragment(fragment: Fragment) {
        closeSoftKeyboard()
        replaceChildFragment(fragment)
    }

    fun setFullscreenMode(fullscreen: Boolean) {
        if (fullscreen) {
            binding.topBar.visibility = View.GONE
            binding.bottomNavCard.visibility = View.GONE
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNavCard.visibility = View.VISIBLE
        }
    }

    private fun replaceChildFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showLayoutSelectorDialog(menuItem: MenuItem) {
        val layouts = keyboardViewModel.layouts.toTypedArray()
        val currentLayoutIndex = layouts.indexOf(keyboardViewModel.activeKeyboardLayout.name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Keyboard Layout")
            .setSingleChoiceItems(layouts, currentLayoutIndex) { dialog, which ->
                val selectedLayoutName = layouts[which]
                if (keyboardViewModel.setKeyboardLayout(selectedLayoutName)) {
                    menuItem.title = "Layout: $selectedLayoutName"
                } else {
                    menuItem.title = "Layout"
                }
                dialog.dismiss()
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun openSoftKeyboard() {
        binding.hiddenInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.hiddenInput, InputMethodManager.SHOW_FORCED)
    }

    private fun closeSoftKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
        view?.clearFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionViewModel.disconnect()
    }
}
package com.darusc.mousedroid.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.transition.TransitionManager
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentTouchpadBinding
import com.darusc.mousedroid.mkinput.GestureHandler
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.sensors.GyroscopeLaserManager
import com.darusc.mousedroid.utils.HapticHelper
import com.darusc.mousedroid.viewmodels.TouchpadViewModel

class Touchpad : Fragment() {

    private val TAG = "Mousedroid"
    private lateinit var binding: FragmentTouchpadBinding

    private val viewModel: TouchpadViewModel by activityViewModels()
    private var gyroLaserManager: GyroscopeLaserManager? = null
    private var isAirMotionActive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_touchpad, container, false)
        binding.viewmodel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Touchpad Gesture Handler
        val gestureHandler = GestureHandler(requireContext()) { event ->
            viewModel.sendMouseEvent(event)
        }
        binding.touchpadSensor.setOnTouchListener { v, event ->
            binding.particleOverlay.onPointerEvent(event)
            gestureHandler.onTouch(v, event)
        }

        // Setup Gyro Air Mouse Engine
        gyroLaserManager = GyroscopeLaserManager(requireContext()) { dx, dy ->
            viewModel.sendMouseEvent(InputEvent.MouseMove(dx, dy))
        }

        // Setup Mode Switcher (Trackpad vs Air Laser)
        binding.btnTabTouchpad.setOnClickListener {
            HapticHelper.tick(it)
            switchToTouchpadTab()
        }

        binding.btnTabAirMouse.setOnClickListener {
            HapticHelper.tick(it)
            switchToAirMouseTab()
        }

        // --- Air Mouse Mode Controls ---

        // 1. Air Mouse Toggle Button (Continuous Motion)
        binding.btnToggleAirMotion.setOnClickListener {
            HapticHelper.heavyClick(it)
            isAirMotionActive = !isAirMotionActive
            updateAirMotionState(isAirMotionActive)
        }

        // 2. Air Mouse Action Buttons
        binding.btnAirLeft.setOnClickListener {
            HapticHelper.heavyClick(it)
            viewModel.sendMouseEvent(InputEvent.MouseClick(InputEvent.MouseButton.LEFT))
        }

        binding.btnAirRight.setOnClickListener {
            HapticHelper.heavyClick(it)
            viewModel.sendMouseEvent(InputEvent.MouseClick(InputEvent.MouseButton.RIGHT))
        }

        binding.btnAirRecenter.setOnClickListener {
            HapticHelper.click(it)
            // Re-calibrate center
            if (isAirMotionActive) {
                gyroLaserManager?.stop()
                gyroLaserManager?.start()
            }
        }

        // 3. Presentation Slides
        binding.btnAirPrevSlide.setOnClickListener {
            HapticHelper.click(it)
            viewModel.sendSlideKey(false)
        }

        binding.btnAirNextSlide.setOnClickListener {
            HapticHelper.click(it)
            viewModel.sendSlideKey(true)
        }

        // 4. Momentary Hold-to-Aim Trigger
        binding.btnAirHoldTrigger.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isAirMotionActive) {
                        HapticHelper.heavyClick(v)
                        HapticHelper.startLaserHum(requireContext())
                        gyroLaserManager?.start()
                        binding.txtAirStatus.text = "LASER ACTIVE (HOLD)"
                        binding.txtAirStatus.setTextColor(0xFF38BDF8.toInt())
                    }
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isAirMotionActive) {
                        HapticHelper.tick(v)
                        gyroLaserManager?.stop()
                        binding.txtAirStatus.text = "AIR MOUSE PAUSED"
                        binding.txtAirStatus.setTextColor(0xFF94A3B8.toInt())
                    }
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }

        // --- Touchpad Controls ---

        // Multimedia dropdown
        binding.btnToggleMedia.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.root as ViewGroup)

            val isHidden = binding.mediaControls.isGone
            binding.mediaControls.visibility = if (isHidden) View.VISIBLE else View.GONE
            binding.btnToggleMedia.setIconResource(
                if (isHidden) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_drop_down
            )
        }

        // Touchpad fullscreen toggle
        binding.btnFullscreen.setOnClickListener {
            val currentOrientation = requireActivity().requestedOrientation

            if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                updateLayoutForOrientation(false)
            } else {
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                updateLayoutForOrientation(true)
            }
        }
    }

    private fun switchToTouchpadTab() {
        binding.btnTabTouchpad.setBackgroundResource(R.drawable.glass_tab_active)
        binding.btnTabTouchpad.setTextColor(0xFFFFFFFF.toInt())
        binding.btnTabAirMouse.background = null
        binding.btnTabAirMouse.setTextColor(0xFF94A3B8.toInt())

        binding.layoutTouchpadMode.visibility = View.VISIBLE
        binding.layoutAirMouseMode.visibility = View.GONE

        // Stop continuous air motion when leaving air tab
        if (isAirMotionActive) {
            isAirMotionActive = false
            updateAirMotionState(false)
        }
    }

    private fun switchToAirMouseTab() {
        binding.btnTabAirMouse.setBackgroundResource(R.drawable.glass_tab_active)
        binding.btnTabAirMouse.setTextColor(0xFFFFFFFF.toInt())
        binding.btnTabTouchpad.background = null
        binding.btnTabTouchpad.setTextColor(0xFF94A3B8.toInt())

        binding.layoutTouchpadMode.visibility = View.GONE
        binding.layoutAirMouseMode.visibility = View.VISIBLE
    }

    private fun updateAirMotionState(active: Boolean) {
        if (active) {
            gyroLaserManager?.start()
            HapticHelper.startLaserHum(requireContext())
            binding.txtAirStatus.text = "AIR MOUSE ACTIVE"
            binding.txtAirStatus.setTextColor(0xFF38BDF8.toInt())
            binding.txtAirHint.text = "Wave phone in the air to guide cursor • Tap below to pause"
            binding.btnToggleAirMotion.text = "⏸ PAUSE AIR MOUSE"
            binding.btnToggleAirMotion.setBackgroundResource(R.drawable.glass_button_laser)
            binding.imgAirMotionIcon.animate().scaleX(1.25f).scaleY(1.25f).setDuration(200).start()
        } else {
            gyroLaserManager?.stop()
            binding.txtAirStatus.text = "AIR MOUSE PAUSED"
            binding.txtAirStatus.setTextColor(0xFF94A3B8.toInt())
            binding.txtAirHint.text = "Tap to enable motion control without holding"
            binding.btnToggleAirMotion.text = "⚡ START AIR MOUSE"
            binding.btnToggleAirMotion.setBackgroundResource(R.drawable.glass_button_primary)
            binding.imgAirMotionIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gyroLaserManager?.stop()
        gyroLaserManager = null
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun updateLayoutForOrientation(landscape: Boolean) {
        val rootLayout = binding.root as ConstraintLayout
        val container = binding.touchpadContainer
        val params = container.layoutParams as ConstraintLayout.LayoutParams

        if (landscape) {
            rootLayout.setPadding(0, 0, 0, 0)
            binding.modeTabBar.visibility = View.GONE

            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.setMargins(0, 0, 0, 0)

            binding.mouseButtonsRow.visibility = View.GONE
            binding.btnToggleMedia.visibility = View.GONE
            binding.mediaControls.visibility = View.GONE
        } else {
            val rootPaddingPx = (16 * resources.displayMetrics.density).toInt()
            rootLayout.setPadding(rootPaddingPx, rootPaddingPx, rootPaddingPx, rootPaddingPx)
            binding.modeTabBar.visibility = View.VISIBLE

            params.topToTop = ConstraintLayout.LayoutParams.UNSET
            params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

            binding.mouseButtonsRow.visibility = View.VISIBLE
            binding.btnToggleMedia.visibility = View.VISIBLE
        }
        (parentFragment as? Input)?.setFullscreenMode(landscape)
        container.layoutParams = params
    }
}

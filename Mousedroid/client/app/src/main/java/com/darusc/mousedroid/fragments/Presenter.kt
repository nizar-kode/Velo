package com.darusc.mousedroid.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentPresenterBinding
import com.darusc.mousedroid.sensors.GyroscopeLaserManager
import com.darusc.mousedroid.utils.HapticHelper
import com.darusc.mousedroid.viewmodels.PresenterViewModel
import java.util.Locale

class Presenter : Fragment() {

    private lateinit var binding: FragmentPresenterBinding
    private val viewModel: PresenterViewModel by viewModels()

    private var gyroLaserManager: GyroscopeLaserManager? = null
    private var isAirActive = false

    private var timerSeconds = 0
    private var isTimerRunning = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                timerSeconds++
                updateTimerDisplay()
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_presenter, container, false)
        binding.viewmodel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gyroLaserManager = GyroscopeLaserManager(requireContext()) { dx, dy ->
            viewModel.sendMouseMove(dx, dy)
        }

        // Continuous Air Mouse Toggle Button
        binding.btnToggleAirMotion.setOnClickListener {
            HapticHelper.heavyClick(it)
            isAirActive = !isAirActive
            if (isAirActive) {
                gyroLaserManager?.start()
                HapticHelper.startLaserHum(requireContext())
                binding.txtLaserStatus.text = "AIR MOUSE ACTIVE"
                binding.txtLaserStatus.setTextColor(0xFF38BDF8.toInt())
                binding.btnToggleAirMotion.text = "⏸ PAUSE AIR MOUSE"
                binding.btnToggleAirMotion.setBackgroundResource(R.drawable.glass_button_laser)
                binding.imgLaserDot.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start()
                startTimerIfNeeded()
            } else {
                gyroLaserManager?.stop()
                binding.txtLaserStatus.text = "AIR MOUSE PAUSED"
                binding.txtLaserStatus.setTextColor(0xFF94A3B8.toInt())
                binding.btnToggleAirMotion.text = "⚡ TOGGLE AIR MOUSE ON"
                binding.btnToggleAirMotion.setBackgroundResource(R.drawable.glass_button_primary)
                binding.imgLaserDot.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }

        // Momentary Hold Trigger
        binding.btnLaserHold.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isAirActive) {
                        HapticHelper.heavyClick(v)
                        HapticHelper.startLaserHum(requireContext())
                        gyroLaserManager?.start()
                        binding.txtLaserStatus.text = "LASER ACTIVE (HOLD)"
                        binding.txtLaserStatus.setTextColor(0xFF38BDF8.toInt())
                        binding.imgLaserDot.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start()
                        startTimerIfNeeded()
                    }
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isAirActive) {
                        HapticHelper.tick(v)
                        gyroLaserManager?.stop()
                        binding.txtLaserStatus.text = "AIR MOUSE PAUSED"
                        binding.txtLaserStatus.setTextColor(0xFF94A3B8.toInt())
                        binding.imgLaserDot.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }

        // Slide Navigation
        binding.btnNextSlide.setOnClickListener {
            HapticHelper.click(it)
            viewModel.nextSlide()
            startTimerIfNeeded()
        }

        binding.btnPrevSlide.setOnClickListener {
            HapticHelper.click(it)
            viewModel.prevSlide()
            startTimerIfNeeded()
        }

        // Presentation Shortcuts
        binding.btnF5Start.setOnClickListener {
            HapticHelper.click(it)
            viewModel.startPresentation()
            startTimerIfNeeded()
        }

        binding.btnBlackScreen.setOnClickListener {
            HapticHelper.click(it)
            viewModel.blackScreen()
        }

        binding.btnPptLaser.setOnClickListener {
            HapticHelper.click(it)
            viewModel.togglePowerPointLaser()
        }

        binding.btnEscExit.setOnClickListener {
            HapticHelper.click(it)
            viewModel.exitPresentation()
            pauseTimer()
        }

        // Timer Controls
        binding.btnResetTimer.setOnClickListener {
            HapticHelper.click(it)
            resetTimer()
        }
    }

    private fun startTimerIfNeeded() {
        if (!isTimerRunning) {
            isTimerRunning = true
            timerHandler.post(timerRunnable)
        }
    }

    private fun pauseTimer() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun resetTimer() {
        pauseTimer()
        timerSeconds = 0
        updateTimerDisplay()
    }

    private fun updateTimerDisplay() {
        val minutes = timerSeconds / 60
        val seconds = timerSeconds % 60
        binding.txtTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pauseTimer()
        gyroLaserManager?.stop()
        gyroLaserManager = null
    }
}

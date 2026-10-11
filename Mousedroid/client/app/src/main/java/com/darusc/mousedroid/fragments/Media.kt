package com.darusc.mousedroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.darusc.mousedroid.R
import com.darusc.mousedroid.databinding.FragmentMediaBinding
import com.darusc.mousedroid.mkinput.InputEvent
import com.darusc.mousedroid.networking.ConnectionManager
import com.darusc.mousedroid.utils.HapticHelper

/**
 * Modern Media Controller fragment with large glass playback and volume controls.
 */
class Media : Fragment() {

    private lateinit var binding: FragmentMediaBinding
    private val connectionManager = ConnectionManager.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_media, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Playback buttons
        binding.btnMediaPlayPause.setOnClickListener {
            HapticHelper.heavyClick(it)
            sendMediaAction(InputEvent.MediaAction.PLAY_PAUSE)
        }

        binding.btnMediaPrev.setOnClickListener {
            HapticHelper.click(it)
            sendMediaAction(InputEvent.MediaAction.PREVIOUS)
        }

        binding.btnMediaNext.setOnClickListener {
            HapticHelper.click(it)
            sendMediaAction(InputEvent.MediaAction.NEXT)
        }

        // Seek buttons
        binding.btnMediaReplay10.setOnClickListener {
            HapticHelper.click(it)
            sendMediaAction(InputEvent.MediaAction.REPLAY)
        }

        binding.btnMediaForward10.setOnClickListener {
            HapticHelper.click(it)
            sendMediaAction(InputEvent.MediaAction.FORWARD)
        }

        // Volume buttons
        binding.btnMediaVolDown.setOnClickListener {
            HapticHelper.click(it)
            sendMediaAction(InputEvent.MediaAction.VOLUME_DOWN)
        }

        binding.btnMediaVolUp.setOnClickListener {
            HapticHelper.click(it)
            sendMediaAction(InputEvent.MediaAction.VOLUME_UP)
        }

        binding.btnMediaVolOff.setOnClickListener {
            HapticHelper.heavyClick(it)
            sendMediaAction(InputEvent.MediaAction.VOLUME_MUTE)
        }
    }

    private fun sendMediaAction(action: InputEvent.MediaAction) {
        connectionManager.send(InputEvent.MediaEvent(action))
    }
}

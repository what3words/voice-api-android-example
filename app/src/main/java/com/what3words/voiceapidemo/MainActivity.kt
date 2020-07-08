package com.what3words.voiceapidemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import okio.ByteString

class MainActivity : AppCompatActivity(), VoiceApiListener {
    companion object {
        const val AUDIO_PERMISSION = 1002
        const val RECORDING_RATE = 44100
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val bufferSize = AudioRecord.getMinBufferSize(
        RECORDING_RATE, CHANNEL, FORMAT
    )

    private val voiceApi: VoiceApi by lazy {
        VoiceApi(this)
    }

    private val adapter: SuggestionsAdapter by lazy {
        SuggestionsAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listSuggestions.layoutManager = LinearLayoutManager(this)
        listSuggestions.adapter = adapter

        imgRecording.setOnClickListener {
            if (continueRecording) return@setOnClickListener
            //check RECORD_AUDIO permissions for android versions >= 23
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                handlePermissions()
            } else {
                startVoiceApi()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun handlePermissions() {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceApi()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
            }
        } catch (e: SecurityException) {
            Log.e("MainActivity", e.message)
        }
    }

    private var recorder: AudioRecord? = null
    private var continueRecording: Boolean = false

    private fun startVoiceApi() {
        voiceApi.open(RECORDING_RATE)
    }

    private fun stopAudio() {
        runOnUiThread { imgRecording.setImageResource(R.drawable.ic_not_recording) }
        continueRecording = false
        recorder?.stop()
        recorder?.release()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            AUDIO_PERMISSION -> {
                if (grantResults.count() > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    startVoiceApi()
                } else {
                    Snackbar.make(rootView, R.string.voice_permission, Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    override fun connected() {
        //update UI
        runOnUiThread {
            adapter.setData(emptyList())
            imgRecording.setImageResource(R.drawable.ic_recording)
        }
        //setup AudioRecord recording
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_RATE,
            CHANNEL,
            FORMAT,
            bufferSize
        ).also {
            continueRecording = true
            val buffer = ByteArray(bufferSize)
            //it.read is blocking so it should be run on a different thread, you can handle this with RX, coroutines, AsyncTask, etc.
            val background = Thread(Runnable {
                while (continueRecording) {
                    it.read(buffer, 0, buffer.size)
                    voiceApi.send(ByteString.of(*buffer))
                }
            })
            it.startRecording()
            background.start()
        }
    }

    override fun suggestions(suggestions: List<Suggestion>) {
        stopAudio()
        runOnUiThread {
            adapter.setData(suggestions)
        }
    }

    override fun error(message: String) {
        stopAudio()
        Log.e("MainActivity", message)
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
    }
}
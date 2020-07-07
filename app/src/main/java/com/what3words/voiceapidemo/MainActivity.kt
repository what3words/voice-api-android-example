package com.what3words.voiceapidemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.WebSocket
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

        //check RECORD_AUDIO permissions
        imgRecording.setOnClickListener {
            if (continueRecording) return@setOnClickListener
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
    }

    private var recorder: AudioRecord? = null
    private var continueRecording: Boolean = false

    private fun startVoiceApi() {
        voiceApi.open(RECORDING_RATE)
    }

    private fun stopAudio() {
        runOnUiThread { imgRecording.setImageResource(R.drawable.ic_not_recording) }
        continueRecording = false
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

    override fun connected(webSocket: WebSocket) {
        // recording and UI updates should be done on IO and UI threads, achieve this using coroutines, RX, asyncTask, etc.
        runOnUiThread {
            adapter.setData(emptyList())
            imgRecording.setImageResource(R.drawable.ic_recording)
        }
        val background = Thread(Runnable {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDING_RATE,
                CHANNEL,
                FORMAT,
                bufferSize
            ).also {
                continueRecording = true
                val buffer = ByteArray(bufferSize)
                it.startRecording()
                while (continueRecording) {
                    it.read(buffer, 0, buffer.size)
                    webSocket.send(ByteString.of(*buffer))
                }
            }
        })
        background.start()
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
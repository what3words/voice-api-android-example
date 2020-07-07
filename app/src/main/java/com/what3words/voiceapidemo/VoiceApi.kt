package com.what3words.voiceapidemo

import com.google.gson.Gson
import okhttp3.*
import org.json.JSONObject

/**
 * Implement this listener to receive the callbacks from VoiceApi
 */
interface VoiceApiListener {
    /**
     * When WebSocket successfully does the handshake with VoiceAPI
     */
    fun connected(webSocket: WebSocket)

    /**
     * When VoiceAPI receive the recording, processed it and retrieved what3word addresses
     */
    fun suggestions(suggestions: List<Suggestion>)

    /**
     * When there's an error with the VoiceAPI connection, please find all errors at: https://developer.what3words.com/voice-api/docs#error-handling
     */
    fun error(message: String)
}

/**
 * This class is a helper to use VoiceAPI with OkHttp3 WebSocket
 *
 * @param listener set the listener for this class (in this example MainActivity)
 */
class VoiceApi constructor(private val listener: VoiceApiListener? = null) {
    companion object {
        //TODO: please replace with your api key
        const val API_KEY = "9X327865"
        const val BASE_URL = "wss://voiceapi.what3words.com/v1/autosuggest"
    }

    private var socket: WebSocket? = null

    /**
     * open a WebSocket and communicate the parameters to Voice API
     * autoSuggest parameters are passed in the URL QueryString, and audio parameters are passed as a JSON message
     * @param sampleRate: the sample rate of the recording
     * @param encoding: the encoding of the audio, pcm_f32le (32 bit float little endian), pcm_s16le (16 bit signed int little endian), mulaw (8 bit mu-law encoding) supported
     * @param language: the two letter language code for the language being spoken
     * @param resultCount: The number of AutoSuggest results to return. A maximum of 100 results can be specified, if a number greater than this is requested, this will be truncated to the maximum. The default is 3
     * @param focusLat: This is a location, specified as a latitude (often where the user making the query is). If specified, the results will be weighted to give preference to those near the focus.
     * @param focusLong: This is a location, specified as a longitude is allowed to wrap around the 180 line, so 361 is equivalent to 1.
     * @param focusCount: Specifies the number of results (must be less than or equal to n-results) within the results set which will have a focus. Defaults to n-results. This allows you to run autosuggest with a mix of focussed and unfocussed results, to give you a "blend" of the two. This is exactly what the old V2 standarblend did, and standardblend behaviour can easily be replicated by passing n-focus-results=1, which will return just one focussed result and the rest unfocussed.
     * @param country: only show results for this country
     * @param circleCenterLat: Restrict autosuggest results to a circle, specified by focus
     * @param circleCenterLong: Restrict autosuggest results to a circle, specified by focus
     * @param circleCenterRadius: Restrict autosuggest results to a circle, specified by focus
     */
    fun open(
        sampleRate: Int,
        encoding: String = "pcm_s16le",
        language: String = "en",
        resultCount: Int = 8,
        focusLat: Double? = null,
        focusLong: Double? = null,
        focusCount: Int? = null,
        country: String? = null,
        circleCenterLat: Double? = null,
        circleCenterLong: Double? = null,
        circleCenterRadius: Double? = null
    ) {
        val url = createSocketUrl(
            language,
            resultCount,
            focusLat,
            focusLong,
            focusCount,
            country,
            circleCenterLat,
            circleCenterLong,
            circleCenterRadius
        )
        val request = Request.Builder().url(url).build()
        OkHttpClient().newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                val message = JSONObject(
                    mapOf(
                        "message" to "StartRecognition",
                        "audio_format" to mapOf(
                            "type" to "raw",
                            "encoding" to encoding,
                            "sample_rate" to sampleRate
                        )
                    )
                )
                webSocket.send(message.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                val message = Gson().fromJson(text, BaseVoiceMessagePayload::class.java)
                if (message.message == BaseVoiceMessagePayload.RecognitionStarted) {
                    listener?.connected(webSocket)
                }

                if (message.message == BaseVoiceMessagePayload.Suggestions) {
                    val result = Gson().fromJson(text, SuggestionsPayload::class.java)
                    listener?.suggestions(result.suggestions)
                    webSocket.close(1000, "JOB FINISHED")
                }

                if (message.code != null && message.message != null) {
                    listener?.error(message.message!!)
                    webSocket.close(1002, "JOB FINISHED WITH ERRORS")
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                super.onFailure(webSocket, t, response)
                t.message?.let { listener?.error(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                if (code != 1000) {
                    listener?.error(reason)
                    webSocket.close(code, reason)
                }
            }
        })
    }

    /**
    Helper to handle URL QueryString
     **/
    private fun createSocketUrl(
        language: String,
        resultCount: Int?,
        focusLat: Double?,
        focusLong: Double?,
        focusCount: Int?,
        country: String?,
        circleCenterLat: Double?,
        circleCenterLong: Double?,
        circleCenterRadius: Double?
    ): String {
        var url = BASE_URL
        url += "?key=$API_KEY"
        url += "&voice-language=$language"
        url += "&n-results=$resultCount"
        if (focusLat != null && focusLong != null) {
            url += "&focus=$focusLat,$focusLong"
            if (focusCount != null) {
                url += "&n-focus-results=$focusCount"
            }
        }
        if (country != null) url += "&clip-to-country=$country"
        if (circleCenterLat != null && circleCenterLong != null && circleCenterRadius != null) {
            url += "&clip-to-circle=$circleCenterLat,$circleCenterLong,$circleCenterRadius"
        }
        return url
    }
}

class SuggestionsPayload : BaseVoiceMessagePayload() {
    var suggestions: List<Suggestion> = emptyList()
}

open class BaseVoiceMessagePayload {
    companion object {
        const val RecognitionStarted = "RecognitionStarted"
        const val Suggestions = "Suggestions"
    }

    var message: String? = null
    var code: String? = null
    var id: String? = null
}

/**
Suggestion object return by VoiceAPI, more details https://developer.what3words.com/voice-api/docs
 **/
class Suggestion {
    val country: String? = null
    val nearestPlace: String? = null
    val words: String? = null
    val distanceToFocusKm = 0
    val rank = 0
    val language: String? = null
}
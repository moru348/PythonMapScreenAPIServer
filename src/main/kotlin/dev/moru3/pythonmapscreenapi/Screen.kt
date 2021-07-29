package dev.moru3.pythonmapscreenapi

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.moru3.pythonmapscreenapi.restful.RestfulAPI.Companion.screenIdToVideoId
import dev.moru3.pythonmapscreenapi.websocket.WebSocketHandler.Companion.sessions
import org.springframework.web.socket.TextMessage
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Screen(val fps: Int, val videoId: String, val screenId: String, val movie: List<List<List<Byte>>>) {
    val timer = Timer()
    var frame = 0
    var old = MutableList(movie.size) { listOf<Byte>() }
    val gson = Gson()
    var stop = false

    fun stop() {
        println("スクリーン番号 ${screenId}の処理を終了します。")
        stop = true
    }

    fun start() {
        println("スクリーン番号 ${screenId}の処理を開始します。")
        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                if(stop) { Thread.interrupted();return }
                if(frame>=movie.size) { frame = 0 }

                val result: MutableList<ScreenData> = mutableListOf()

                movie.forEachIndexed { mapIndex, data1 ->
                    val frameData = data1[frame]
                    if(old.getOrNull(frame)!=frameData) {
                        val temp = mutableListOf<Byte>()
                        var startX = 127
                        var startY = 127
                        var endX = 0
                        var endY = 0
                        frameData.forEachIndexed { index, byte ->
                            if(old.getOrNull(mapIndex)?.getOrNull(index)!=byte) {
                                (index%128).apply { takeIf{ startX>it }?.also{ startX=it };takeIf{ endX<it }?.also{ endX=it } }
                                (index/128).apply { takeIf{ startY>it }?.also{ startY=it };takeIf{ endY<it }?.also{ endY=it } }
                            }
                        }
                        for(x in startX..endX) { for(y in startY..endY) { temp+=frameData[x+y*127] } }
                        result+=ScreenData(temp, startX, startY, endX, endY, mapIndex)
                        old[mapIndex] = frameData
                    }
                }

                val message = TextMessage(gson.toJson(result))
                sendMessage(screenId, message)
                frame++
            }

            override fun cancel(): Boolean { return super.cancel().also { Thread.interrupted() } }
        }, 0, 1000/fps.toLong())
    }

    companion object {
        private val executor: ExecutorService = Executors.newSingleThreadExecutor()
        fun sendMessage(screenId: String, message: TextMessage) {
            executor.execute { sessions[screenId]?.also { try { it.sendMessage(message) } catch(_: Exception) {} } }
        }
    }
}

data class ScreenData(val data: List<Byte>, val start_x: Int, val start_y: Int, val end_x: Int, val end_y: Int, val index: Int)
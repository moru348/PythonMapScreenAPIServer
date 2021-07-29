package dev.moru3.pythonmapscreenapi

import dev.moru3.pythonmapscreenapi.Screen.Companion.sendMessage
import dev.moru3.pythonmapscreenapi.websocket.WebSocketConfig.Companion.tokens
import dev.moru3.pythonmapscreenapi.websocket.WebSocketHandler.Companion.sessions
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.socket.TextMessage
import java.util.*

@SpringBootApplication
class PythonMapScreenApiApplication

fun main(args: Array<String>) {
    runApplication<PythonMapScreenApiApplication>(*args)

    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            sessions.keys.forEach {
                sendMessage(it, TextMessage("生存確認"))
            }
        }
    }, 0, 10000)

    //デバッグ用
    args.map { it.split(":") }.filter { it.size >= 2 }.map(List<String>::toMutableList).forEach { list ->
        when (list.getOrNull(0) ?: return@forEach) {
            "token" -> {
                tokens[list.also { it.removeAt(0) }.joinToString(":")] = Calendar.getInstance().also { it.add(Calendar.DATE, 100) }.time
            }
        }
    }
}

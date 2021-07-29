package dev.moru3.pythonmapscreenapi.websocket

import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class WebSocketHandler: TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        println("${session.remoteAddress?.hostName} からの接続を許可しました。")
        val id: String = session.attributes["id"]?.toString()?: kotlin.run { session.close(CloseStatus.NO_STATUS_CODE);return }
        sessions[id] = session
        println("スクリーン番号 ${id}のスクリーンが追加されました。")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {

    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.attributes["id"].toString())
        println("スクリーン番号 ${session.attributes["id"].toString()}のスクリーンが削除されました。")
    }

    companion object {
        val sessions = mutableMapOf<String, WebSocketSession>()
    }
}
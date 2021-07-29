package dev.moru3.pythonmapscreenapi.websocket

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor
import java.lang.Exception
import java.util.*

@Configuration
@EnableWebSocket
class WebSocketConfig: WebSocketConfigurer {

    companion object { val tokens = mutableMapOf<String, Date>() }

    @Bean
    fun test(): org.springframework.web.socket.WebSocketHandler {
        return WebSocketHandler()
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        println("!!!!!")
        registry.addHandler(test(), "/api/websocket/rts/screen").addInterceptors(object: HttpSessionHandshakeInterceptor() {
            override fun beforeHandshake(request: ServerHttpRequest, response: ServerHttpResponse, wsHandler: org.springframework.web.socket.WebSocketHandler, attributes: MutableMap<String, Any>): Boolean {
                println("ハンドシェイク！")
                println("test")
                val params = mutableMapOf<String, String>()
                println("test1")
                request.uri.query.split("&").forEach { it.split("=").also { param -> params[param[0]] = param[1] } }
                println("test2")
                params["token"]?.also { token ->
                    tokens[token]?.also {
                        println("test3")
                        if(it.after(Date())) {
                            attributes["id"] = params["id"]?:return false
                            println("test4")
                            attributes["height"] = params["height"]?.toInt()?:return false
                            attributes["width"] = params["width"]?.toInt()?:return false
                            println("接続を許可しました。")
                            return true
                        } else {
                            tokens.remove(token)
                            return false
                        }
                    }?:return false
                }
                return false
            }
        })
    }
}
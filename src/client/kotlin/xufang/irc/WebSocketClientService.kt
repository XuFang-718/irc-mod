package xufang.irc

import com.google.gson.Gson
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory
import java.net.URI

class WebSocketClientService(private val config: IrcConfig) {
    private val logger = LoggerFactory.getLogger(WebSocketClientService::class.java)
    private val gson = Gson()
    private var client: IrcWebSocketClient? = null
    private val displayedMessageIds = mutableSetOf<Int>()
    
    fun connect() {
        try {
            val uri = URI(config.serverUrl)
            client = IrcWebSocketClient(uri)
            client?.setConnectionLostTimeout(config.connectionTimeoutMs / 1000)
            client?.connectBlocking()
            logger.info("WebSocket 连接成功: ${config.serverUrl}")
        } catch (e: Exception) {
            logger.warn("WebSocket 连接失败，将在 ${config.reconnectDelayMs}ms 后重试")
            scheduleReconnect()
        }
    }
    
    private fun scheduleReconnect() {
        Thread {
            try {
                Thread.sleep(config.reconnectDelayMs.toLong())
                if (client?.isOpen != true) {
                    logger.info("尝试重新连接...")
                    connect()
                }
            } catch (e: InterruptedException) {
                logger.debug("重连被中断")
            }
        }.start()
    }
    
    fun disconnect() {
        client?.close()
        client = null
    }
    
    fun sendMessage(playerName: String, message: String) {
        val data = mapOf(
            "type" to "send_message",
            "player_name" to playerName,
            "message" to message
        )
        val json = gson.toJson(data)
        client?.send(json)
    }
    
    fun isConnected(): Boolean = client?.isOpen == true
    
    inner class IrcWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {
        
        override fun onOpen(handshakedata: ServerHandshake?) {
            logger.info("WebSocket 已连接")
            val request = mapOf("type" to "get_history")
            send(gson.toJson(request))
        }
        
        override fun onMessage(message: String) {
            try {
                val data = gson.fromJson(message, Map::class.java)
                val type = data["type"] as? String ?: return
                
                when (type) {
                    "new_message" -> handleNewMessage(message)
                    "history" -> handleHistory(message)
                    "message_sent" -> logger.debug("消息已发送")
                    "error" -> logger.warn("服务器错误: ${data["message"]}")
                    "connected" -> logger.debug("连接确认")
                }
            } catch (e: Exception) {
                logger.error("处理消息失败", e)
            }
        }
        
        override fun onClose(code: Int, reason: String?, remote: Boolean) {
            logger.warn("WebSocket 已断开 (code: $code)")
            scheduleReconnect()
        }
        
        override fun onError(ex: Exception?) {
            logger.debug("WebSocket 错误: ${ex?.message}")
        }
        
        private fun handleNewMessage(json: String) {
            try {
                val data = gson.fromJson(json, Map::class.java)
                val id = (data["id"] as? Double)?.toInt() ?: return
                val playerName = data["player_name"] as? String ?: return
                val message = data["message"] as? String ?: return
                val timestamp = data["timestamp"] as? Double ?: 0.0
                
                val msg = IrcMessage(id, playerName, message, timestamp)
                if (!displayedMessageIds.contains(msg.id)) {
                    displayedMessageIds.add(msg.id)
                    displayMessage(msg)
                }
            } catch (e: Exception) {
                logger.error("解析消息失败", e)
            }
        }
        
        private fun handleHistory(json: String) {
            try {
                val data = gson.fromJson(json, Map::class.java)
                val messagesList = data["messages"] as? List<*> ?: return
                
                messagesList.forEach { msgData ->
                    val msgMap = msgData as? Map<*, *> ?: return@forEach
                    val id = (msgMap["id"] as? Double)?.toInt() ?: return@forEach
                    val playerName = msgMap["player_name"] as? String ?: return@forEach
                    val message = msgMap["message"] as? String ?: return@forEach
                    val timestamp = msgMap["timestamp"] as? Double ?: 0.0
                    
                    val msg = IrcMessage(id, playerName, message, timestamp)
                    if (!displayedMessageIds.contains(msg.id)) {
                        displayedMessageIds.add(msg.id)
                        displayMessage(msg)
                    }
                }
            } catch (e: Exception) {
                logger.error("解析历史消息失败", e)
            }
        }
        
        private fun displayMessage(msg: IrcMessage) {
            val minecraft = Minecraft.getInstance()
            minecraft.execute {
                try {
                    if (minecraft.player != null) {
                        val formatted = "§7[§birc§7] §f${msg.player_name}§7: §f${msg.message}"
                        minecraft.player?.displayClientMessage(Component.literal(formatted), false)
                    }
                } catch (e: Exception) {
                    logger.error("显示消息失败", e)
                }
            }
        }
        

    }
    
    data class IrcMessage(
        val id: Int,
        val player_name: String,
        val message: String,
        val timestamp: Double
    )
    
    data class HistoryResponse(
        val messages: List<IrcMessage>
    )
}

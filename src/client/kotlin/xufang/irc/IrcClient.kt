package xufang.irc

import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

object IrcClient : ClientModInitializer {
	private val logger = LoggerFactory.getLogger("irc-client")
	lateinit var config: IrcConfig
		private set
	
	private lateinit var wsClient: WebSocketClientService

	override fun onInitializeClient() {
		try {
			logger.info("初始化IRC客户端...")
			
			config = IrcConfig.load()
			
			wsClient = WebSocketClientService(config)
			wsClient.connect()
			
			Runtime.getRuntime().addShutdownHook(Thread {
				try {
					wsClient.disconnect()
					logger.info("IRC客户端已关闭")
				} catch (e: Exception) {
					logger.error("关闭失败", e)
				}
			})
			
			logger.info("IRC客户端初始化完成")
		} catch (e: Exception) {
			logger.error("初始化失败", e)
			throw e
		}
	}
	
	fun getWebSocketClient() = wsClient
}
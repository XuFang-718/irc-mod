package xufang.irc

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader

data class IrcConfig(
    val serverUrl: String = "ws://111.170.170.251:5001",
    val reconnectDelayMs: Int = 5000,
    val connectionTimeoutMs: Int = 15000,
    val maxRetryAttempts: Int = 3
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(IrcConfig::class.java)
        private const val CONFIG_FILE = "config/irc.json"
        private val gson = Gson()
        
        fun load(): IrcConfig {
            val file = File(CONFIG_FILE)
            return if (file.exists()) {
                try {
                    FileReader(file).use { reader ->
                        val config = gson.fromJson(reader, IrcConfig::class.java)
                        logger.info("配置已加载: 重连=${config.reconnectDelayMs}ms, 超时=${config.connectionTimeoutMs}ms")
                        config
                    }
                } catch (e: Exception) {
                    logger.warn("配置文件读取失败，使用默认配置: ${e.message}")
                    IrcConfig()
                }
            } else {
                logger.info("使用默认配置")
                IrcConfig()
            }
        }
    }
}

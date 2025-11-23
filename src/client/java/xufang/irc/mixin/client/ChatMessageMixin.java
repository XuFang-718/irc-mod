package xufang.irc.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xufang.irc.IrcClient;
import xufang.irc.WebSocketClientService;
import net.minecraft.network.chat.Component;

@Mixin(ClientPacketListener.class)
public class ChatMessageMixin {
    
    private static boolean ircModeEnabled = false;
    
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, CallbackInfo ci) {
        if (message == null) return;
        
        if (message.startsWith("##")) {
            ci.cancel();
            handleIrcCommand(message.substring(2));
            return;
        }
        
        boolean shouldSendToIrc = false;
        String ircMessage = null;
        
        if (ircModeEnabled && !message.startsWith("/")) {
            shouldSendToIrc = true;
            ircMessage = message;
        } else if (message.startsWith("#")) {
            shouldSendToIrc = true;
            ircMessage = message.substring(1);
        }
        
        if (shouldSendToIrc) {
            ci.cancel();
            
            if (ircMessage.trim().isEmpty()) {
                showMessage("§c[IRC] 消息不能为空", false);
                return;
            }
            
            if (ircMessage.length() > 256) {
                showMessage("§c[IRC] 消息过长 (最多256字符)", false);
                return;
            }
            
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                showMessage("§c[IRC] 无法获取玩家信息", false);
                return;
            }
            
            String playerName = minecraft.player.getName().getString();
            String finalIrcMessage = ircMessage;
            
            new Thread(() -> {
                try {
                    WebSocketClientService wsClient = IrcClient.INSTANCE.getWebSocketClient();
                    if (wsClient.isConnected()) {
                        wsClient.sendMessage(playerName, finalIrcMessage);
                    } else {
                        minecraft.execute(() -> showMessage("§c[IRC] 未连接到服务器", false));
                    }
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger("irc-client").error("发送IRC消息失败", e);
                    minecraft.execute(() -> showMessage("§c[IRC] 发送失败: " + e.getMessage(), false));
                }
            }, "IRC-Send-Thread").start();
        }
    }
    
    private void handleIrcCommand(String command) {
        String cmd = command.trim().toLowerCase();
        
        if (cmd.equals("chat on")) {
            ircModeEnabled = true;
            showMessage("§a[IRC] IRC模式已开启 - 所有消息将发送到IRC频道", false);
            showMessage("§7[IRC] 使用 §e##chat off §7关闭IRC模式", false);
        } else if (cmd.equals("chat off")) {
            ircModeEnabled = false;
            showMessage("§a[IRC] IRC模式已关闭 - 恢复正常聊天", false);
            showMessage("§7[IRC] 使用 §e# §7前缀发送单条IRC消息", false);
        } else if (cmd.equals("chat") || cmd.equals("chat status")) {
            if (ircModeEnabled) {
                showMessage("§e[IRC] 当前状态: §aIRC模式已开启", false);
                showMessage("§7[IRC] 使用 §e##chat off §7关闭IRC模式", false);
            } else {
                showMessage("§e[IRC] 当前状态: §7正常聊天模式", false);
                showMessage("§7[IRC] 使用 §e##chat on §7开启IRC模式", false);
            }
        } else {
            showMessage("§c[IRC] 未知命令: ##" + command, false);
            showMessage("§7[IRC] 可用命令:", false);
            showMessage("§7  - §e##chat on §7- 开启IRC模式", false);
            showMessage("§7  - §e##chat off §7- 关闭IRC模式", false);
            showMessage("§7  - §e##chat §7- 查看当前状态", false);
        }
    }
    
    
    private void showMessage(String message, boolean actionBar) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), actionBar);
        }
    }
}

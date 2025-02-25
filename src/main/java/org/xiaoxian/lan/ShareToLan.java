package org.xiaoxian.lan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.xiaoxian.gui.GuiShareToLanEdit;
import org.xiaoxian.util.ChatUtil;
import org.xiaoxian.util.NetworkUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.xiaoxian.EasyLAN.*;


public class ShareToLan {
    public static List<ServerPlayer> playerList;
    public static ExecutorService executorService;
    public static ScheduledExecutorService updateService;

    ApiLanStatus HttpApi = new ApiLanStatus();
    Integer HttpApiPort;
    static boolean isShared = false;

    public void handleStop() {
        if (isShared) {
            executorService.shutdownNow();
            if (HttpAPI) {
                updateService.shutdownNow();
                HttpApi.stop();
            }
        }
    }

    public void handleLanSetup() {
        executorService = Executors.newFixedThreadPool(2);

        /* 变量区~ */
        String fieldName = devMode ? "maxPlayers" : "f_11193_";
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        ServerConnectionListener networkSystem = server.getConnection();

        /* 判断是否自定义端口号 */
        if (!(GuiShareToLanEdit.PortTextBox.getValue().isEmpty())) {
            startLanPort(networkSystem, Integer.parseInt(GuiShareToLanEdit.PortTextBox.getValue()));
        }

        /* 判断是否自定义最大玩家数 */
        if (!(GuiShareToLanEdit.MaxPlayerBox.getValue().isEmpty())) {
            setMaxPlayer(fieldName, server);
        }

        /* 处理HttpAPI */
        if (HttpAPI) {
            updateService = Executors.newSingleThreadScheduledExecutor();
            startHttpApi(server);
        }

        /* 因为输出包含原版端口号，而只能在开放后进行获取，创建一个异步线程附加等待处理 */
        if (LanOutput) {
            sendLanInfo(server);
        }

        isShared = true;
    }

    private void sendLanInfo(IntegratedServer server) {
        executorService.submit(() -> {
            String lanIPv4 = NetworkUtil.getLocalIpv4();
            String lanIPv6 = NetworkUtil.getLocalIpv6();
            String publicIPv4 = NetworkUtil.getPublicIPv4();
            boolean checkIPv4 = NetworkUtil.checkIpIsPublic();
            String lanPort = getLanPort();

            ChatUtil.sendMsg("&e[&6EasyLAN&e] &aSuccessfully");
            ChatUtil.sendMsg("&4---------------------");
            ChatUtil.sendComponentMsg(Component.translatable("§e" + I18n.get("easylan.local") + "IPv4: §a").append(ComponentUtils.copyOnClickText(lanIPv4)));
            ChatUtil.sendComponentMsg(Component.translatable("§e" + I18n.get("easylan.local") + "IPv6: §a").append(ComponentUtils.copyOnClickText(lanIPv6)));
            ChatUtil.sendMsg(" ");
            ChatUtil.sendMsg("&e" + I18n.get("easylan.public") + "IPv4: &a" + publicIPv4);
            ChatUtil.sendMsg("&e" + I18n.get("easylan.chat.isPublic") + ": &a" + checkIPv4);
            ChatUtil.sendMsg(" ");
            ChatUtil.sendMsg("&e" + I18n.get("easylan.text.port") + ": &a" + lanPort);

            if (!(GuiShareToLanEdit.PortTextBox.getValue().isEmpty())) {
                ChatUtil.sendMsg("&e" + I18n.get("easylan.text.CtPort") + ": &a" + GuiShareToLanEdit.PortTextBox.getValue());
            }

            ChatUtil.sendMsg(" ");
            ChatUtil.sendMsg("&e" + I18n.get("easylan.text.maxplayer") + ": &a" + server.getMaxPlayers());
            ChatUtil.sendMsg("&e" + I18n.get("easylan.text.onlineMode") + ": &a" + onlineMode);

            if (HttpAPI) {
                ChatUtil.sendMsg(" ");
                ChatUtil.sendMsg("&eHttp-Api:&a true");
                ChatUtil.sendMsg("&eStatus:&a localhost:" + HttpApiPort + "/status");
                ChatUtil.sendMsg("&ePlayerList:&a localhost:" + HttpApiPort + "/playerlist");
            }
            ChatUtil.sendMsg("&4---------------------");
        });
    }

    private void startHttpApi(IntegratedServer server) {
        executorService.submit(() -> {
            System.out.println("[EasyLAN] Starting Thread!");

            updateApiInfo(server);

            try {
                HttpApiPort = HttpApi.start();
            } catch (IOException e) {
                System.out.println("[EasyLAN] HttpApi Start Error!" + e.getMessage());
            }
        });
    }

    /* 定时异步处理API */
    private void updateApiInfo(IntegratedServer server) {
        updateService.scheduleAtFixedRate(() -> {
            if (GuiShareToLanEdit.PortTextBox.getValue().isEmpty()) {
                HttpApi.set("port", String.valueOf(getLanPort()));
            } else {
                HttpApi.set("port", GuiShareToLanEdit.PortTextBox.getValue());
            }

            HttpApi.set("version", server.getServerVersion());
            HttpApi.set("owner", server.getSingleplayerProfile().getName());
            HttpApi.set("motd", server.getMotd());
            HttpApi.set("pvp", String.valueOf(allowPVP));
            HttpApi.set("onlineMode", String.valueOf(onlineMode));
            HttpApi.set("spawnAnimals", String.valueOf(spawnAnimals));
            HttpApi.set("allowFlight", String.valueOf(allowFlight));
            HttpApi.set("difficulty", String.valueOf(server.getWorldData().getDifficulty()));
            HttpApi.set("gameType", String.valueOf(server.getDefaultGameType()));
            HttpApi.set("maxPlayer", String.valueOf(server.getMaxPlayers()));
            HttpApi.set("onlinePlayer", String.valueOf(server.getPlayerCount()));

            playerList = server.getPlayerList().getPlayers();
            List<String> playerIDs = new ArrayList<>();
            for (ServerPlayer player : playerList) {
                playerIDs.add(player.getDisplayName().getString());
            }
            ApiLanStatus.playerIDs = playerIDs;

        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void setMaxPlayer(String fieldName, IntegratedServer server) {
        try {
            PlayerList playerList = new ServerStartingEvent(server).getServer().getPlayerList();
            Class<?> minecraftServerPlayerClass = Class.forName("net.minecraft.server.players.PlayerList");
            Field maxplayerField = minecraftServerPlayerClass.getDeclaredField(fieldName);
            maxplayerField.setAccessible(true);
            maxplayerField.set(playerList, Integer.parseInt(GuiShareToLanEdit.MaxPlayerBox.getValue()));
            if (!LanOutput) {
                ChatUtil.sendMsg("&e[&6EasyLAN&e] &a" + I18n.get("easylan.chat.CtPlayer") + " &f[&e" + GuiShareToLanEdit.MaxPlayerBox.getValue() + "&f]");
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            ChatUtil.sendMsg("&e[&6EasyLAN&e] &c" + I18n.get("easylan.chat.CtPlayerError"));
            System.out.println("[EasyLAN] setMaxPlayer Error: " + e.getMessage());
        }
    }

    private void startLanPort(ServerConnectionListener networkSystem, int port) {
        try {
            networkSystem.startTcpServerListener(InetAddress.getByName("0.0.0.0"), port);
            if (!LanOutput) {
                ChatUtil.sendMsg("&e[&6EasyLAN&e] &a" + I18n.get("easylan.chat.CtPort") + " &f[&e" + GuiShareToLanEdit.PortTextBox.getValue() + "&f]");
            }
        } catch (IOException e) {
            ChatUtil.sendMsg("&e[&6EasyLAN&e] &c" + I18n.get("easylan.chat.CtPortError"));
            System.out.println("[EasyLAN] addLanEndpoint Error: " + e.getMessage());
        }
    }

    public static String getLanPort() {
        String lastPort = null;
        try (BufferedReader reader = new BufferedReader(new FileReader("logs/latest.log"))) {
            String line;
            Pattern pattern = Pattern.compile("Started serving on ([0-9]*)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    lastPort = matcher.group(1);
                }
            }
        } catch (IOException e) {
            System.out.println("[EasyLAN] getLanPort Error: " + e.getMessage());
            return null;
        }
        return lastPort;
    }
}
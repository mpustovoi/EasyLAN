package org.xiaoxian.lan;

import fi.iki.elonen.NanoHTTPD;
import net.minecraft.entity.player.EntityPlayerMP;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.xiaoxian.lan.ShareToLan.playerList;

public class ApiLanStatus {

    public static SimpleHttpServer HttpServer;
    public static List<String> playerIDs = new ArrayList<>();

    private static final Map<String, String> data = new HashMap<>();

    public synchronized int start() throws IOException {
        if (HttpServer != null) {
            throw new IllegalStateException("[EasyLAN] HttpServer already started");
        }

        int port = 28960;
        while (isPortInUse(port)) {
            port++;
        }

        HttpServer = new SimpleHttpServer(port);
        HttpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("[EasyLAN] HttpAPI Server started on port " + port);
        return port;
    }

    public synchronized void stop() {
        if (HttpServer == null) {
            throw new IllegalStateException("[EasyLAN] Server not running");
        }

        HttpServer.stop();
        HttpServer = null;

        System.out.println("[EasyLAN] HttpApi Server stopped");
    }

    private boolean isPortInUse(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    public void set(String key, String value) {
        data.put(key, value);
    }

    private static class SimpleHttpServer extends NanoHTTPD {

        public SimpleHttpServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if ("/status".equals(uri)) {
                return handleStatus();
            } else if ("/playerlist".equals(uri)) {
                return handlePlayerList();
            } else {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "Not Found");
            }
        }

        private Response handleStatus() {
            Map<String, String> responseMap = new HashMap<>(data);
            Gson gson = new Gson();
            String jsonResponse = gson.toJson(responseMap);
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse);
        }

        private Response handlePlayerList() {
            playerIDs.clear();
            for (EntityPlayerMP player : playerList) {
                playerIDs.add(player.getDisplayName());
            }
            Gson gson = new Gson();
            String jsonResponse = gson.toJson(playerIDs);
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse);
        }
    }
}
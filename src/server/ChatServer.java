package server;

import common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * ChatServer listens for incoming TCP connections and spawns a ClientHandler
 * thread for each connected client.
 */
public class ChatServer {

    // ─── State ────────────────────────────────────────────────────────────────
    private final Map<String, ClientHandler>  clients    = new ConcurrentHashMap<>();
    private final Map<String, Set<String>>    rooms      = new ConcurrentHashMap<>(); // room → usernames
    private final Map<String, String>         userStatus = new ConcurrentHashMap<>(); // username → status
    private final ExecutorService             pool       = Executors.newCachedThreadPool();

    private ServerSocket  serverSocket;
    private boolean       running = false;
    private int           maxMsgSize = Protocol.MAX_MSG_SIZE;

    // GUI callback for log messages
    private Consumer<String> logCallback;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        // Default rooms
        rooms.put(Protocol.DEFAULT_ROOM, ConcurrentHashMap.newKeySet());
        rooms.put("Networks", ConcurrentHashMap.newKeySet());
        rooms.put("Java", ConcurrentHashMap.newKeySet());
        log("Server started on TCP port " + port);

        // Accept loop runs in background thread so GUI stays responsive
        pool.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    pool.submit(handler);
                    log("New connection from " + clientSocket.getInetAddress().getHostAddress());
                } catch (IOException e) {
                    if (running) log("Accept error: " + e.getMessage());
                }
            }
        });
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
        log("Server stopped.");
    }

    // ─── Client Management ────────────────────────────────────────────────────

    public synchronized boolean isUsernameTaken(String name) {
        return clients.containsKey(name);
    }

    public synchronized void registerClient(String name, ClientHandler handler) {
        clients.put(name, handler);
        userStatus.put(name, "ACTIVE");
        // Auto-join General room
        joinRoom(name, Protocol.DEFAULT_ROOM);
    }

    public synchronized void removeClient(String name) {
        clients.remove(name);
        userStatus.remove(name);
        // Remove from all rooms
        for (Map.Entry<String, Set<String>> entry : rooms.entrySet()) {
            if (entry.getValue().remove(name)) {
                broadcastToRoom(entry.getKey(), Protocol.R_USER_LEFT + " " + name, name);
            }
        }
    }

    public Set<String> getOnlineUsers() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    public String getUserStatus(String name) {
        return userStatus.getOrDefault(name, "ACTIVE");
    }

    public void setUserStatus(String name, String status) {
        userStatus.put(name, status);
    }

    public String getUserIP(String name) {
        ClientHandler h = clients.get(name);
        return h != null ? h.getIP() : "unknown";
    }

    // ─── Room Management ──────────────────────────────────────────────────────

    public synchronized void joinRoom(String username, String room) {
        rooms.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(username);
    }

    public synchronized void leaveRoom(String username, String room) {
        Set<String> members = rooms.get(room);
        if (members != null) members.remove(username);
    }

    public Set<String> getRooms() {
        return Collections.unmodifiableSet(rooms.keySet());
    }

    public int getRoomMemberCount(String room) {
        Set<String> members = rooms.get(room);
        return members == null ? 0 : members.size();
    }

    // ─── Messaging ────────────────────────────────────────────────────────────

    /** Sends message to all members of a room. excludeUser = null to include all. */
    public void broadcastToRoom(String room, String message, String excludeUser) {
        Set<String> members = rooms.get(room);
        if (members == null) return;
        for (String member : members) {
            if (!member.equals(excludeUser)) {
                ClientHandler h = clients.get(member);
                if (h != null) h.send(message);
            }
        }
    }

    /** Sends a server-wide broadcast to all connected clients. */
    public void broadcastAll(String message) {
        clients.values().forEach(h -> h.send(message));
    }

    /** Sends a private message. Returns false if target not found. */
    public boolean sendPrivate(String targetUsername, String message) {
        ClientHandler h = clients.get(targetUsername);
        if (h == null) return false;
        h.send(message);
        return true;
    }

    // ─── Admin ────────────────────────────────────────────────────────────────

    public void kickUser(String username) {
        ClientHandler h = clients.get(username);
        if (h != null) {
            h.send("400 KICKED");
            removeClient(username);
        }
    }

    public int getMaxMsgSize()              { return maxMsgSize; }
    public void setMaxMsgSize(int size)     { this.maxMsgSize = size; }
    public boolean isRunning()              { return running; }
    public Map<String, ClientHandler> getClients() { return Collections.unmodifiableMap(clients); }

    // ─── Logging ──────────────────────────────────────────────────────────────

    public void setLogCallback(Consumer<String> cb) { this.logCallback = cb; }

    public void log(String message) {
        String ts = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + ts + "] " + message;
        System.out.println(entry);
        if (logCallback != null) logCallback.accept(entry);
    }
}

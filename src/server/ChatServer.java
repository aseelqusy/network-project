package server;

import common.Protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ChatServer {

    private final Map<String, ClientHandler>  clients      = new ConcurrentHashMap<>();
    private final Map<String, Set<String>>    rooms        = new ConcurrentHashMap<>();
    private final Map<String, String>         userStatus   = new ConcurrentHashMap<>();

    private final Map<String, Integer>        sentCount    = new ConcurrentHashMap<>();
    private final Map<String, Integer>        receivedCount= new ConcurrentHashMap<>();

    private final ExecutorService             pool         = Executors.newCachedThreadPool();

    private ServerSocket  serverSocket;
    private boolean       running    = false;
    private int           maxMsgSize = Protocol.MAX_MSG_SIZE;

    private Consumer<String> logCallback;
    private final Map<String, String> registeredUsers = new ConcurrentHashMap<>();

    public void start(int port) throws IOException {
        start("0.0.0.0", port);
    }

    public void start(String host, int port) throws IOException {
        InetAddress bindAddress = InetAddress.getByName(host);
        serverSocket = new ServerSocket(port, 50, bindAddress);
        running = true;
        rooms.put(Protocol.DEFAULT_ROOM, ConcurrentHashMap.newKeySet());
        rooms.put("Networks", ConcurrentHashMap.newKeySet());
        rooms.put("Java", ConcurrentHashMap.newKeySet());
        log("Server started on " + bindAddress.getHostAddress() + ":" + port);

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
        broadcastAll(Protocol.R_SERVER_SHUTDOWN);
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
        log("Server stopped.");
    }

    public synchronized boolean isUsernameTaken(String name) {
        return clients.containsKey(name);
    }

    public synchronized void registerClient(String name, ClientHandler handler) {
        clients.put(name, handler);
        userStatus.put(name, "ACTIVE");
        sentCount.put(name, 0);
        receivedCount.put(name, 0);
        joinRoom(name, Protocol.DEFAULT_ROOM);
    }

    public synchronized void removeClient(String name) {
        clients.remove(name);
        userStatus.remove(name);
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

    public synchronized void joinRoom(String username, String room) {
        rooms.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(username);
    }

    public synchronized String getCurrentRoom(String username) {
        for (Map.Entry<String, Set<String>> entry : rooms.entrySet()) {
            if (entry.getValue().contains(username)) {
                return entry.getKey();
            }
        }
        return null;
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

    public void broadcastToRoom(String room, String message, String excludeUser) {
        Set<String> members = rooms.get(room);
        if (members == null) return;
        for (String member : members) {
            if (!member.equals(excludeUser)) {
                ClientHandler h = clients.get(member);
                if (h != null) {
                    h.send(message);
                    receivedCount.merge(member, 1, Integer::sum);
                }
            }
        }
    }

    public void broadcastAll(String message) {
        clients.values().forEach(h -> h.send(message));
    }

    public boolean sendPrivate(String targetUsername, String message) {
        ClientHandler h = clients.get(targetUsername);
        if (h == null) return false;
        h.send(message);
        receivedCount.merge(targetUsername, 1, Integer::sum);
        return true;
    }

    public void incrementSentCount(String username) {
        sentCount.merge(username, 1, Integer::sum);
    }

    public int getSentCount(String username) {
        return sentCount.getOrDefault(username, 0);
    }

    public int getReceivedCount(String username) {
        return receivedCount.getOrDefault(username, 0);
    }

    public void resetMessageCounters() {
        sentCount.replaceAll((k, v) -> 0);
        receivedCount.replaceAll((k, v) -> 0);
    }

    public boolean kickUser(String username) {
        ClientHandler h = clients.get(username);
        if (h == null) return false;
        h.kickByAdmin();
        log("Admin deleted user: " + username);
        return true;
    }

    public int getMaxMsgSize()          { return maxMsgSize; }
    public void setMaxMsgSize(int size) {
        this.maxMsgSize = size;
        broadcastAll(Protocol.R_MAX_MSG_SIZE + " " + size);
    }
    public boolean isRunning()          { return running; }
    public Map<String, ClientHandler> getClients() { return Collections.unmodifiableMap(clients); }

    public void setLogCallback(Consumer<String> cb) { this.logCallback = cb; }

    public void log(String message) {
        String ts = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + ts + "] " + message;
        System.out.println(entry);
        if (logCallback != null) logCallback.accept(entry);
    }
    public boolean registerUser(String username, String password) {
        if (username == null || username.isBlank()) return false;
        if (registeredUsers.containsKey(username)) return false;
        registeredUsers.put(username, password);
        return true;
    }

    public boolean isRegistered(String username) {
        return registeredUsers.containsKey(username);
    }

    public boolean validatePassword(String username, String password) {
        String stored = registeredUsers.get(username);
        if (stored == null) return true;
        return stored.equals(password);
    }

    public Set<String> getRegisteredUsers() {
        return Collections.unmodifiableSet(registeredUsers.keySet());
    }

    public boolean removeRegisteredUser(String username) {
        return registeredUsers.remove(username) != null;
    }
}
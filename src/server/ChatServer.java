package server;

import common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ChatServer {

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> userStatus = new ConcurrentHashMap<>();

    private final Map<String, Integer> sentCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> receivedCount = new ConcurrentHashMap<>();

    private final ExecutorService pool = Executors.newCachedThreadPool();

    private final Map<String, String> registeredUsers = new ConcurrentHashMap<>();

    private final Path dataDir = Paths.get("data");
    private final Path usersFile = dataDir.resolve("users.txt");
    private final Path messagesFile = dataDir.resolve("messages.txt");
    private final Object usersFileLock = new Object();
    private final Object messagesFileLock = new Object();

    private static final DateTimeFormatter HISTORY_TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ServerSocket serverSocket;
    private boolean running = false;
    private int maxMsgSize = Protocol.MAX_MSG_SIZE;

    private Consumer<String> logCallback;

    public void start(int port) throws IOException {
        start("0.0.0.0", port);
    }

    public void start(String host, int port) throws IOException {
        ensureDataDirectory();
        loadUsersFromFile();

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
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {
        }
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
        log("Server stopped.");
    }

    // Creates the data directory used by users.txt and messages.txt if missing.
    public void ensureDataDirectory() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            log("Failed to create data directory: " + e.getMessage());
        }
    }

    // Loads persisted users from data/users.txt into the in-memory registeredUsers map.
    public void loadUsersFromFile() {
        registeredUsers.clear();
        if (!Files.exists(usersFile)) {
            return;
        }

        synchronized (usersFileLock) {
            try (BufferedReader reader = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int idx = trimmed.indexOf(':');
                    if (idx < 0) {
                        registeredUsers.put(trimmed, "");
                    } else {
                        String username = trimmed.substring(0, idx).trim();
                        String password = trimmed.substring(idx + 1);
                        if (!username.isEmpty()) {
                            registeredUsers.put(username, password);
                        }
                    }
                }
                log("Loaded " + registeredUsers.size() + " user account(s) from " + usersFile + ".");
            } catch (IOException e) {
                log("Failed to load users: " + e.getMessage());
            }
        }
    }

    // Writes the in-memory registeredUsers map to data/users.txt atomically.
    public void saveUsersToFile() {
        ensureDataDirectory();
        synchronized (usersFileLock) {
            Path tempFile = usersFile.resolveSibling("users.txt.tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                List<String> usernames = new ArrayList<>(registeredUsers.keySet());
                Collections.sort(usernames);
                for (String username : usernames) {
                    String password = registeredUsers.getOrDefault(username, "");
                    writer.write(username + ":" + password);
                    writer.newLine();
                }
            } catch (IOException e) {
                log("Failed to save users: " + e.getMessage());
                return;
            }

            try {
                Files.move(tempFile, usersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException moveEx) {
                try {
                    Files.move(tempFile, usersFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException fallbackEx) {
                    log("Failed to finalize users file: " + fallbackEx.getMessage());
                    return;
                }
                log("Users file move warning: " + moveEx.getMessage());
            }
        }
    }

    // Appends one formatted history line to data/messages.txt.
    public void appendMessageToHistory(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        ensureDataDirectory();
        synchronized (messagesFileLock) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    messagesFile,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND)) {
                writer.write(line);
                writer.newLine();
            } catch (IOException e) {
                log("Failed to append history: " + e.getMessage());
            }
        }
    }

    // Loads all persisted message history lines from data/messages.txt.
    public List<String> loadMessageHistory() {
        if (!Files.exists(messagesFile)) {
            return Collections.emptyList();
        }

        synchronized (messagesFileLock) {
            try {
                return Files.readAllLines(messagesFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log("Failed to read message history: " + e.getMessage());
                return Collections.emptyList();
            }
        }
    }

    public String nowHistoryTimestamp() {
        return LocalDateTime.now().format(HISTORY_TS_FMT);
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

    public void broadcastServerMessage(String room, String message) {
        String roomName = (room == null || room.isBlank()) ? Protocol.DEFAULT_ROOM : room.trim();
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String payload = Protocol.R_MESSAGE + " " + roomName + " SERVER " + ts + " [BROADCAST] " + message;
        broadcastAll(payload);
        appendMessageToHistory("[" + nowHistoryTimestamp() + "] [BROADCAST] SERVER: " + message);
        log("BROADCAST " + message);
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

    public int getMaxMsgSize() {
        return maxMsgSize;
    }

    public void setMaxMsgSize(int size) {
        this.maxMsgSize = size;
        broadcastAll(Protocol.R_MAX_MSG_SIZE + " " + size);
    }

    public boolean isRunning() {
        return running;
    }

    public Map<String, ClientHandler> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    public void setLogCallback(Consumer<String> cb) {
        this.logCallback = cb;
    }

    public void log(String message) {
        String ts = LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + ts + "] " + message;
        System.out.println(entry);
        if (logCallback != null) logCallback.accept(entry);
    }

    public boolean registerUser(String username, String password) {
        if (username == null || username.isBlank()) return false;
        if (registeredUsers.containsKey(username)) return false;
        registeredUsers.put(username, password == null ? "" : password);
        saveUsersToFile();
        return true;
    }

    public boolean isRegistered(String username) {
        return registeredUsers.containsKey(username);
    }

    public boolean validatePassword(String username, String password) {
        String stored = registeredUsers.get(username);
        if (stored == null) return true;
        return stored.equals(password == null ? "" : password);
    }

    public Set<String> getRegisteredUsers() {
        return Collections.unmodifiableSet(registeredUsers.keySet());
    }

    public boolean removeRegisteredUser(String username) {
        boolean removed = registeredUsers.remove(username) != null;
        if (removed) {
            saveUsersToFile();
        }
        return removed;
    }
    public boolean deleteUser(String username) {
        ClientHandler h = clients.get(username);
        if (h == null) return false;
        h.deleteByAdmin();
        log("Admin deleted user: " + username);
        return true;
    }
}


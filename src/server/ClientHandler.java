package server;

import common.Protocol;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * ClientHandler runs in its own Thread for each connected client.
 * It reads commands line by line and delegates to ChatServer.
 */
public class ClientHandler implements Runnable {

    private final Socket           socket;
    private final ChatServer       server;
    private       BufferedReader   in;
    private       PrintWriter      out;
    private       String           username;
    private       String           currentStatus = "ACTIVE";
    private volatile boolean       disconnected  = false;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            String line;
            while ((line = in.readLine()) != null) {
                handleCommand(line.trim());
            }
        } catch (IOException e) {
            // client disconnected abruptly
        } finally {
            disconnect();
        }
    }

    // ─── Command Dispatcher ───────────────────────────────────────────────────

    private void handleCommand(String line) {
        if (line.isEmpty()) return;
        String[] parts = line.split(" ", 3);
        String cmd = parts[0].toUpperCase();

        switch (cmd) {
            case Protocol.HELLO  -> handleHello(parts);
            case Protocol.JOIN   -> handleJoin(parts);
            case Protocol.MSG    -> handleMsg(parts);
            case Protocol.PM     -> handlePm(parts);
            case Protocol.USERS  -> handleUsers();
            case Protocol.ROOMS  -> handleRooms();
            case Protocol.LEAVE  -> handleLeave(parts);
            case Protocol.STATUS -> handleStatus(parts);
            case Protocol.QUIT   -> handleQuit();
            default              -> send(Protocol.R_ERROR + ": Unknown command");
        }
    }

    // ─── Handlers ─────────────────────────────────────────────────────────────

    private void handleHello(String[] parts) {
        if (parts.length < 2) { send(Protocol.R_ERROR + ": Usage: HELLO <username>"); return; }
        String name = parts[1].trim();

        // Password is the third token if provided
        String password = parts.length >= 3 ? parts[2].trim() : "";

        if (server.isUsernameTaken(name)) {
            send(Protocol.R_NAME_TAKEN);
            return;
        }
        // If this user was pre-registered by admin, validate the password
        if (server.isRegistered(name) && !server.validatePassword(name, password)) {
            send(Protocol.R_AUTH_FAIL);
            return;
        }
        this.username = name;
        server.registerClient(name, this);
        send(Protocol.R_WELCOME);
        server.log("User connected: " + name + " from " + socket.getInetAddress().getHostAddress());
    }

    private void handleJoin(String[] parts) {
        if (!isLoggedIn()) return;
        if (parts.length < 2) { send(Protocol.R_ERROR + ": Usage: JOIN <room>"); return; }

        String targetRoom = parts[1].trim();
        if (targetRoom.isEmpty()) {
            send(Protocol.R_ERROR + ": Usage: JOIN <room>");
            return;
        }

        String previousRoom = server.getCurrentRoom(username);
        if (targetRoom.equals(previousRoom)) {
            // No-op join; keep response so client state remains consistent.
            send(Protocol.R_JOINED + " " + targetRoom);
            return;
        }

        if (previousRoom != null) {
            server.leaveRoom(username, previousRoom);
            server.broadcastToRoom(previousRoom, Protocol.R_USER_LEFT + " " + username, username);
            server.log("ROOM " + username + " left #" + previousRoom);
        }

        server.joinRoom(username, targetRoom);
        send(Protocol.R_JOINED + " " + targetRoom);
        server.broadcastToRoom(targetRoom, Protocol.R_USER_JOINED + " " + username, username);
        server.log("ROOM " + username + " joined #" + targetRoom);
    }

    private void handleMsg(String[] parts) {
        if (!isLoggedIn()) return;
        if (parts.length < 3) { send(Protocol.R_ERROR + ": Usage: MSG <room> <message>"); return; }
        String room    = parts[1].trim();
        String message = parts[2].trim();
        if (message.length() > server.getMaxMsgSize()) {
            send(Protocol.R_ERROR + ": Message too large"); return;
        }
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        server.broadcastToRoom(room,
                Protocol.R_MESSAGE + " " + room + " " + username + " " + timestamp + " " + message,
                null);
        send(Protocol.R_SENT);
        // FIX #4: record the sent message in the server counters
        server.incrementSentCount(username);
        server.log("MSG [" + room + "] " + username + ": " + message);
    }

    private void handlePm(String[] parts) {
        if (!isLoggedIn()) return;
        if (parts.length < 3) { send(Protocol.R_ERROR + ": Usage: PM <username> <message>"); return; }
        String target  = parts[1].trim();
        String message = parts[2].trim();
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        boolean sent = server.sendPrivate(target,
                Protocol.R_PRIVATE + " " + username + " " + timestamp + " " + message);
        send(sent ? Protocol.R_PRIVATE_SENT : Protocol.R_ERROR + ": User not found");
        if (sent) {
            // FIX #4: record the sent PM in the server counters
            server.incrementSentCount(username);
        }
        server.log("PM " + username + " → " + target + ": " + message);
    }

    private void handleUsers() {
        if (!isLoggedIn()) return;
        Set<String> users = server.getOnlineUsers();
        send(Protocol.R_USERS + " " + users.size());
        for (String u : users) {
            String status = server.getUserStatus(u);
            send(Protocol.R_USERS_ENTRY + " " + u + " " + status);
        }
        send(Protocol.R_USERS_END);
    }

    private void handleRooms() {
        if (!isLoggedIn()) return;
        Set<String> rooms = server.getRooms();
        for (String r : rooms) {
            int count = server.getRoomMemberCount(r);
            send(Protocol.R_ROOM + " " + r + " (" + count + ")");
        }
    }

    private void handleLeave(String[] parts) {
        if (!isLoggedIn()) return;
        if (parts.length < 2) { send(Protocol.R_ERROR + ": Usage: LEAVE <room>"); return; }
        String room = parts[1].trim();
        server.leaveRoom(username, room);
        send(Protocol.R_LEFT);
        server.broadcastToRoom(room, Protocol.R_USER_LEFT + " " + username, username);
        server.log("ROOM " + username + " left #" + room);
    }

    private void handleStatus(String[] parts) {
        if (!isLoggedIn()) return;
        if (parts.length < 2) return;
        currentStatus = parts[1].toUpperCase();
        server.setUserStatus(username, currentStatus);
        server.broadcastAll(Protocol.R_STATUS_UPDATE + " " + username + " " + currentStatus);
        server.log("STATUS " + username + " → " + currentStatus);
    }

    private void handleQuit() {
        send(Protocol.R_BYE);
        disconnect();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public void send(String message) {
        if (out != null) out.println(message);
    }

    public void kickByAdmin() {
        send(Protocol.R_KICKED);
        disconnect(false);
    }

    private boolean isLoggedIn() {
        if (username == null) { send(Protocol.R_ERROR + ": Not logged in. Use HELLO first."); return false; }
        return true;
    }

    private synchronized void disconnect(boolean logDisconnect) {
        if (disconnected) return;
        disconnected = true;

        if (username != null) {
            server.removeClient(username);
            if (logDisconnect) {
                server.log("User disconnected: " + username);
            }
        }
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    private void disconnect() {
        disconnect(true);
    }

    public String getUsername()     { return username; }
    public String getClientStatus() { return currentStatus; }
    public String getIP()           { return socket.getInetAddress().getHostAddress(); }
}

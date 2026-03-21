package client;

import common.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * ChatClient manages the TCP connection to ChatLite Server.
 * It runs a background thread to receive incoming messages
 * and exposes a simple API to send commands.
 */
public class ChatClient {

    private Socket       socket;
    private PrintWriter  out;
    private BufferedReader in;
    private boolean      connected = false;

    // Callback to pass received lines to the GUI
    private Consumer<String> messageCallback;
    private Runnable         disconnectCallback;

    public ChatClient(Consumer<String> messageCallback, Runnable disconnectCallback) {
        this.messageCallback    = messageCallback;
        this.disconnectCallback = disconnectCallback;
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    public void connect(String host, int port) throws IOException {
        socket    = new Socket(host, port);
        out       = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        in        = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;

        // Background thread listens for server messages
        Thread listener = new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    final String msg = line;
                    if (messageCallback != null) messageCallback.accept(msg);
                }
            } catch (IOException e) {
                if (connected) {
                    connected = false;
                    if (disconnectCallback != null) disconnectCallback.run();
                }
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    public void disconnect() {
        connected = false;
        sendCommand(Protocol.QUIT);
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    // ─── Command Helpers ──────────────────────────────────────────────────────

    /** Sends a raw command string to the server. */
    public void sendCommand(String command) {
        if (out != null) out.println(command);
    }

    public void login(String username) {
        sendCommand(Protocol.HELLO + " " + username);
    }

    public void joinRoom(String room) {
        sendCommand(Protocol.JOIN + " " + room);
    }

    public void sendMessage(String room, String message) {
        sendCommand(Protocol.MSG + " " + room + " " + message);
    }

    public void sendPrivate(String targetUser, String message) {
        sendCommand(Protocol.PM + " " + targetUser + " " + message);
    }

    public void requestUsers() {
        sendCommand(Protocol.USERS);
    }

    public void requestRooms() {
        sendCommand(Protocol.ROOMS);
    }

    public void leaveRoom(String room) {
        sendCommand(Protocol.LEAVE + " " + room);
    }

    public void setStatus(String status) {
        sendCommand(Protocol.STATUS + " " + status);
    }

    public boolean isConnected() { return connected; }
}

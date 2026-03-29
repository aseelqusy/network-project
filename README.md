# ChatLite — Minimal Real-Time Chat System

A multi-client real-time chat application built with Java Sockets and Swing GUI, developed as part of the Networks 1 course at An-Najah National University.

---

## Requirements

- Java JDK 11 or higher
- No external libraries required — pure Java SE

---

## Project Structure

```
ChatLite/
├── src/
│   ├── client/
│   │   ├── ChatClient.java       # TCP socket logic and protocol commands
│   │   └── ClientGUI.java        # Swing GUI for the chat client
│   ├── server/
│   │   ├── ChatServer.java       # Core server logic, rooms, messaging
│   │   ├── ClientHandler.java    # Per-client thread, protocol parser
│   │   └── ServerGUI.java        # Swing GUI for the server console
│   └── common/
│       └── Protocol.java         # Shared protocol constants
└── README.md
```

---

## How to Compile

Open a terminal in the project root directory and run:

```bash
# Create output directory
mkdir -p out

# Compile all source files
javac -d out src/common/Protocol.java \
             src/server/ChatServer.java \
             src/server/ClientHandler.java \
             src/server/ServerGUI.java \
             src/client/ChatClient.java \
             src/client/ClientGUI.java
```

Or compile everything at once:

```bash
find src -name "*.java" | xargs javac -d out
```

---

## How to Run

### 1. Start the Server

```bash
java -cp out server.ServerGUI
```

The server GUI will open and automatically start listening on **port 5000**.

> **Note:** The server binds to the IP address defined in `Protocol.SERVER_HOST`. If running locally, change this value in `src/common/Protocol.java` to `127.0.0.1` before compiling.

### 2. Start the Client

Open a second terminal and run:

```bash
java -cp out client.ClientGUI
```

A login dialog will appear. Enter a username (and password if the admin pre-registered your account) and click **Connect**.

You can launch multiple clients simultaneously by opening additional terminals and repeating this command.

---

## Default Configuration

| Setting        | Value              |
|----------------|--------------------|
| Server IP      | 10.250.162.140     |
| Server Port    | 5000               |
| Default Room   | General            |
| Max Msg Size   | 64 KB (65,536 B)   |

To run locally, change `SERVER_HOST` in `Protocol.java` to `127.0.0.1`.

---

## Sample Users for Testing

The server pre-registers the following accounts on startup:

| Username | Password |
|----------|----------|
| alice    | 1234     |
| bob      | 1234     |

Any other username can connect freely without a password unless registered by the admin.

---

## Available Chat Rooms

Three rooms are created automatically when the server starts:

- **General** (default room for all users)
- **Networks**
- **Java**

---

## Communication Protocol Summary

| Client Command         | Server Response       |
|------------------------|-----------------------|
| `HELLO <user> [pass]`  | `200 WELCOME`         |
| `JOIN <room>`          | `210 JOINED <room>`   |
| `MSG <room> <text>`    | `211 SENT`            |
| `PM <user> <text>`     | `212 PRIVATE SENT`    |
| `USERS`                | `213U` entries + `213 END` |
| `ROOMS`                | `214 <room>` entries  |
| `LEAVE <room>`         | `215 LEFT`            |
| `STATUS <state>`       | `216 STATUS` broadcast|
| `QUIT`                 | `221 BYE`             |

---

## Server Admin Features

From the Server Console GUI you can:

- **Create users** with optional passwords
- **Delete / kick** connected or pre-registered users
- **Broadcast** a message to all connected clients
- **View active sessions** with IP addresses and status
- **View message statistics** (sent / received / total per user)
- **Adjust max message size** (16 KB / 32 KB / 64 KB / 128 KB)
- **Save system logs** to a text file

---

## Client Features

- Join and switch between multiple chat rooms
- Send and receive private messages
- Change status: **Active**, **Busy**, or **Away**
- Search and filter chat history in real time
- Connection uptime display
- Kick countdown — if removed by admin, a 5-second dialog appears before returning to the login screen

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `Connection refused` | Make sure the server is running before starting the client |
| `Failed to start server` | The IP in `Protocol.SERVER_HOST` may not match your machine. Change it to `127.0.0.1` for local testing |
| Username taken | Choose a different username or wait for the previous session to disconnect |
| Auth failed | You are trying to connect with a pre-registered username and the wrong password |

---

## Course Information

- **Course:** Networks 1 (10636454)
- **Institution:** An-Najah National University — Faculty of Engineering
- **Instructor:** Dr. Eng. Saed TARAPIAH
- **Academic Year:** 2025–2026, Spring Semester

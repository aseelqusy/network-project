# ChatLite

A minimal real-time chat system built with Java sockets and Swing GUI. Supports multiple clients, chat rooms, private messaging, user authentication, and message history persistence.

---

## Requirements

- Java 11 or higher
- No external libraries required

---

## Project Structure

```
ChatLite/
├── src/
│   ├── common/
│   │   └── Protocol.java          # Shared constants and protocol codes
│   ├── server/
│   │   ├── ChatServer.java        # Core server logic
│   │   ├── ClientHandler.java     # Per-client connection handler
│   │   └── ServerGUI.java         # Server admin console (Swing)
│   └── client/
│       ├── ChatClient.java        # Socket-based client logic
│       └── ClientGUI.java         # Client chat interface (Swing)
└── data/
    ├── users.txt                  # Persisted user accounts (auto-created)
    └── messages.txt               # Persisted message history (auto-created)
```

---

## Configuration

Before running, open `src/common/Protocol.java` and set the server IP to match your machine:

```java
public static final String SERVER_HOST = "your.ip.address.here";
```

Use `localhost` if running both server and client on the same machine, or your LAN IP (e.g. `192.168.1.x`) if connecting across a network. The default port is `5000` and can also be changed in this file.

---

## Compiling

From the project root directory:

```bash
# Create output directory
mkdir -p out

# Compile all source files
javac -d out src/common/Protocol.java src/server/*.java src/client/*.java
```

---

## Running

**Start the server first:**

```bash
java -cp out server.ServerGUI
```

The server console window will open and the server will start listening on port 5000. Two sample users (`Ahmed` and `Ali`, password: `1234`) are created automatically on first run if no `data/users.txt` exists.

**Then start one or more clients:**

```bash
java -cp out client.ClientGUI
```

Each client instance opens a login dialog. Enter a username and password to connect.

---

## Default Test Accounts

| Username | Password |
|----------|----------|
| Ahmed    | 1234     |
| Ali      | 1234     |

New accounts are created automatically when a user logs in for the first time with a name that doesn't exist yet.

---

## Default Chat Rooms

| Room     | Description              |
|----------|--------------------------|
| General  | Default room for all users |
| Networks | Computer Networks topics |
| Java     | Java programming topics  |

---

## Features

- **Real-time messaging** across multiple chat rooms
- **Private messaging** between any two online users
- **User authentication** with password validation
- **Message history** replayed on login from persistent storage
- **User status** — set yourself as Active, Busy, or Away
- **Message search** — filter the chat area by keyword
- **Admin controls** — kick users, delete accounts, broadcast messages, reset message counters, set maximum message size
- **Uptime display** on both server and client

---

## Communication Protocol

All messages are plain text lines over TCP, terminated by a newline. Key commands:

| Client sends         | Server responds        | Meaning                        |
|----------------------|------------------------|--------------------------------|
| `HELLO <user> <pw>`  | `200 WELCOME`          | Login / register               |
| `JOIN <room>`        | `210 JOINED <room>`    | Join a chat room               |
| `MSG <room> <text>`  | `211 SENT`             | Send a room message            |
| `PM <user> <text>`   | `212 PRIVATE SENT`     | Send a private message         |
| `USERS`              | `213U` entries + END   | List online users              |
| `ROOMS`              | `214` entries          | List available rooms           |
| `LEAVE <room>`       | `215 LEFT`             | Leave a room                   |
| `STATUS <status>`    | broadcast to all       | Update your status             |
| `QUIT`               | `221 BYE`              | Disconnect                     |

Server-initiated messages include `400 KICKED`, `400 DELETED`, `400 SHUTDOWN`, and `HISTORY` lines sent on login.

---

## Data Persistence

User accounts are saved to `data/users.txt` in `username:password` format. This file is created automatically and updated whenever a user registers or is deleted.

Chat history is appended to `data/messages.txt` in real time. All connected clients receive this history when they log in.

Both files are created in the working directory from which the server is launched.

---

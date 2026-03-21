# ChatLite — Minimal Real-Time Chat System

A Java-based real-time chat system using TCP Sockets and Swing GUI.

---

## Project Structure

```
ChatLite/
└── src/
    ├── common/
    │   └── Protocol.java        ← Shared constants & command definitions
    ├── server/
    │   ├── ChatServer.java      ← Core server logic (multi-client, rooms, PM)
    │   ├── ClientHandler.java   ← Per-client thread handler
    │   └── ServerGUI.java       ← Server console GUI (entry point for server)
    └── client/
        ├── ChatClient.java      ← TCP socket + background listener
        └── ClientGUI.java       ← Client chat GUI (entry point for client)
```

---

## Requirements

- Java JDK 17 or higher
- IntelliJ IDEA (recommended) or any Java IDE

---

## How to Compile & Run in IntelliJ IDEA

### Step 1 — Open Project
1. Open IntelliJ IDEA
2. File → New → Project → Java (no build system needed)
3. Name it `ChatLite`
4. Create the packages `common`, `server`, `client` under `src/`
5. Copy each `.java` file into its matching package folder

### Step 2 — Run the Server
1. Open `server/ServerGUI.java`
2. Right-click → Run `ServerGUI.main()`
3. The server console will appear and start listening on **port 5000**

### Step 3 — Run the Client(s)
1. Open `client/ClientGUI.java`
2. Right-click → Run `ClientGUI.main()`
3. In the login dialog:
   - **Server IP**: `127.0.0.1` (or the server machine's IP on your network)
   - **Username**: any unique name (e.g., `Ahmed`)
4. Click **Connect**
5. Repeat from step 1 of this section to open a second client with a different username

---

## How to Compile from Command Line

```bash
# From the project root
mkdir -p out
javac -d out src/common/*.java src/server/*.java src/client/*.java

# Run Server
java -cp out server.ServerGUI

# Run Client (in a new terminal)
java -cp out client.ClientGUI
```

---

## Protocol Reference

| Command                    | Response              | Description                   |
|----------------------------|-----------------------|-------------------------------|
| `HELLO <username>`         | `200 WELCOME`         | Login / register username      |
| `JOIN <room>`              | `210 JOINED <room>`   | Join a chat room               |
| `MSG <room> <message>`     | `211 SENT`            | Send message to room           |
| `PM <username> <message>`  | `212 PRIVATE SENT`    | Send private message           |
| `USERS`                    | `213 <count>` + list  | List online users              |
| `ROOMS`                    | `214 <room>` × N      | List available rooms           |
| `LEAVE <room>`             | `215 LEFT`            | Leave a room                   |
| `STATUS <ACTIVE\|BUSY\|AWAY>` | —                  | Update user status             |
| `QUIT`                     | `221 BYE`             | Disconnect                     |

---

## Sample Users & Rooms for Testing

| Username | Status  |
|----------|---------|
| Ahmed    | Active  |
| Sara     | Busy    |
| Mohammed | Away    |
| Student1 | Active  |

| Room     | Description              |
|----------|--------------------------|
| General  | Default public room      |
| Networks | Networks course room     |
| Java     | Java programming room    |

---

## Authors

- Student 1: ………………………………
- Student 2: ………………………………
- Course: Networks1 (10636454) — An-Najah National University
- Instructor: Dr. Eng. Saed TARAPIAH
- Semester: Spring 2025-2026

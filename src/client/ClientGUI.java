package client;

import common.Protocol;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ClientGUI extends JFrame {

    // ── Palette (warm dark — easy on eyes) ───────────────────────────────────
    private static final Color BG_BASE      = new Color(28,  26,  24);
    private static final Color BG_PANEL     = new Color(36,  33,  30);
    private static final Color BG_SURFACE   = new Color(46,  42,  38);
    private static final Color BG_HIGHLIGHT = new Color(60,  54,  46);
    private static final Color BG_BUTTON    = new Color(72,  65,  54);
    private static final Color BG_BTN_SEND  = new Color(72, 108,  54);
    private static final Color ACCENT       = new Color(205, 165,  90);
    private static final Color TEXT_PRIMARY = new Color(228, 220, 208);
    private static final Color TEXT_MUTED   = new Color(145, 135, 118);
    private static final Color BORDER_CLR   = new Color(58,  52,  44);

    // ── Status colours ────────────────────────────────────────────────────────
    private static final Color STATUS_ACTIVE = new Color( 80, 200,  90);
    private static final Color STATUS_BUSY   = new Color(220, 180,  40);
    private static final Color STATUS_AWAY   = new Color(140, 138, 135);

    // ── State ─────────────────────────────────────────────────────────────────
    private ChatClient client;
    private String     myUsername  = "";
    private String     currentRoom = Protocol.DEFAULT_ROOM;

    private boolean collectingUsers = false;

    private final List<String> allChatLines = new ArrayList<>();

    private DefaultListModel<String> roomListModel = new DefaultListModel<>();
    private JList<String>            roomList      = new JList<>(roomListModel);
    private DefaultListModel<String> userListModel = new DefaultListModel<>();
    private JList<String>            userList      = new JList<>(userListModel);

    private JTextArea         chatArea;
    private JTextField        msgInput;
    private JTextField        searchBox;
    private JComboBox<String> pmTargetCombo = new JComboBox<>();
    private JTextArea         pmArea        = new JTextArea(4, 16);
    private JTextField        pmInput       = new JTextField();
    private JLabel            statusBar;
    private JLabel            connLabel;
    private JLabel            lblUptime;
    private long              connectionStartTime;
    private Timer             uptimeTimer;
    private boolean serverShutdown = false;
    private int serverMaxMsgSize = Protocol.MAX_MSG_SIZE;

    public ClientGUI() {
        super("ChatLite Client");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(920, 590);
        setMinimumSize(new Dimension(720, 460));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_BASE);
        setLayout(new BorderLayout(0, 0));

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                resetUptime();
                if (client != null && client.isConnected()) client.disconnect();
                System.exit(0);
            }
        });

        uptimeTimer = new Timer(1000, e -> updateUptime());
        uptimeTimer.start();

        SwingUtilities.invokeLater(this::showLoginDialog);
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 6));
        p.setBackground(new Color(22, 20, 18));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR));

        JLabel title = new JLabel("ChatLite");
        title.setForeground(ACCENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));

        connLabel = new JLabel("  |  not connected");
        connLabel.setForeground(TEXT_MUTED);
        connLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));

        lblUptime = new JLabel("Uptime: 00:00:00");
        lblUptime.setForeground(TEXT_MUTED);
        lblUptime.setFont(new Font("Monospaced", Font.PLAIN, 11));

        JLabel stLbl = new JLabel("Status:");
        stLbl.setForeground(TEXT_MUTED);
        stLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JComboBox<String> stCombo = styledCombo(new String[]{"ACTIVE","BUSY","AWAY"});
        stCombo.setForeground(Color.BLACK);
        stCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setForeground(Color.BLACK);
                return label;
            }
        });
        stCombo.addActionListener(e -> {
            if (client != null && client.isConnected())
                client.setStatus((String) stCombo.getSelectedItem());
        });

        p.add(title); p.add(connLabel);
        p.add(Box.createHorizontalStrut(8));
        p.add(lblUptime);
        p.add(Box.createHorizontalStrut(16));
        p.add(stLbl); p.add(stCombo);
        return p;
    }

    private JSplitPane buildCenter() {
        JSplitPane left = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildCenterPanel());
        left.setDividerLocation(172);
        left.setDividerSize(3);
        left.setBorder(null);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                left, buildRightPanel());
        main.setDividerLocation(660);
        main.setDividerSize(3);
        main.setBorder(null);
        return main;
    }

    private JPanel buildLeftPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

        roomListModel.addElement("General");
        roomListModel.addElement("Networks");
        roomListModel.addElement("Java");
        styleList(roomList);
        roomList.setCellRenderer(new RoomRenderer());
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && roomList.getSelectedValue() != null)
                switchRoom(roomList.getSelectedValue());
        });

        
        styleList(userList);
        userList.setCellRenderer(new UserRenderer());

        JScrollPane roomSP = styledScroll(roomList, "CHAT ROOMS");
        JScrollPane userSP = styledScroll(userList, "ONLINE USERS");

        JButton btnRefresh = styledBtn("↺  Refresh", BG_BUTTON);
        btnRefresh.addActionListener(e -> {
            if (client != null && client.isConnected()) {
                clearUserList();
                client.requestUsers();
                client.requestRooms();
            }
        });

        p.add(roomSP,      BorderLayout.NORTH);
        p.add(userSP,      BorderLayout.CENTER);
        p.add(btnRefresh,  BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildCenterPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(BG_BASE);
        p.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(22, 20, 18));
        chatArea.setForeground(TEXT_PRIMARY);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane chatSP = new JScrollPane(chatArea);
        chatSP.setBorder(titledBorder("MESSAGES"));
        chatSP.getViewport().setBackground(new Color(22, 20, 18));

        searchBox = styledField("Search messages...");
        searchBox.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { filterChat(); }
            @Override public void removeUpdate(DocumentEvent e)  { filterChat(); }
            @Override public void changedUpdate(DocumentEvent e) { filterChat(); }
        });

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setBackground(BG_BASE);
        JLabel sLbl = new JLabel("  Search: ");
        sLbl.setForeground(TEXT_MUTED);
        sLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        searchRow.add(sLbl,       BorderLayout.WEST);
        searchRow.add(searchBox,  BorderLayout.CENTER);

        msgInput = styledField("Type a message and press Enter...");
        msgInput.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendMessage();
            }
        });

        JButton btnSend = styledBtn("SEND", BG_BTN_SEND);
        btnSend.setForeground(new Color(225, 238, 210));
        btnSend.setPreferredSize(new Dimension(68, 32));
        btnSend.addActionListener(e -> sendMessage());

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(BG_BASE);
        inputRow.add(msgInput, BorderLayout.CENTER);
        inputRow.add(btnSend,  BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBackground(BG_BASE);
        bottom.add(inputRow,  BorderLayout.CENTER);
        bottom.add(searchRow, BorderLayout.SOUTH);

        p.add(chatSP,  BorderLayout.CENTER);
        p.add(bottom,  BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
        p.setPreferredSize(new Dimension(205, 0));

        pmArea.setEditable(false);
        pmArea.setBackground(new Color(22, 20, 18));
        pmArea.setForeground(TEXT_PRIMARY);
        pmArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        pmArea.setLineWrap(true);
        pmArea.setWrapStyleWord(true);
        pmArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane pmSP = new JScrollPane(pmArea);
        pmSP.setBorder(titledBorder("PRIVATE MSG"));
        pmSP.getViewport().setBackground(new Color(22, 20, 18));

        pmTargetCombo = styledCombo(new String[]{});
        pmTargetCombo.setForeground(Color.BLACK);
        pmTargetCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setForeground(Color.BLACK);
                return label;
            }
        });
        JLabel toLbl = new JLabel("To:");
        toLbl.setForeground(TEXT_MUTED);
        toLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));

        JPanel toRow = new JPanel(new BorderLayout(6, 0));
        toRow.setBackground(BG_PANEL);
        toRow.add(toLbl, BorderLayout.WEST);
        toRow.add(pmTargetCombo, BorderLayout.CENTER);

        pmInput = styledField("Private message...");
        pmInput.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendPrivate();
            }
        });

        JButton btnSend  = styledBtn("SEND",  BG_BTN_SEND);
        JButton btnClear = styledBtn("CLEAR", BG_BUTTON);
        btnSend.setForeground(new Color(225, 238, 210));
        btnSend.addActionListener(e  -> sendPrivate());
        btnClear.addActionListener(e -> pmArea.setText(""));

        JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
        btns.setBackground(BG_PANEL);
        btns.add(btnSend); btns.add(btnClear);

        JPanel pmBottom = new JPanel(new BorderLayout(0, 4));
        pmBottom.setBackground(BG_PANEL);
        pmBottom.add(toRow,   BorderLayout.NORTH);
        pmBottom.add(pmInput, BorderLayout.CENTER);
        pmBottom.add(btns,    BorderLayout.SOUTH);

        p.add(pmSP,    BorderLayout.CENTER);
        p.add(pmBottom, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(20, 18, 16));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_CLR));

        statusBar = new JLabel("   Not connected");
        statusBar.setForeground(TEXT_MUTED);
        statusBar.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusBar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JLabel dots = new JLabel("● ● ●   ");
        dots.setForeground(new Color(62, 56, 48));
        p.add(statusBar, BorderLayout.CENTER);
        p.add(dots,      BorderLayout.EAST);
        return p;
    }

    // ─── Login Dialog ─────────────────────────────────────────────────────────

    private void showLoginDialog() {
        JDialog d = new JDialog(this, "Connect", true);
        d.setSize(340, 240);
        d.setLocationRelativeTo(this);
        d.getContentPane().setBackground(BG_PANEL);
        d.setLayout(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 12, 6, 12);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("ChatLite — Connect");
        title.setForeground(ACCENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        gc.gridx=0; gc.gridy=0; gc.gridwidth=2; d.add(title, gc);

        JTextField tfUser = styledField("Username");

        gc.gridwidth=1; gc.gridy=1; gc.gridx=0; d.add(muted("Username:"), gc);
        gc.gridx=1; d.add(tfUser, gc);

        // ── Password field ────────────────────────────────────────────────────
        JPasswordField tfPassword = new JPasswordField();
        tfPassword.setBackground(BG_SURFACE);
        tfPassword.setForeground(TEXT_PRIMARY);
        tfPassword.setCaretColor(ACCENT);
        tfPassword.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tfPassword.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CLR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        gc.gridy=2; gc.gridx=0; d.add(muted("Password:"), gc);
        gc.gridx=1; d.add(tfPassword, gc);
        // ─────────────────────────────────────────────────────────────────────

        JButton btnConn = styledBtn("  Connect  ", BG_BTN_SEND);
        btnConn.setForeground(new Color(225, 238, 210));
        gc.gridy=3; gc.gridx=0; gc.gridwidth=2; d.add(btnConn, gc);

        btnConn.addActionListener(e -> {
            String host = Protocol.SERVER_HOST;
            String user = tfUser.getText().trim();

            Object phUser = tfUser.getClientProperty("placeholder");
            if (phUser != null && user.equals(phUser.toString())) user = "";


            if (user.isEmpty()) {
                JOptionPane.showMessageDialog(d,
                        "Please enter a username.", "Missing input", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                client = new ChatClient(this::onServerMessage, this::onDisconnect);
                client.connect(host, Protocol.SERVER_PORT);
                myUsername = user;
                // ── Send username + password to server ────────────────────
                String password = new String(tfPassword.getPassword());
                client.login(user, password);
                // ─────────────────────────────────────────────────────────
                setTitle("ChatLite  —  " + user);
                connLabel.setText("  |  " + host + ":" + Protocol.SERVER_PORT);
                log("Connected as " + user);
                Timer t = new Timer(500, ev -> { client.requestRooms(); client.requestUsers(); });
                t.setRepeats(false); t.start();
                d.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(d,
                        "Connection failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        tfUser.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) btnConn.doClick();
            }
        });
        d.setVisible(true);
    }

    // ─── Server Message Processing ────────────────────────────────────────────

    private void onServerMessage(String line) {
        SwingUtilities.invokeLater(() -> process(line));
    }

    private void process(String line) {
        if (line.equals(Protocol.R_SERVER_SHUTDOWN)) {
            serverShutdown = true;
            if (client != null && client.isConnected()) client.disconnect();
            JOptionPane.showMessageDialog(this,
                    "The server has been shut down by the administrator.\nThis window will now close.",
                    "Server Closed",
                    JOptionPane.WARNING_MESSAGE);
            dispose();
            return;
        }
        if (line.equals(Protocol.R_KICKED)) {
            if (client != null && client.isConnected()) client.disconnect();
            showKickCountdown();
            return;
        }

        if (line.startsWith(Protocol.R_MESSAGE)) {
            // MSG <room> <username> <timestamp> <text>
            String[] p = line.split(" ", 5);
            if (p.length >= 5)
                appendChat(p[2], p[3], p[4]);

        } else if (line.startsWith(Protocol.R_PRIVATE)) {
            // PM <from> <timestamp> <text>
            String[] p = line.split(" ", 4);
            if (p.length >= 4) {
                appendChat("PM:" + p[1], p[2], p[3]);
                pmArea.append("[" + p[2] + "] " + p[1] + ": " + p[3] + "\n");
            }

        } else if (line.startsWith(Protocol.R_USERS)) {
            if (line.equals(Protocol.R_USERS_END)) {
                collectingUsers = false;
            } else if (line.startsWith(Protocol.R_USERS_ENTRY)) {
                String[] p = line.split(" ", 3);
                if (p.length >= 2) {
                    String user   = p[1];
                    String status = p.length >= 3 ? p[2] : "ACTIVE";
                    String entry  = status.toUpperCase() + "|" + user;
                    userListModel.addElement(entry);
                    pmTargetCombo.addItem(user);
                }
            } else {
                clearUserList();
                collectingUsers = true;
            }

        } else if (line.startsWith(Protocol.R_ROOM)) {
            String room = line.substring(4).trim().split(" ")[0];
            if (!listContains(roomListModel, room))
                roomListModel.addElement(room);

        } else if (line.startsWith(Protocol.R_USER_JOINED)) {
            String u = line.split(" ").length > 1 ? line.split(" ")[1] : "?";
            appendSystem("→ " + u + " joined");

        } else if (line.startsWith(Protocol.R_USER_LEFT)) {
            String u = line.split(" ").length > 1 ? line.split(" ")[1] : "?";
            appendSystem("← " + u + " left");

        } else if (line.equals(Protocol.R_WELCOME)) {
            connectionStartTime = System.currentTimeMillis();
            updateUptime();
            appendSystem("Welcome, " + myUsername + "! You are in #" + currentRoom);

        } else if (line.equals(Protocol.R_NAME_TAKEN)) {
            JOptionPane.showMessageDialog(this,
                    "Username already taken!", "Error", JOptionPane.ERROR_MESSAGE);

        } else if (line.startsWith(Protocol.R_MAX_MSG_SIZE)) {
            String[] p = line.split(" ");
            if (p.length >= 3) {
                try {
                    serverMaxMsgSize = Integer.parseInt(p[2]);
                } catch (NumberFormatException ignored) {
                    serverMaxMsgSize = Protocol.MAX_MSG_SIZE;
                }
            }

        } else if (line.startsWith(Protocol.R_ERROR) && line.contains("Message too large")) {
            JOptionPane.showMessageDialog(this,
                    "Message is too large. Max allowed is " + serverMaxMsgSize + " characters.",
                    "Message Too Large", JOptionPane.WARNING_MESSAGE);

        } else if (line.startsWith(Protocol.R_STATUS_UPDATE)) {
            // "216 STATUS <username> <newStatus>"
            String[] p = line.split(" ", 4);
            if (p.length >= 4) {
                String user      = p[2];
                String newStatus = p[3].toUpperCase();
                updateUserStatus(user, newStatus);
            }

            // ── Wrong password response from server ───────────────────────────
        } else if (line.equals(Protocol.R_AUTH_FAIL)) {
            JOptionPane.showMessageDialog(this,
                    "Incorrect password for this username.",
                    "Auth Failed", JOptionPane.ERROR_MESSAGE);
        }
        // ─────────────────────────────────────────────────────────────────
    }
    private void showKickCountdown() {
        JDialog d = new JDialog(this, "Kicked by Admin", true);
        d.setSize(360, 200);
        d.setLocationRelativeTo(this);
        d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        d.getContentPane().setBackground(new Color(40, 20, 20));
        d.setLayout(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(10, 20, 6, 20);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridwidth = 1;

        JLabel iconLbl = new JLabel("⛔  You have been kicked by the admin", JLabel.CENTER);
        iconLbl.setForeground(new Color(220, 80, 60));
        iconLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        gc.gridx = 0; gc.gridy = 0;
        d.add(iconLbl, gc);

        JLabel msgLbl = new JLabel("You will be returned to login in:", JLabel.CENTER);
        msgLbl.setForeground(new Color(200, 190, 175));
        msgLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gc.gridy = 1;
        d.add(msgLbl, gc);

        JLabel countLbl = new JLabel("5", JLabel.CENTER);
        countLbl.setForeground(new Color(205, 165, 90));
        countLbl.setFont(new Font("Monospaced", Font.BOLD, 40));
        gc.gridy = 2;
        d.add(countLbl, gc);

        JLabel secLbl = new JLabel("seconds", JLabel.CENTER);
        secLbl.setForeground(new Color(145, 135, 118));
        secLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        gc.gridy = 3;
        d.add(secLbl, gc);

        int[] countdown = {5};
        Timer t = new Timer(1000, null);
        t.addActionListener(e -> {
            countdown[0]--;
            countLbl.setText(String.valueOf(countdown[0]));
            if (countdown[0] <= 0) {
                t.stop();
                d.dispose();
                resetAfterKick();
            }
        });
        t.start();

        d.setVisible(true);
    }

    private void resetAfterKick() {
        // Reset all state
        myUsername  = "";
        currentRoom = Protocol.DEFAULT_ROOM;
        client      = null;
        serverMaxMsgSize = Protocol.MAX_MSG_SIZE;

        // Clear UI
        chatArea.setText("");
        allChatLines.clear();
        userListModel.clear();
        roomListModel.clear();
        roomListModel.addElement("General");
        roomListModel.addElement("Networks");
        roomListModel.addElement("Java");
        pmArea.setText("");
        pmTargetCombo.removeAllItems();
        connLabel.setText("  |  not connected");
        resetUptime();
        setTitle("ChatLite Client");
        log("Disconnected — kicked by admin.");

        // Re-open login dialog
        showLoginDialog();
    }

    private void onDisconnect() {
        SwingUtilities.invokeLater(() -> {
            if (serverShutdown) return;
            resetUptime();
            log("Connection lost.");
            JOptionPane.showMessageDialog(this,
                    "Disconnected from server.", "Disconnected", JOptionPane.WARNING_MESSAGE);
        });
    }

    private void updateUptime() {
        if (lblUptime == null) return;
        if (connectionStartTime == 0) {
            lblUptime.setText("Uptime: 00:00:00");
            return;
        }
        long s = (System.currentTimeMillis() - connectionStartTime) / 1000;
        lblUptime.setText(String.format("Uptime: %02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60));
    }

    private void resetUptime() {
        connectionStartTime = 0;
        updateUptime();
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = msgInput.getText().trim();
        Object phObj = msgInput.getClientProperty("placeholder");
        if (phObj != null) {
            String ph = phObj.toString();
            if (text.equals(ph) || text.isEmpty()) return;
        } else {
            if (text.isEmpty()) return;
        }
        if (client == null || !client.isConnected()) return;
        if (text.length() > serverMaxMsgSize) {
            JOptionPane.showMessageDialog(this,
                    "Message is too large. Max allowed is " + serverMaxMsgSize + " characters.",
                    "Message Too Large", JOptionPane.WARNING_MESSAGE);
            return;
        }
        client.sendMessage(currentRoom, text);
        msgInput.setText("");
    }

    private void sendPrivate() {
        String target = (String) pmTargetCombo.getSelectedItem();
        String text   = pmInput.getText().trim();
        Object phObj = pmInput.getClientProperty("placeholder");
        if (phObj != null) {
            String ph = phObj.toString();
            if (text.equals(ph) || text.isEmpty()) return;
        } else {
            if (text.isEmpty()) return;
        }
        if (target == null || client == null) return;
        client.sendPrivate(target, text);
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        pmArea.append("[" + ts + "] Me → " + target + ": " + text + "\n");
        pmInput.setText("");
    }

    private void switchRoom(String room) {
        if (!room.equals(currentRoom)) {
            if (client != null && client.isConnected()) client.joinRoom(room);
            currentRoom = room;
            appendSystem("─── Switched to #" + room + " ───");
        }
    }

    private void appendChat(String user, String time, String msg) {
        String line = "[" + time + "] " + user + ": " + msg;
        allChatLines.add(line);
        filterChat();
    }

    private void appendSystem(String text) {
        String line = "  " + text;
        allChatLines.add(line);
        filterChat();
    }

    private void filterChat() {
        String query = searchBox.getText().trim();
        Object placeholder = searchBox.getClientProperty("placeholder");
        if (placeholder != null && query.equals(placeholder.toString())) {
            query = "";
        }
        query = query.toLowerCase();

        StringBuilder sb = new StringBuilder();
        for (String line : allChatLines) {
            if (query.isEmpty() || line.toLowerCase().contains(query)) {
                sb.append(line).append("\n");
            }
        }
        chatArea.setText(sb.toString());
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void log(String text) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        statusBar.setText("   [" + ts + "] " + text);
    }

    private void clearUserList() {
        userListModel.clear();
        pmTargetCombo.removeAllItems();
    }

    private void removeUserFromList(String username) {
        for (int i = 0; i < userListModel.size(); i++) {
            if (userListModel.get(i).contains("|" + username)) {
                userListModel.remove(i);
                break;
            }
        }
        for (int i = 0; i < pmTargetCombo.getItemCount(); i++) {
            if (username.equals(pmTargetCombo.getItemAt(i))) {
                pmTargetCombo.removeItemAt(i);
                break;
            }
        }
    }

    private boolean listContains(DefaultListModel<String> m, String key) {
        for (int i = 0; i < m.size(); i++) if (m.get(i).contains(key)) return true;
        return false;
    }

    // ─── Cell Renderers ───────────────────────────────────────────────────────

    private class UserRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            String raw = value.toString();
            String[] sp = raw.split("\\|", 2);
            String status = sp.length > 0 ? sp[0] : "AWAY";
            String name   = sp.length > 1 ? sp[1] : raw;

            Color dot; String label;
            switch (status.toUpperCase()) {
                case "ACTIVE" -> { dot = STATUS_ACTIVE; label = "●  " + name + "  (Online)"; }
                case "BUSY"   -> { dot = STATUS_BUSY;   label = "●  " + name + "  (Busy)";   }
                default       -> { dot = STATUS_AWAY;   label = "●  " + name + "  (Away)";   }
            }
            l.setText(label);
            l.setForeground(sel ? Color.WHITE : dot);
            l.setBackground(sel ? BG_HIGHLIGHT : BG_PANEL);
            l.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setOpaque(true);
            return l;
        }
    }

    private class RoomRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
            l.setText("# " + value);
            l.setForeground(sel ? ACCENT : TEXT_PRIMARY);
            l.setBackground(sel ? BG_HIGHLIGHT : BG_PANEL);
            l.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setOpaque(true);
            return l;
        }
    }

    // ─── Style Helpers ────────────────────────────────────────────────────────

    private JButton styledBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(TEXT_PRIMARY);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return b;
    }

    private JTextField styledField(String defaultValue) {
        JTextField f = new JTextField(defaultValue);
        f.putClientProperty("placeholder", defaultValue);
        f.setBackground(BG_SURFACE);
        f.setForeground(TEXT_MUTED);
        f.setCaretColor(ACCENT);
        f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CLR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                Object ph = f.getClientProperty("placeholder");
                if (ph != null && f.getText().equals(ph.toString())) {
                    f.setText("");
                    f.setForeground(TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                Object ph = f.getClientProperty("placeholder");
                if (ph != null && f.getText().trim().isEmpty()) {
                    f.setText(ph.toString());
                    f.setForeground(TEXT_MUTED);
                }
            }
        });

        return f;
    }

    private <T> JComboBox<T> styledCombo(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setBackground(BG_SURFACE); c.setForeground(TEXT_PRIMARY);
        c.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return c;
    }

    private void styleList(JList<?> list) {
        list.setBackground(BG_PANEL); list.setForeground(TEXT_PRIMARY);
        list.setSelectionBackground(BG_HIGHLIGHT); list.setSelectionForeground(Color.WHITE);
        list.setFixedCellHeight(26);
    }

    private JScrollPane styledScroll(Component c, String title) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(titledBorder(title));
        sp.getViewport().setBackground(BG_PANEL);
        return sp;
    }

    private TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_CLR), title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10), TEXT_MUTED);
    }

    private JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ClientGUI().setVisible(true));
    }

    private void updateUserStatus(String username, String newStatus) {
        for (int i = 0; i < userListModel.size(); i++) {
            String entry = userListModel.get(i);
            if (entry.contains("|" + username)) {
                userListModel.set(i, newStatus + "|" + username);
                break;
            }
        }
    }
}
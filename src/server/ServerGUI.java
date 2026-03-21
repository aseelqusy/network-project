package server;

import common.Protocol;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ServerGUI extends JFrame {

    // ── Palette (matches ClientGUI) ───────────────────────────────────────────
    private static final Color BG_BASE      = new Color(28,  26,  24);
    private static final Color BG_PANEL     = new Color(36,  33,  30);
    private static final Color BG_SURFACE   = new Color(46,  42,  38);
    private static final Color BG_HIGHLIGHT = new Color(60,  54,  46);
    private static final Color BG_BUTTON    = new Color(72,  65,  54);
    private static final Color BG_BTN_RED   = new Color(120,  55,  45);
    private static final Color BG_BTN_GREEN = new Color( 72, 108,  54);
    private static final Color ACCENT       = new Color(205, 165,  90);
    private static final Color TEXT_PRIMARY = new Color(228, 220, 208);
    private static final Color TEXT_MUTED   = new Color(145, 135, 118);
    private static final Color BORDER_CLR   = new Color( 58,  52,  44);

    private static final Color STATUS_ACTIVE = new Color( 80, 200,  90);
    private static final Color STATUS_BUSY   = new Color(220, 180,  40);
    private static final Color STATUS_AWAY   = new Color(140, 138, 135);

    // ── Server ────────────────────────────────────────────────────────────────
    private final ChatServer server = new ChatServer();

    // ── UI ────────────────────────────────────────────────────────────────────
    private JLabel            lblStatus;
    private JLabel            lblUptime;
    private long              startTime;
    private DefaultTableModel sessionsModel;
    private DefaultTableModel mailboxModel;
    private JTextArea         logArea;
    private JTextField        tfNewUser;
    private DefaultListModel<String> existingUsersModel = new DefaultListModel<>();
    private JList<String>     existingUsersList;
    private JComboBox<String> cbMaxMsg;
    private JTextField        tfBroadcast;

    public ServerGUI() {
        super("ChatLite Server Console");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(980, 680);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_BASE);
        setLayout(new BorderLayout(0, 0));

        add(buildStatusBar(), BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildLogPanel(),  BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        startServer();

        new Timer(2000, e -> refreshSessions()).start();
        new Timer(1000, e -> updateUptime()).start();
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 7));
        p.setBackground(new Color(22, 20, 18));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR));

        lblStatus = new JLabel("● ONLINE");
        lblStatus.setForeground(STATUS_ACTIVE);
        lblStatus.setFont(new Font("Monospaced", Font.BOLD, 13));

        JLabel portLbl = new JLabel("PORT: " + Protocol.SERVER_PORT);
        portLbl.setForeground(TEXT_MUTED);
        portLbl.setFont(new Font("Monospaced", Font.PLAIN, 12));

        lblUptime = new JLabel("Uptime: 00:00:00");
        lblUptime.setForeground(TEXT_MUTED);
        lblUptime.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JLabel title = new JLabel("ChatLite Server");
        title.setForeground(ACCENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));

        p.add(title);
        p.add(Box.createHorizontalStrut(12));
        p.add(lblStatus);
        p.add(Box.createHorizontalStrut(20));
        p.add(portLbl);
        p.add(Box.createHorizontalStrut(20));
        p.add(lblUptime);
        return p;
    }

    private JSplitPane buildCenter() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildLeftPanel(), buildRightPanel());
        sp.setDividerLocation(210);
        sp.setDividerSize(3);
        sp.setBorder(null);
        sp.setBackground(BG_BASE);
        return sp;
    }

    private JPanel buildLeftPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel lbl = new JLabel("USER MANAGEMENT");
        lbl.setForeground(ACCENT);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        tfNewUser = styledField("Username");
        JTextField tfPass = styledField("Password");
        JButton btnCreate = styledBtn("Create User", BG_BTN_GREEN);
        btnCreate.setForeground(new Color(225, 238, 210));
        btnCreate.addActionListener(e -> createUser());

        JPanel addPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        addPanel.setBackground(BG_PANEL);
        addPanel.add(tfNewUser); addPanel.add(tfPass); addPanel.add(btnCreate);

        existingUsersList = new JList<>(existingUsersModel);
        existingUsersList.setBackground(BG_SURFACE);
        existingUsersList.setForeground(TEXT_PRIMARY);
        existingUsersList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        existingUsersList.setSelectionBackground(BG_HIGHLIGHT);
        existingUsersList.setFixedCellHeight(24);
        JScrollPane uScroll = new JScrollPane(existingUsersList);
        uScroll.setBorder(titledBorder("Existing Users"));
        uScroll.getViewport().setBackground(BG_SURFACE);

        JButton btnDelete = styledBtn("Delete Selected", BG_BTN_RED);
        btnDelete.setForeground(new Color(240, 210, 205));
        btnDelete.addActionListener(e -> deleteSelected());
        JButton btnReset = styledBtn("Reset Password", BG_BUTTON);
        btnReset.addActionListener(e -> appendLog("Password reset triggered."));

        JPanel btns = new JPanel(new GridLayout(2, 1, 4, 4));
        btns.setBackground(BG_PANEL);
        btns.add(btnDelete); btns.add(btnReset);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setBackground(BG_PANEL);
        center.add(addPanel,  BorderLayout.NORTH);
        center.add(uScroll,   BorderLayout.CENTER);
        center.add(btns,      BorderLayout.SOUTH);

        p.add(lbl,    BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(BG_BASE);
        p.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 8));

        JSplitPane tables = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildSessionsPanel(), buildMailboxPanel());
        tables.setDividerLocation(370);
        tables.setDividerSize(3);
        tables.setBorder(null);
        tables.setBackground(BG_BASE);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBackground(BG_BASE);
        bottom.add(buildBroadcastRow(), BorderLayout.CENTER);
        bottom.add(buildConfigPanel(),  BorderLayout.EAST);
        bottom.setPreferredSize(new Dimension(0, 42));

        p.add(tables, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildSessionsPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(BG_BASE);
        p.setBorder(titledBorder("ACTIVE SESSIONS"));

        String[] cols = {"Username", "Status", "IP Address"};
        sessionsModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = styledTable(sessionsModel);
        tbl.getColumnModel().getColumn(1).setCellRenderer(new StatusCellRenderer());

        JScrollPane sp = new JScrollPane(tbl);
        sp.getViewport().setBackground(BG_SURFACE);
        sp.setBackground(BG_SURFACE);
        sp.setBorder(null);

        tfBroadcast = styledField("Broadcast message to all users...");
        JButton btnKick = styledBtn("Kick", BG_BTN_RED);
        btnKick.setForeground(new Color(240, 210, 205));
        btnKick.addActionListener(e -> kickSelected(tbl));
        JButton btnBroadcast = styledBtn("Broadcast", BG_BUTTON);
        btnBroadcast.addActionListener(e -> sendBroadcast());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG_BASE);
        row.add(btnKick,       BorderLayout.WEST);
        row.add(tfBroadcast,   BorderLayout.CENTER);
        row.add(btnBroadcast,  BorderLayout.EAST);

        p.add(sp,  BorderLayout.CENTER);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildMailboxPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(BG_BASE);
        p.setBorder(titledBorder("MAILBOX STATISTICS"));

        String[] cols = {"User", "Inbox", "Sent", "Size"};
        mailboxModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        mailboxModel.addRow(new Object[]{"Ahmed",    5, 12, "3 KB"});
        mailboxModel.addRow(new Object[]{"Sara",     2,  8, "500 B"});
        mailboxModel.addRow(new Object[]{"Mohammed", 0,  1, "0 KB"});

        JTable tbl = styledTable(mailboxModel);
        JScrollPane sp = new JScrollPane(tbl);
        sp.getViewport().setBackground(BG_SURFACE);
        sp.setBorder(null);

        JButton btnCleanup = styledBtn("Archive Cleanup (>30 days)", BG_BUTTON);
        btnCleanup.addActionListener(e -> appendLog("Archive cleanup triggered."));

        p.add(sp,         BorderLayout.CENTER);
        p.add(btnCleanup, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildBroadcastRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setBackground(BG_BASE);
        JButton btnClear = styledBtn("Clear Logs", BG_BUTTON);
        btnClear.addActionListener(e -> logArea.setText(""));
        JButton btnSave = styledBtn("Save Logs", BG_BUTTON);
        btnSave.addActionListener(e -> saveLogs());
        p.add(btnClear); p.add(btnSave);
        return p;
    }

    private JPanel buildConfigPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        p.setBackground(BG_BASE);
        JLabel lbl = new JLabel("Max Msg:");
        lbl.setForeground(TEXT_MUTED);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cbMaxMsg = styledCombo(new String[]{"64 KB","32 KB","16 KB","128 KB"});
        cbMaxMsg.setPreferredSize(new Dimension(80, 26));
        JButton btnApply = styledBtn("Apply", BG_BTN_GREEN);
        btnApply.setForeground(new Color(225, 238, 210));
        btnApply.addActionListener(e -> appendLog("Settings applied: " + cbMaxMsg.getSelectedItem()));
        p.add(lbl); p.add(cbMaxMsg); p.add(btnApply);
        return p;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_PANEL);
        p.setBorder(titledBorder("SYSTEM LOGS (Live)"));
        p.setPreferredSize(new Dimension(0, 150));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(20, 18, 16));
        logArea.setForeground(new Color(160, 210, 150));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(null);
        sp.getViewport().setBackground(new Color(20, 18, 16));

        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // ─── Status Cell Renderer ─────────────────────────────────────────────────

    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, sel, focus, row, col);
            String s = value == null ? "" : value.toString().toUpperCase();
            Color c = switch (s) {
                case "ACTIVE" -> STATUS_ACTIVE;
                case "BUSY"   -> STATUS_BUSY;
                default       -> STATUS_AWAY;
            };
            l.setText("● " + s);
            l.setForeground(sel ? Color.WHITE : c);
            l.setBackground(sel ? BG_HIGHLIGHT : BG_SURFACE);
            l.setFont(new Font("Monospaced", Font.PLAIN, 12));
            return l;
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void startServer() {
        try {
            server.setLogCallback(msg -> SwingUtilities.invokeLater(() -> appendLog(msg)));
            server.start(Protocol.SERVER_PORT);
            startTime = System.currentTimeMillis();
            lblStatus.setText("● ONLINE");
            lblStatus.setForeground(STATUS_ACTIVE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to start server:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void shutdown() {
        int c = JOptionPane.showConfirmDialog(this,
                "Stop the server and exit?", "Confirm Exit", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) { server.stop(); System.exit(0); }
    }

    private void refreshSessions() {
        sessionsModel.setRowCount(0);
        existingUsersModel.clear();
        server.getClients().forEach((name, handler) -> {
            String status = server.getUserStatus(name);
            sessionsModel.addRow(new Object[]{name, status, handler.getIP()});
            existingUsersModel.addElement("○  " + name);
        });
    }

    private void createUser() {
        String name = tfNewUser.getText().trim();
        if (name.isEmpty()) return;
        appendLog("User account created: " + name);
        tfNewUser.setText("");
    }

    private void deleteSelected() {
        String sel = existingUsersList.getSelectedValue();
        if (sel == null) return;
        String name = sel.replace("○  ", "").trim();
        server.kickUser(name);
        appendLog("Kicked/deleted user: " + name);
    }

    private void kickSelected(JTable tbl) {
        int row = tbl.getSelectedRow();
        if (row < 0) return;
        String name = (String) sessionsModel.getValueAt(row, 0);
        server.kickUser(name);
        appendLog("Kicked: " + name);
    }

    private void sendBroadcast() {
        String msg = tfBroadcast.getText().trim();
        if (msg.isEmpty()) return;
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        server.broadcastAll("MSG General SERVER " + ts + " [BROADCAST] " + msg);
        appendLog("Broadcast sent: " + msg);
        tfBroadcast.setText("");
    }

    private void saveLogs() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("server_logs.txt"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw = new PrintWriter(fc.getSelectedFile())) {
                pw.print(logArea.getText());
                appendLog("Logs saved.");
            } catch (IOException ex) {
                appendLog("Save failed: " + ex.getMessage());
            }
        }
    }

    private void appendLog(String msg) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + ts + "] " + msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void updateUptime() {
        if (startTime == 0) return;
        long s = (System.currentTimeMillis() - startTime) / 1000;
        lblUptime.setText(String.format("Uptime: %02d:%02d:%02d", s/3600, (s%3600)/60, s%60));
    }

    // ─── Style Helpers ────────────────────────────────────────────────────────

    private JButton styledBtn(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg); b.setForeground(TEXT_PRIMARY);
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        return b;
    }

    private JTextField styledField(String hint) {
        JTextField f = new JTextField();
        f.setBackground(BG_SURFACE); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT);
        f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CLR),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return f;
    }

    private <T> JComboBox<T> styledCombo(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setBackground(BG_SURFACE); c.setForeground(TEXT_PRIMARY);
        c.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return c;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setBackground(BG_SURFACE); t.setForeground(TEXT_PRIMARY);
        t.setGridColor(BORDER_CLR);
        t.setSelectionBackground(BG_HIGHLIGHT); t.setSelectionForeground(Color.WHITE);
        t.setRowHeight(24);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        t.getTableHeader().setBackground(BG_PANEL);
        t.getTableHeader().setForeground(TEXT_MUTED);
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        t.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR));
        return t;
    }

    private TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_CLR), title,
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 10), TEXT_MUTED);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ServerGUI().setVisible(true));
    }
}

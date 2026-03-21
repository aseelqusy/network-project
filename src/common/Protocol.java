package common;

/**
 * Protocol constants for ChatLite communication.
 * All commands sent from Client to Server, and responses from Server to Client.
 */
public class Protocol {

    // ─── Client → Server Commands ────────────────────────────────────────────
    public static final String HELLO   = "HELLO";   // HELLO <username>
    public static final String JOIN    = "JOIN";    // JOIN <room>
    public static final String MSG     = "MSG";     // MSG <room> <message>
    public static final String PM      = "PM";      // PM <username> <message>
    public static final String USERS   = "USERS";   // USERS
    public static final String ROOMS   = "ROOMS";   // ROOMS
    public static final String LEAVE   = "LEAVE";   // LEAVE <room>
    public static final String QUIT    = "QUIT";    // QUIT
    public static final String STATUS  = "STATUS";  // STATUS <ACTIVE|BUSY|AWAY>

    // ─── Server → Client Responses ───────────────────────────────────────────
    public static final String R_WELCOME        = "200 WELCOME";
    public static final String R_NAME_TAKEN     = "401 NAME TAKEN";
    public static final String R_JOINED         = "210 JOINED";
    public static final String R_SENT           = "211 SENT";
    public static final String R_PRIVATE_SENT   = "212 PRIVATE SENT";
    public static final String R_USERS          = "213";
    public static final String R_USERS_ENTRY    = "213U";
    public static final String R_USERS_END      = "213 END";
    public static final String R_ROOM           = "214";
    public static final String R_LEFT           = "215 LEFT";
    public static final String R_BYE            = "221 BYE";
    public static final String R_MESSAGE        = "MSG";      // broadcast to room
    public static final String R_PRIVATE        = "PM";       // incoming private
    public static final String R_USER_JOINED    = "USER_JOINED";
    public static final String R_USER_LEFT      = "USER_LEFT";
    public static final String R_ERROR          = "400 ERROR";

    // ─── Default Settings ────────────────────────────────────────────────────
    public static final int    SERVER_PORT      = 5000;
    public static final String DEFAULT_ROOM     = "General";
    public static final int    MAX_MSG_SIZE     = 65536; // 64 KB
}

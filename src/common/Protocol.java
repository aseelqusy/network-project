package common;

public class Protocol {
    public static final String HELLO   = "HELLO";
    public static final String JOIN    = "JOIN";
    public static final String MSG     = "MSG";
    public static final String PM      = "PM";
    public static final String USERS   = "USERS";
    public static final String ROOMS   = "ROOMS";
    public static final String LEAVE   = "LEAVE";
    public static final String QUIT    = "QUIT";
    public static final String STATUS  = "STATUS";

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
    public static final String R_MESSAGE        = "MSG";
    public static final String R_PRIVATE        = "PM";
    public static final String R_USER_JOINED    = "USER_JOINED";
    public static final String R_USER_LEFT      = "USER_LEFT";
    public static final String R_STATUS_UPDATE  = "216 STATUS";
    public static final String R_SERVER_SHUTDOWN = "400 SHUTDOWN";
    public static final String R_KICKED         = "400 KICKED";
    public static final String R_ERROR          = "400 ERROR";

    public static final int    SERVER_PORT      = 5000;
    public static final String SERVER_HOST      = "10.250.162.140";
    public static final String DEFAULT_ROOM     = "General";
    public static final int    MAX_MSG_SIZE     = 65536;
}

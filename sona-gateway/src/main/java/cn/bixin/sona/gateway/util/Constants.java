package cn.bixin.sona.gateway.util;

/**
 * @author qinwei
 */
public class Constants {

    public static final String MQ_SEND_KEY_CMD = "cmd";

    public static final String MQ_REPORT_KEY_TYPE = "t";

    public static final String MQ_REPORT_VAL_TYPE_CONNECT = "connect";

    public static final String MQ_REPORT_VAL_TYPE_ROOM = "room";

    public static final String MQ_REPORT_KEY_DEVICE_ID = "deviceId";
    public static final String MQ_REPORT_KEY_CLIENT_IP = "clientIp";
    public static final String MQ_REPORT_KEY_SESSION = "session";
    public static final String MQ_REPORT_KEY_CHANNEL_ID = "channelId";
    public static final String MQ_REPORT_KEY_SERVER_ID = "serverId";
    public static final String MQ_REPORT_KEY_START_TIME = "startTime";

    public static final String MQ_REPORT_KEY_TIMESTAMP_SHORT = "tm";
    public static final String MQ_REPORT_KEY_AUTH_CONN = "authConn";
    public static final String MQ_REPORT_KEY_UNAUTH_CONN = "unAuthConn";

    public static final String MQ_REPORT_KEY_UID = "uid";
    //房间号
    public static final String MQ_REPORT_KEY_ROOM = "room";
    //房间号列表
    public static final String MQ_REPORT_KEY_ROOMS = "rooms";
    //global字段用于控制消息的广播范围，如果为true则全局广播，如果为false则只广播给特定的聊天室
    public static final String MQ_REPORT_KEY_GLOBAL = "global";
    public static final String MQ_REPORT_KEY_GROUP = "group";
    public static final String MQ_REPORT_KEY_CMD = "cmd";
    public static final String MQ_REPORT_KEY_DATA = "data";
    //用户ID的列表，这些用户是消息的接收者
    public static final String MQ_REPORT_KEY_MEMBERS = "members";
    public static final String MQ_REPORT_KEY_PRIORITY = "highPriority";
    public static final String MQ_REPORT_KEY_ACK_UIDS = "ackUids";


    public static final int SESSION_ONLINE = 1;
    public static final int SESSION_OFFLINE = 0;

    public static final String PUSH_MSG_KEY_TYPE = "type";
    public static final String PUSH_MSG_KEY_DATA = "data";

    public static final String PUSH_MSG_TYPE_APPSTATE = "appstate";
    public static final String APPSTATE_KEY_FOREGROUND = "foreground";

    public static final String CHATROOM_MSG_KEY_ROOM = "room";
    public static final String CHATROOM_MSG_KEY_ACK = "ack";
    public static final String CHATROOM_MSG_KEY_SIGNAL = "signal";
    public static final String CHATROOM_MSG_KEY_UID = "uid";
    public static final String CHATROOM_MSG_KEY_IDENTITY = "identity";

    public static final int PROTOCOL_META_LEN = 5;

}

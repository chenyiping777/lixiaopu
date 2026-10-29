package com.lixiaopu.security;

/** 会话所属业务端，不表示手机、浏览器等物理设备。name() 同时是 Redis 会话中的稳定取值。 */
public enum ClientType {
    BUYER("buyer:access", "/ws/buyer/chat"),
    ADMIN("admin:access", "/ws/admin/chat");

    private final String accessPermission;
    private final String chatPath;

    ClientType(String accessPermission, String chatPath) {
        this.accessPermission = accessPermission;
        this.chatPath = chatPath;
    }

    public String getAccessPermission() { return accessPermission; }
    public String getChatPath() { return chatPath; }

    /** 只接受精确的公开握手路径；不从查询参数读取访问端。 */
    public static ClientType fromChatPath(String path) {
        for (ClientType type : values()) if (type.chatPath.equals(path)) return type;
        return null;
    }
}

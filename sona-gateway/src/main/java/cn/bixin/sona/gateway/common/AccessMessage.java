package cn.bixin.sona.gateway.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author qinwei
 */
public class AccessMessage {

    /**
     * 1表示request ， 0表示response
     */
    private boolean req;

    /**
     * 是否需要回复response ，1 需要， 0 不需要
     */
    private boolean twoWay;

    /**
     * 是否心跳 ，1是 ， 0 不是
     */
    private boolean heartbeat;

    /**
     * 版本号
     */
    private int version;

    /**
     * 请求或响应的id（从1开始递增，单连接不重复），如果是request请求并且twoWay是false ，设置 0
     */
    private int id;

    /**
     * command 命令，每个command都有对应的请求处理器{@link CommandEnum}
     */
    private int cmd;

    /**
     * 所有 header + body 的大小
     */
    private int length;

    /**
     * Header
     */
    private List<Header> headers;

    /**
     * 消息体
     */
    private byte[] body;

    public AccessMessage() {
    }

    public AccessMessage(AccessMessage message) {
        this.req = message.req;
        this.twoWay = message.twoWay;
        this.heartbeat = message.heartbeat;
        this.version = message.version;
        this.id = message.id;
        this.cmd = message.cmd;
        if (message.headers != null) {
            this.headers = new ArrayList<>(message.headers);
        }
        this.body = message.body;
    }

    public void addHeader(Header header) {
        if (headers == null) {
            headers = new ArrayList<>();
        }
        headers.add(header);
    }

    public boolean isReq() {
        return req;
    }

    public void setReq(boolean req) {
        this.req = req;
    }

    public boolean isTwoWay() {
        return twoWay;
    }

    public void setTwoWay(boolean twoWay) {
        this.twoWay = twoWay;
    }

    public boolean isHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(boolean heartbeat) {
        this.heartbeat = heartbeat;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCmd() {
        return cmd;
    }

    public void setCmd(int cmd) {
        this.cmd = cmd;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Header> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "AccessMessage{" +
                "req=" + req +
                ", twoWay=" + twoWay +
                ", heartbeat=" + heartbeat +
                ", version=" + version +
                ", id=" + id +
                ", cmd=" + cmd +
                ", length=" + length +
                ", headers=" + headers +
                ", body=" + (body == null ? null : new String(body, StandardCharsets.UTF_8)) +
                '}';
    }
}

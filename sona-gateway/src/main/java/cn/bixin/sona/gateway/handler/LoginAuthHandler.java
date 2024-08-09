package cn.bixin.sona.gateway.handler;

import cn.bixin.sona.gateway.cat.MonitorUtils;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.support.ChannelAttrs;
import cn.bixin.sona.gateway.common.AccessMessage;
import cn.bixin.sona.gateway.exception.RemoteException;
import cn.bixin.sona.gateway.msg.AccessResponse;
import cn.bixin.sona.gateway.msg.HandShakeBody;
import cn.bixin.sona.gateway.service.SocketNotifyService;
import cn.bixin.sona.gateway.util.AccessMessageUtils;
import cn.bixin.sona.gateway.util.EventRecordLog;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * @author qinwei
 * <p>
 * 登录认证
 */
@Service("loginAuth")
public class LoginAuthHandler extends AbstractHandler {

    public static final String LOGIN_EVENT = "Login";

    @Resource
    private SocketNotifyService socketNotifyService;

    @Override
    protected Object doHandle(NettyChannel channel, AccessMessage message) throws RemoteException {
        // 解析请求体
        String bodyData = new String(message.getBody(), StandardCharsets.UTF_8);
        HandShakeBody handShake = JSON.parseObject(bodyData, HandShakeBody.class);
        String deviceId = handShake.getD();

        // 检查设备 ID
        if (!StringUtils.hasText(deviceId)) {
            channel.send(AccessMessageUtils.createResponse(message.getId(), message.getCmd(), JSON.toJSONBytes(AccessResponse.ACCESS_FAIL)), false, true);
            EventRecordLog.logEvent(channel, LOGIN_EVENT, message, "EmptyDeviceId");
            MonitorUtils.logEvent(MonitorUtils.LOGIN_PROBLEM, "EmptyDeviceId");
            return null;
        }

        // 设置通道属性
        ChannelAttrs attrs = channel.getAttrs();
        attrs.setClientProtoVer(message.getVersion());
        attrs.setChannelType(handShake.getT());
        attrs.setDeviceId(deviceId);
        attrs.setPlatform(handShake.getP());
        attrs.setSysVer(handShake.getSv());
        attrs.setModel(handShake.getM());
        attrs.setUid(handShake.getU());
        attrs.setForeground(handShake.getB() == 0);

        // 标记认证
        if (!channel.markAuth()) {
            EventRecordLog.logEvent(channel, LOGIN_EVENT, message, "Repeated handshake");
            return AccessResponse.SUCCESS;
        }

        // 增加通道类型计数
        NettyChannel.addChannelTypeCount(channel);

        // 处理连接
        socketNotifyService.processConnect(channel);

        // 记录事件日志和监控日志
        EventRecordLog.logEvent(channel, LOGIN_EVENT, message, bodyData);
        MonitorUtils.logCatEventWithChannelAttrs(MonitorUtils.LOGIN, "", channel, true);

        // 返回成功响应
        return AccessResponse.SUCCESS;
    }
}

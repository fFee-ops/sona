package cn.bixin.sona.gateway.interceptor;

import cn.bixin.sona.gateway.cat.MonitorUtils;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.handler.IdleChannelHandler;
import cn.bixin.sona.gateway.common.AccessMessage;
import cn.bixin.sona.gateway.common.CommandEnum;
import cn.bixin.sona.gateway.handler.LoginAuthHandler;
import cn.bixin.sona.gateway.msg.AccessResponse;
import cn.bixin.sona.gateway.util.AccessMessageUtils;
import cn.bixin.sona.gateway.util.EventRecordLog;
import com.alibaba.fastjson.JSON;
import io.netty.util.Timeout;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @author qinwei
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Interceptor(name = "loginAuth")
public class LoginAuthInterceptor implements HandlerInterceptor {

    /**
     * 在登陆请求前进行拦截，判断请求是否合法
     *
     * @param channel channel
     * @param message message
     * @return boolean
     * @throws Exception Exception
     */
    @Override
    public boolean preHandle(NettyChannel channel, AccessMessage message) throws Exception {
        byte[] body = message.getBody();
        if (body == null || body.length == 0) {
            channel.send(AccessMessageUtils.createResponse(message.getId(), message.getCmd(), JSON.toJSONBytes(AccessResponse.ACCESS_FAIL)), false, true);
            EventRecordLog.logEvent(channel, LoginAuthHandler.LOGIN_EVENT, message, "EmptyBody");
            MonitorUtils.logEvent(MonitorUtils.LOGIN_PROBLEM, "EmptyBody");
            return false;
        }
        if (CommandEnum.LOGIN_AUTH.getCommand() != message.getCmd()) {
            EventRecordLog.logEvent(channel, LoginAuthHandler.LOGIN_EVENT, message, "Unknown cmd");
            MonitorUtils.logEvent(MonitorUtils.LOGIN_PROBLEM, "UnknownCmd");
            return false;
        }
        return true;
    }

    /**
     * 登陆请求业务逻辑处理后进行拦截，取消握手超时定时器
     *
     * @param channel channel
     * @param message message
     * @throws Exception Exception
     */
    @Override
    public void postHandle(NettyChannel channel, AccessMessage message) throws Exception {
        Timeout timeout = channel.removeAttribute(IdleChannelHandler.KEY_HAND_SHAKE, Timeout.class);
        if (timeout != null) {
            timeout.cancel();
        }
    }

}

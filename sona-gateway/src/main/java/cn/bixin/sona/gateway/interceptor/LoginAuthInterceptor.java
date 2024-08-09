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
     * <p>
     * 请求体是否为空：检查 message 的 body 是否为空或长度为零。如果为空，则发送失败响应，并记录事件日志和监控日志，返回 false。
     * 命令是否为登录认证命令：检查 message 的命令是否为 LOGIN_AUTH 命令。如果不是，则记录事件日志和监控日志，返回 false。
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

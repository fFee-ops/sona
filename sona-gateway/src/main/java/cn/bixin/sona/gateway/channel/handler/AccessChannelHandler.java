package cn.bixin.sona.gateway.channel.handler;

import cn.bixin.sona.gateway.cat.MonitorUtils;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.support.AccessFilter;
import cn.bixin.sona.gateway.exception.RemoteException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author qinwei
 * <p>
 * Access 权限校验
 */
@Slf4j
public class AccessChannelHandler extends AbstractChannelHandler {

    public AccessChannelHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void connect(NettyChannel channel) throws RemoteException {
        //基于 IP 的权限校验，不符合的连接直接拒绝
        if (!AccessFilter.INSTANCE.accept(channel.getRemoteAddress())) {
            log.info("AccessDeny, remoteAddress={}", channel.getRemoteAddress());
            MonitorUtils.logEvent(MonitorUtils.MERCURY_ACCESS_DENY, channel.getRemoteAddress().getAddress().getHostAddress());
            channel.close();
            return;
        }
        handler.connect(channel);
    }
}

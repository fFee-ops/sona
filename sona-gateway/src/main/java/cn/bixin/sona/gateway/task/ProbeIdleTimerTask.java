package cn.bixin.sona.gateway.task;

import cn.bixin.sona.common.spring.SpringApplicationContext;
import cn.bixin.sona.gateway.cat.MonitorUtils;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.support.ChannelAttrs;
import cn.bixin.sona.gateway.common.AccessMessage;
import cn.bixin.sona.gateway.concurrent.counter.SystemClock;
import cn.bixin.sona.gateway.config.ApolloConfiguration;
import cn.bixin.sona.gateway.util.AccessMessageUtils;
import cn.bixin.sona.gateway.util.EventRecordLog;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeoutException;

/**
 * @author qinwei
 * 客户端会将 App 是 前台还是后台的状态上报给 Mercury 网关，服务端维护的NettyChannel 会记录下这个状态。
 * 处于前台状态的 channel，如果130秒内都没有读到过新数据，也没有收到任何心跳包，服务端就会主动下发 探测消息。
 * 这时客户端需要在4秒内给服务端返回一个响应消息，否则会 close 掉这个连接。
 */
@Slf4j
public class ProbeIdleTimerTask extends HeartbeatTimerTask {

    public ProbeIdleTimerTask(long tick, int idleTimeout) {
        super(tick, idleTimeout);
    }

    @Override
    protected void doTask(NettyChannel channel) {
        try {
            if (!channel.isConnected()) {
                return;
            }
            long now = SystemClock.currentTimeMillis();
            boolean isReadTimeout = isReadTimeout(channel, now);
            if (isReadTimeout) {
                ChannelAttrs attrs = channel.getAttrsIfExists();
                if (attrs == null) {
                    return;
                }
                if (attrs.isForeground()) {
                    int probeWaitSeconds = SpringApplicationContext.getBean(ApolloConfiguration.class).getProbeWaitSeconds();
                    if (probeWaitSeconds <= 0) {
                        return;
                    }
                    log.info("ProbeSent, remoteAddress={}", channel.getRemoteAddress());
                    AccessMessage request = AccessMessageUtils.createHeartRequest(channel.getSequece());
                    channel.request(request, probeWaitSeconds * 1000)
                            .whenComplete((message, throwable) -> {
                                if (throwable instanceof TimeoutException) {
                                    EventRecordLog.logEvent(channel, "Probe timeout", probeWaitSeconds + " s");
                                    channel.close();
                                    MonitorUtils.logCatEventWithChannelAttrs(MonitorUtils.PROBE_IDLE, "", channel, false);
                                }
                            });
                }
            }
        } catch (Throwable t) {
            log.warn("Exception when handle probe message , channel " + channel.getRemoteAddress(), t);
        }
    }
}

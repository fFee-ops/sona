package cn.bixin.sona.gateway.task;

import cn.bixin.sona.gateway.cat.MonitorUtils;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.handler.IdleChannelHandler;
import cn.bixin.sona.gateway.concurrent.counter.SystemClock;
import cn.bixin.sona.gateway.util.EventRecordLog;
import lombok.extern.slf4j.Slf4j;

/**
 * @author qinwei
 * <p>
 * HeartbeatTimerTask 里面会拿到所有维护的channel，依次比较当前时间戳和channel
 * 的readTimestamp、writeTimestamp，如果有任意一个差值超过了配置的心跳超时
 * 时间，就 close 掉当前 channel
 * <p>
 * 使用IdleStateHandler的问题：
 * 如果使用Netty的IdleStateHandler，单机连接数达到10w 以上之后，IdleStateHandler 内部使用了 eventLoop.schedule(task) 的方式来实现定时任务，
 * 大量的心跳检测任务会影响到正常 IO 事件处理，导致CPU 居高不下。
 * 原因：
 * IdleStateHandler 也是一个 ChannelHandler ，并且是有状态的，没有加上@io.netty.channel.ChannelHandler.Sharable 注解，不能被共享，所以每个 channel 都需要创建单独的定时任务。
 * 如果有 10w 个连接，就会有 10w 个这样的定时任务 交给 workGroup 里面的 eventloop 去处理。
 * <p>
 * <p>
 * 解决方案：
 * 采用HashedWheelTimer来执行这个定时任务这种方式，不管有 10w 个还是 20w 个连接，永远都只会存在一个HeartbeatTimerTask，并不会因为连接数的增加而增加。
 * HashedWheelTimer 内部维护了一个单独的线程，所以不会影响到 eventloop 的执行，也就不会影响到正常的 IO 事件处理。
 */
@Slf4j
public class HeartbeatTimerTask extends ScheduleTimerTask {

    private final int idleTimeout;

    public HeartbeatTimerTask(long tick, int idleTimeout) {
        super(tick);
        this.idleTimeout = idleTimeout;
    }

    @Override
    protected void doTask(NettyChannel channel) {
        try {
            if (!channel.isConnected()) {
                return;
            }
            long now = SystemClock.currentTimeMillis();
            boolean isReadTimeout = isReadTimeout(channel, now);
            boolean isWriteTimeout = isWriteTimeout(channel, now);
            if (isReadTimeout || isWriteTimeout) {
                EventRecordLog.logEvent(channel, "Heartbeat timeout", idleTimeout + " ms");
                channel.close();
                MonitorUtils.logCatEventWithChannelAttrs(MonitorUtils.IDLE_STATE_EVENT, isReadTimeout ? "READER_IDLE" : "WRITER_IDLE", channel, true);
            }
        } catch (Throwable t) {
            log.warn("Exception when close channel " + channel.getRemoteAddress(), t);
        }
    }

    protected boolean isReadTimeout(NettyChannel channel, long now) {
        Long lastRead = lastRead(channel);
        return lastRead != null && now - lastRead > idleTimeout;
    }

    protected boolean isWriteTimeout(NettyChannel channel, long now) {
        Long lastWrite = lastWrite(channel);
        return lastWrite != null && now - lastWrite > idleTimeout;
    }

    public static Long lastRead(NettyChannel channel) {
        return channel.getAttribute(IdleChannelHandler.KEY_READ_TIMESTAMP, Long.class);
    }

    public static Long lastWrite(NettyChannel channel) {
        return channel.getAttribute(IdleChannelHandler.KEY_WRITE_TIMESTAMP, Long.class);
    }
}

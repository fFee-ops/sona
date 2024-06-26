package cn.bixin.sona.gateway.netty;

import cn.bixin.sona.gateway.cat.MonitorUtils;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.common.AccessMessage;
import cn.bixin.sona.gateway.common.CommandEnum;
import cn.bixin.sona.gateway.config.ApolloConfiguration;
import cn.bixin.sona.gateway.util.AccessMessageUtils;
import com.google.common.util.concurrent.RateLimiter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author qinwei
 */
@Component
@Slf4j
public class NettyServer implements ApplicationListener<ContextClosedEvent> {

    public static final Integer PORT = 2180;
    public static final Integer PORT_WS = 2190;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private Channel channelWs;

    @Resource
    private ApolloConfiguration apolloConfiguration;

    /**
     * 启动netty服务
     */
    public ChannelFuture start() {
        ChannelFuture cf = null;
        ChannelFuture cfWs = null;
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bossGroup = NettyFactory.eventLoopGroup(1, "bossLoopGroup");
            workerGroup = NettyFactory.eventLoopGroup(4, "workerLoopGroup");
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NettyFactory.serverSocketChannelClass())
                    .option(ChannelOption.SO_BACKLOG, 2048)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(64 * 1024, 128 * 1024))
                    //NettyServerInitializer 是一个 ChannelInitializer<SocketChannel> 的子类，它的作用是初始化每个新连接的 SocketChannel 的 ChannelPipeline。
                    .childHandler(new NettyServerInitializer());
            cf = bootstrap.bind(PORT).sync();
            channel = cf.channel();
            cfWs = bootstrap.bind(PORT_WS).sync();
            channelWs = cfWs.channel();
        } catch (Exception e) {
            log.error("Netty server start fail", e);
        } finally {
            if (cf != null && cf.isSuccess() && cfWs != null && cfWs.isSuccess()) {
                log.info("Netty server listening ready for connections...");
            } else {
                log.error("Netty server start up error!");
            }
        }
        printNettyConfig();
        return cf;
    }

    private static void printNettyConfig() {
        log.info("Runtime.getRuntime().availableProcessors() : {}", Runtime.getRuntime().availableProcessors());
        log.info("-Dio.netty.allocator.numHeapArenas: {}", PooledByteBufAllocator.defaultNumHeapArena());
        log.info("-Dio.netty.allocator.numDirectArenas: {}", PooledByteBufAllocator.defaultNumDirectArena());
        log.info("-Dio.netty.allocator.pageSize: {}", PooledByteBufAllocator.defaultPageSize());
        log.info("-Dio.netty.allocator.maxOrder: {}", PooledByteBufAllocator.defaultMaxOrder());
        log.info("-Dio.netty.allocator.chunkSize: {}", PooledByteBufAllocator.defaultPageSize() << PooledByteBufAllocator.defaultMaxOrder());
        log.info("-Dio.netty.allocator.tinyCacheSize: {}", PooledByteBufAllocator.defaultTinyCacheSize());
        log.info("-Dio.netty.allocator.smallCacheSize: {}", PooledByteBufAllocator.defaultSmallCacheSize());
        log.info("-Dio.netty.allocator.normalCacheSize: {}", PooledByteBufAllocator.defaultNormalCacheSize());
        log.info("-Dio.netty.allocator.useCacheForAllThreads: {}", PooledByteBufAllocator.defaultUseCacheForAllThreads());
    }

    /**
     * 停止netty服务
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Shutdown Netty Server... ");
        //关闭 server channel
        closeServerChannel();
        // server发送close消息，让client主动断开连接
        sendReconnectMsg();
        //还是不断开的连接，就让server主动断开
        forceCloseChannel();
        //等待channel被清空
        waitClose();
        //优雅关闭
        shutdown();
        log.info("Shutdown Netty Server Success!");
    }

    private void shutdown() {
        try {
            // TODO: 2024/4/11 梳理一下该方法的底层细节，文档上有 @sl
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        } catch (Throwable e) {
            log.error("EventLoopGroup.shutdownGracefully() failed", e);
        }
    }

    /**
     * 一直不停的检测是否还存在有效连接，如果有的话等待 250 ms再重新检测，不过检测时间最多3秒。
     * 然后直接关闭所有连接。
     */
    private void waitClose() {
        long startTime = System.currentTimeMillis();
        try {
            while (NettyChannel.authChannelCount() > 0) {
                Thread.sleep(250L);
                if (System.currentTimeMillis() - startTime >= 3000L) {
                    log.info("wait channel close remainingSize={}, elapsedTime={} seconds, wait no more, just shut down!", NettyChannel.authChannelCount(), (System.currentTimeMillis() - startTime) / 1000.0);
                    break;
                }
            }
        } catch (Throwable e) {
            log.error("wait channel close failed!", e);
        }
    }

    /**
     * 服务端主动关闭连接
     * 做了平滑处理，每次只close 指定数量的channel 。
     */
    private void forceCloseChannel() {
        long startTime = System.currentTimeMillis();
        try {
            Collection<NettyChannel> channels = NettyChannel.getAllChannels();
            RateLimiter rateLimiter = RateLimiter.create(Math.max(apolloConfiguration.getCloseMsgThrottling(), 1));
            for (NettyChannel channel : channels) {
                if (channel.isAuth()) {
                    rateLimiter.acquire();
                    channel.close();
                }
            }
            log.info("server-side close all remaining channels, elapsedTime={} seconds", (System.currentTimeMillis() - startTime) / 1000.0);
        } catch (Throwable e) {
            log.error("server-side close channels failed!", e);
        }
    }

    /**
     * 当客户端收到 server 下发的 reconnect 消息之后，就会断开当前连接，然后重新建立连接。
     * 每次只会同时给指定数量的连接下发 reconnect 消息 ，然后等待几秒后再接着下发，避免数万客户端同时发起重新建连，会造成其他的网关机器 CPU使用率突增。
     */
    private void sendReconnectMsg() {
        try {
            long startTime = System.currentTimeMillis();
            int maxProcessSeconds = Math.max(apolloConfiguration.getCloseMsgMaxWaitSeconds(), 1);
            long deadline = startTime + 1000L * maxProcessSeconds;
            RateLimiter rateLimiter = RateLimiter.create(Math.max(apolloConfiguration.getCloseMsgThrottling(), 1));
            Collection<NettyChannel> channels = NettyChannel.getAllChannels();
            if (CollectionUtils.isEmpty(channels)) {
                return;
            }
            AccessMessage message = AccessMessageUtils.createRequest(CommandEnum.CLOSE_CHANNEL.getCommand(), null);
            for (NettyChannel ch : channels) {
                if (!ch.isAuth()) {
                    continue;
                }
                rateLimiter.acquire();
                ch.send(message);
                if (System.currentTimeMillis() >= deadline) {
                    log.info("send close message remainingSize={}, elapsedTime={} seconds, wait no more, just kill them!", channels.size(), (System.currentTimeMillis() - startTime) / 1000.0);
                    break;
                }
            }
            log.info("send close message remainingSize={}, elapsedTime={} seconds", channels.size(), (System.currentTimeMillis() - startTime) / 1000.0);
            Thread.sleep(2000L);
        } catch (Throwable e) {
            log.error("send close message failed !", e);
        }
    }

    /**
     * 服务端关闭 NioServerSocketChannel，取消端口绑定，关闭服务。
     * 这里的 close() 方法实际是在 AbstractChannel 实现的。
     * 在方法内部，会调用对应的 ChannelPipeline的 close() 方法，将 close 事件在 pipeline 上传播。而 close 事件
     * 属于 Outbound 事件，所以会从 tail 节点开始，最终传播到 head 节点，使用 Unsafe 进行关闭：unsafe 内部最终会执行 Java 原生 NIO ServerSocketChannel 关闭
     */
    private void closeServerChannel() {
        try {
            if (channel != null) {
                channel.close();
            }
            if (channelWs != null) {
                channelWs.close();
            }
        } catch (Throwable e) {
            log.error("server channel close failed !", e);
        }
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void closeLongLastingChannels() {
        int longLastingCloseHours = apolloConfiguration.getLongLastingCloseHours();
        if (longLastingCloseHours <= 0) {
            return;
        }
        long longLastingCloseMs = 1000L * 60 * 60 * longLastingCloseHours;
        long startTime = System.currentTimeMillis();
        try {
            Collection<NettyChannel> channels = NettyChannel.getAllChannels();
            List<NettyChannel> longLastingChannels = new ArrayList<>();
            for (NettyChannel ch : channels) {
                if (!ch.isAuth()) {
                    continue;
                }
                long createTime = ch.getAttrs().getCreateTime();
                if (System.currentTimeMillis() - createTime >= longLastingCloseMs) {
                    longLastingChannels.add(ch);
                    log.info("closeLongLastingChannels remoteAddress={}", ch.getRemoteAddress());
                    MonitorUtils.logEvent(MonitorUtils.CLOSE_LONG_LASTING_CHANNEL, MonitorUtils.CLOSE_LONG_LASTING_CHANNEL);
                }
            }
            log.info("closeLongLastingChannels size={}", longLastingChannels.size());

            if (!longLastingChannels.isEmpty()) {
                AccessMessage message = AccessMessageUtils.createRequest(CommandEnum.CLOSE_CHANNEL.getCommand(), null);
                for (NettyChannel ch : longLastingChannels) {
                    ch.send(message);
                }
                Thread.sleep(2000L);
                for (NettyChannel ch : longLastingChannels) {
                    if (ch.isConnected()) {
                        ch.close();
                    }
                }
            }
            log.info("closeLongLastingChannels complete, elapsedTime={}", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("closeLongLastingChannels failed ! ", e);
        }
    }

}

package cn.bixin.sona.gateway.netty;

import cn.bixin.sona.gateway.channel.support.ChannelAttrs;
import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.handler.ChannelHandler;
import cn.bixin.sona.gateway.util.NetUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * @author qinwei
 * `@Sharable` :无论有多少个连接，也只需 new 一个 ChannelHandler 实例，被所有 ChannelPipeline 共享
 */
@Slf4j
@io.netty.channel.ChannelHandler.Sharable
public class NettyServerHandler extends ChannelDuplexHandler {
    //底层处理器，被装饰者模式装饰
    private final ChannelHandler handler;

    public NettyServerHandler(ChannelHandler handler) {
        this.handler = handler;
    }

    /**
     * 对应netty的connect事件即连接建立事件
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int serverPort = NetUtil.getPort(ctx.channel().localAddress());
        if (serverPort == NettyServer.PORT) {
            // 非websocket连接初始化
            initChannel(ctx.channel(), null);
        }
    }

    /**
     * 对应netty的disconnect事件即连接断开事件
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel());
        try {
            log.info("The connection of {} -> {} is disconnected, channelId={}", channel.getRemoteAddress(), channel.getLocalAddress(), channel.getChannelId());
            handler.disconnect(channel);
        } finally {
            NettyChannel.removeChannel(ctx.channel());
        }
    }

    /**
     * 对应netty的receive事件即读事件
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel());
        handler.receive(channel, msg);
        // 对于 WebSocket 场景，接收到的消息可能是 WebSocketFrame 对象，它属于 ReferenceCounted 接口的实现类
        // 需要手动释放资源，因此调用 super.channelRead(ctx, msg) 方法触发 TailContext 的 channelRead 方法
        // 以执行 ReferenceCountUtil.release(msg) 释放资源
        super.channelRead(ctx, msg);
    }

    /**
     * 对应netty的send事件即写事件
     *
     * @param ctx     上下文
     * @param msg     消息
     * @param promise 保证
     * @throws Exception 异常
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel());
        handler.send(channel, msg);
    }

    /**
     * 针对 WebSocket 协议的握手完成事件进行了处理
     *
     * @param ctx 上下文
     * @param evt 事件
     * @throws Exception 异常
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete handshakeComplete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            int serverPort = NetUtil.getPort(ctx.channel().localAddress());
            if (serverPort == NettyServer.PORT_WS) {
                // websocket连接初始化
                InetSocketAddress remoteAddr = NetUtil.getWsRemoteAddrFromHeader(handshakeComplete.requestHeaders(), ctx.channel());
                initChannel(ctx.channel(), remoteAddr);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * 对应netty的caught事件即异常事件
     *
     * @param ctx   上下文
     * @param cause 异常
     * @throws Exception 异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel());
        handler.caught(channel, cause);
    }

    private NettyChannel initChannel(Channel ch, InetSocketAddress remoteAddr) throws Exception {
        if (remoteAddr == null) {
            remoteAddr = (InetSocketAddress) ch.remoteAddress();
        }
        ChannelAttrs.init(ch, remoteAddr);
        NettyChannel channel = NettyChannel.getOrAddChannel(ch);
        log.info("The connection of {} -> {} is established, channelId={}", channel.getRemoteAddress(), channel.getLocalAddress(), channel.getChannelId());
        handler.connect(channel);
        return channel;
    }
}

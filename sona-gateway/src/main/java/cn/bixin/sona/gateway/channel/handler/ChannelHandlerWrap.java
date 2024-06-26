package cn.bixin.sona.gateway.channel.handler;

/**
 * @author qinwei
 * <p>
 * ChannelHandler 包装类
 */
public class ChannelHandlerWrap {

    private static final ChannelHandlerWrap INSTANCE = new ChannelHandlerWrap();

    private ChannelHandlerWrap() {
    }

    private static ChannelHandlerWrap getInstance() {
        return INSTANCE;
    }

    public static ChannelHandler wrap(ChannelHandler handler) {
        return getInstance().wrapHandler(handler);
    }

    /**
     * 包装handler
     * <p>
     * 当有事件发生时，例如新连接建立或者数据到来时，处理顺序将从最外层的 AccessChannelHandler 开始，一直传递到最内层的 MercuryServerHandler。
     *
     * @param handler handler
     * @return ChannelHandler
     */
    private ChannelHandler wrapHandler(ChannelHandler handler) {
        return new AccessChannelHandler(new CatReportChannelHandler(new IdleChannelHandler(new DispatchChannelHandler(handler))));
    }
}

package cn.bixin.sona.gateway.channel.handler;

import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.channel.support.NettyFuture;
import cn.bixin.sona.gateway.common.AccessMessage;
import cn.bixin.sona.gateway.exception.RemoteException;
import cn.bixin.sona.gateway.handler.Handler;
import cn.bixin.sona.gateway.handler.MercuryRouter;
import cn.bixin.sona.gateway.util.AccessMessageUtils;
import com.alibaba.fastjson.JSON;

/**
 * @author qinwei
 * <p>
 * 最上层的业务 ChannelHandler，会根据请求中的 Command 命令，将当前请求路由到对应的业务 handler 中处理
 * <p>
 * 可以分发到下面的handler：
 * ChatRoomHandler
 * ClientPushHandler
 * LoginAuthHandler
 */
public class MercuryServerHandler extends ChannelHandlerDelegate {

    @Override
    public void receive(NettyChannel channel, Object message) throws RemoteException {
        AccessMessage msg = (AccessMessage) message;
        if (msg.isReq()) {
            if (msg.isTwoWay()) {
                handRequest(channel, msg);
            } else {
                MercuryRouter.router(msg.getCmd()).receive(channel, msg);
            }
        } else {
            NettyFuture.received(msg, channel);
        }
        super.receive(channel, message);
    }

    private static void handRequest(NettyChannel channel, AccessMessage msg) throws RemoteException {
        //返回的是HandlerWrapper，是对Handler的包装类，主要用于执行 handler 拦截器链
        Handler handler = MercuryRouter.router(msg.getCmd());
        Object result;
        try {
            result = handler.handle(channel, msg);
        } catch (RemoteException e) {
            result = e.getMessage();
        }
        if (result == null) {
            return;
        }
        //返回给客户端AccessResponse
        channel.send(AccessMessageUtils.createResponse(msg.getId(), msg.getCmd(), JSON.toJSONBytes(result)));
    }

}

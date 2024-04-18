package cn.bixin.sona.gateway.handler;

import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.common.AccessMessage;
import cn.bixin.sona.gateway.exception.RemoteException;
import cn.bixin.sona.gateway.interceptor.HandlerInterceptorChain;
import lombok.extern.slf4j.Slf4j;

/**
 * @author qinwei
 * <p>
 * 是下面这些业务处理handler 的包装类，
 * 主要用于执行 handler 拦截器链，类似于 spring mvc 里面的拦截器，可以在业务处理的前后增加一些额外的处理逻辑：
 * ChatRoomHandler
 * ClientPushHandler
 * LoginAuthHandler
 */
@Slf4j
public class HandlerWrapper implements Handler {

    private final Handler handler;

    private final HandlerInterceptorChain chain;

    public HandlerWrapper(String name, Handler handler) {
        this.handler = handler;
        this.chain = HandlerInterceptorChain.getHandlerInterceptorChain(name);
    }

    @Override
    public Object handle(NettyChannel channel, AccessMessage message) throws RemoteException {
        if (handler == null) {
            return null;
        }
        try {
            //校验请求是否合法
            if (!chain.applyPreHandle(channel, message)) {
                return null;
            }
            //真正执行业务逻辑的入口，根据不同的handler，执行不同的业务逻辑，也就是AbstractHandler的子类
            Object result = handler.handle(channel, message);
            chain.applyPostHandle(channel, message);
            chain.applyAfterHandle(channel, message, null);
            return result;
        } catch (Exception e) {
            chain.applyAfterHandle(channel, message, e);
            throw new RemoteException("HandlerInterceptorChain execute failure !", e);
        }
    }
}

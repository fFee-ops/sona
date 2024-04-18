package cn.bixin.sona.gateway.interceptor;


import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.common.AccessMessage;

/**
 * @author qinwei
 * <p>
 * 业务 handler 拦截器 ,在实现类上添加注解 {@link Interceptor}, 指定需要拦截的handler (默认拦截所有)
 * 并且支持下面几种方式排序
 * @see org.springframework.core.Ordered
 * @see org.springframework.core.annotation.Order
 * @see javax.annotation.Priority
 */
public interface HandlerInterceptor {

    /**
     * 在业务处理前进行拦截
     *
     * @param channel channel
     * @param message message
     * @return boolean
     * @throws Exception Exception
     */
    default boolean preHandle(NettyChannel channel, AccessMessage message) throws Exception {
        return true;
    }

    /**
     * 在业务处理后进行拦截
     *
     * @param channel channel
     * @param message message
     * @throws Exception Exception
     */
    default void postHandle(NettyChannel channel, AccessMessage message) throws Exception {

    }

    /**
     * 在业务处理器执行后，处理异常情况的机制
     *
     * @param channel channel
     * @param message message
     * @param ex      异常
     * @throws Exception Exception
     */
    default void afterHandle(NettyChannel channel, AccessMessage message, Exception ex) throws Exception {

    }

}

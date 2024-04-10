package cn.bixin.sona.gateway.channel.handler;

import cn.bixin.sona.gateway.channel.NettyChannel;
import cn.bixin.sona.gateway.exception.RemoteException;
import org.springframework.util.Assert;

/**
 * @author qinwei
 * 装饰者抽象基类，它要实现与原始对象相同的接口ChannelHandler，其内部持有一个ChannelHandler类型的引用，用来接收被装饰的对象
 * 剩下的
 * AccessChannelHandler
 * CatReportChannelHandler
 * DispatchChannelHandler
 * IdleChannelHandler
 * MercuryServerHandler
 * 都是各种装饰者类，他们都继承至装饰者基类
 * <p>
 * 如果不太理解，请看装饰者模式讲解：https://zhuanlan.zhihu.com/p/64584677
 */
public abstract class AbstractChannelHandler implements ChannelHandler {

    protected final ChannelHandler handler;

    public AbstractChannelHandler(ChannelHandler handler) {
        Assert.notNull(handler, "handler == null");
        this.handler = handler;
    }

    @Override
    public void connect(NettyChannel channel) throws RemoteException {
        handler.connect(channel);
    }

    @Override
    public void disconnect(NettyChannel channel) throws RemoteException {
        handler.disconnect(channel);
    }

    @Override
    public void send(NettyChannel channel, Object message) throws RemoteException {
        handler.send(channel, message);
    }

    @Override
    public void receive(NettyChannel channel, Object message) throws RemoteException {
        handler.receive(channel, message);
    }

    @Override
    public void caught(NettyChannel channel, Throwable exception) throws RemoteException {
        handler.caught(channel, exception);
    }

}

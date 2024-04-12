package cn.bixin.sona.gateway.concurrent;

import cn.bixin.sona.gateway.channel.support.ChannelEventTask;
import cn.bixin.sona.gateway.loadbalance.ConsistentHashLoadBalance;
import cn.bixin.sona.gateway.loadbalance.LoadBalance;
import cn.bixin.sona.gateway.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author qinwei
 * <p>
 * 自定义线程池，提升处理能力，并且可以保证单个 channel 的处理顺序
 * <p>
 * 对于 IM 场景下，需要严格保证单个 channel 的处理顺序，但不同channel 之间可以不用保证顺序
 * 线程池 OrderedChannelExecutor的详细设计理念如下：
 * <p>
 * <p>
 * <p>
 * 1. 每个channel 都会生成一个 channelId （这里并没有使用 Netty里面默认的 id，而是通过一定规则生成的，主要是因为某些业务处理上的需要）
 * <p>
 * <p>
 * 2. 通过对 channelId 做一致性哈希，将这个channel 中的所有请求都路由到同一个 MpscQueue 里面。（MpscQueue 是 JCTools里面提供的 多生产者单消费者模式的        lock free 队列，能保证并发安全并且性能极高，Netty 底层使用的队列就是这个）
 * <p>
 * <p>
 * 3. MpscQueue 会从线程池里挑选一个线程去执行任务，只有当前任务处理完成，才会再从队列里 poll 下一个任务执行
 * <p>
 * 4. 只要MpscQueue 里面有数据，就会一直霸占这个线程，等队列的任务都执行完了，才会将它放回线程池中
 * <p>
 * 目前 MpscQueue 和线程数量是 1：1 ，每个队列都能拿到一个线程，不需要任何等待
 * 设计的时候，没有参考 netty eventloop 那样将 Selector 和线程 强绑定，因为我觉得线程是比较珍贵的资源，生产机器的配置是4核8g，线程多了性能也不一定能提升，而队列相对来说还好，只是耗点内存，项目中在节省内存方面也做了很多优化，大量使用池化技术，8g内存完全足够了，所以队列数量是可以大于线程数量的。
 * 当时想着后面实现一个分级队列，支持队列按照优先级划分，优先级越高则有更高的概率优先执行，优先级低的在系统负载过大时，则允许延迟处理、丢弃或者快速失败
 * 。不过之前压测，单机可以支持1.5w qps ，目前线上还远远没有达到这种量级，就暂时搁置了。
 */
public class OrderedChannelExecutor extends ThreadPoolExecutor {

    /**
     * 减少内存消耗
     */
    private static final AtomicIntegerFieldUpdater<SerialExecutor> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(SerialExecutor.class, "state");

    private LoadBalance<Executor> loadBalance;

    public OrderedChannelExecutor(int poolSize, String name) {
        //使用AffinityThreadFactory策略给进程绑定指定的cpu
        this(poolSize, poolSize, 0, TimeUnit.SECONDS, new SynchronousQueue<>(), new AffinityThreadFactory(name, AffinityStrategies.DIFFERENT_CORE), new DiscardPolicy());
    }

    public OrderedChannelExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        init();
    }

    private void init() {
        List<Executor> list = IntStream.range(0, 64).mapToObj(SerialExecutor::new).collect(Collectors.toList());
        loadBalance = new ConsistentHashLoadBalance<>(list);
    }

    @Override
    public void execute(Runnable command) {
        if (!(command instanceof ChannelEventTask)) {
            throw new RejectedExecutionException("command must be " + ChannelEventTask.class.getName() + "!");
        }
        ChannelEventTask task = (ChannelEventTask) command;
        getSerialExecutor(task).execute(command);
    }

    private void dispatch(Runnable task) {
        super.execute(task);
    }

    private Executor getSerialExecutor(ChannelEventTask task) {
        String channelId = task.getChannel().getChannelId();
        return loadBalance.selectNode(channelId);
    }

    private final class SerialExecutor implements Executor, Runnable {

        private final Queue<Runnable> tasks = PlatformDependent.newMpscQueue();

        public volatile int state;

        private final int sequence;

        SerialExecutor(int sequence) {
            this.sequence = sequence;
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);

            if (STATE_UPDATER.get(this) == 0) {
                dispatch(this);
            }
        }

        @Override
        public void run() {
            if (STATE_UPDATER.compareAndSet(this, 0, 1)) {
                try {
                    Thread thread = Thread.currentThread();
                    for (; ; ) {
                        final Runnable task = tasks.poll();

                        if (task == null) {
                            break;
                        }

                        boolean ran = false;
                        beforeExecute(thread, task);
                        try {
                            task.run();
                            ran = true;
                            afterExecute(task, null);
                        } catch (Exception e) {
                            if (!ran) {
                                afterExecute(task, e);
                            }
                            throw e;
                        }
                    }
                } finally {
                    STATE_UPDATER.set(this, 0);
                }

                if (STATE_UPDATER.get(this) == 0 && tasks.peek() != null) {
                    dispatch(this);
                }
            }
        }

        @Override
        public String toString() {
            return NetUtil.LOCAL_IP_ADDR + "|" + sequence;
        }
    }

}

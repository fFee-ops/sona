package cn.bixin.sona.gateway.loadbalance;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author qinwei
 * <p>
 * 使用了一致性哈希算法来进行负载均衡。
 * 在构造函数中，根据传入的节点列表，为每个节点生成若干个虚拟节点，并存储到一个有序映射中。
 * 提供了 selectNode 方法来选择一个节点，该方法将给定的键值哈希化后，通过 locate 方法定位到对应的节点。
 * locate 方法根据给定的哈希值定位节点，它通过有序映射的 ceilingEntry 方法获取大于等于指定哈希值的节点，如果找不到则返回第一个节点。
 */
public class ConsistentHashLoadBalance<T> implements LoadBalance<T> {

    private static final int REPLICA_NUM = 8;

    private static final String VIRTUAL_SEPARATOR = "|";

    private final TreeMap<Integer, T> virtualNodes = new TreeMap<>();

    private final HashStrategy strategy = new FnvHashStrategy();

    public ConsistentHashLoadBalance(List<T> list) {
        this(list, REPLICA_NUM);
    }

    public ConsistentHashLoadBalance(List<T> list, int replicaNum) {
        for (T t : list) {
            String key = t.toString();
            for (int i = 0; i < replicaNum; i++) {
                virtualNodes.put(strategy.hash(key + VIRTUAL_SEPARATOR + Integer.toHexString(i)), t);
            }
        }
    }

    @Override
    public T selectNode(String key) {
        return locate(strategy.hash(key));
    }

    private T locate(int hash) {
        Map.Entry<Integer, T> entry = virtualNodes.ceilingEntry(hash);
        if (entry == null) {
            entry = virtualNodes.firstEntry();
        }
        return entry.getValue();
    }

}

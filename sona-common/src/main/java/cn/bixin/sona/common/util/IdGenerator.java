package cn.bixin.sona.common.util;


import org.apache.dubbo.common.utils.NetUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * @author qinwei
 */
@Service
public class IdGenerator {

    // 总位数
    private static final int TOTAL_BITS = 64;
    // 时间戳位数
    private static final int EPOCH_BITS = 40;
    // 节点IP位数
    private static final int NODE_IP_BITS = 16;
    // 序列号位数
    private static final int SEQUENCE_BITS = 8;

    // 自定义起始时间（2015年1月1日0点0分0秒 UTC）
    private static final long CUSTOM_EPOCH = 1420070400000L;

    // 节点IP，取IP地址的后16位
    private static final int NODE_IP = createNodeId();

    // 序列号的最大值
    private static final int MAX_SEQUENCE = (int) (Math.pow(2, SEQUENCE_BITS) - 1);

    // 上一次的时间戳
    private long lastTimestamp = -1L;

    // 序列号
    private long sequence = 0L;

    // 生成ID
    public long id() {
        long currentTimestamp = timestamp();
        synchronized (this) {
            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    // 序列号耗尽，等待下一毫秒
                    currentTimestamp = waitNextMillis(currentTimestamp);
                }
            } else {
                // 重置序列号为下一毫秒的0
                sequence = 0;
            }
            lastTimestamp = currentTimestamp;
        }

        long id = currentTimestamp << (TOTAL_BITS - EPOCH_BITS);
        id |= (NODE_IP << (TOTAL_BITS - EPOCH_BITS - NODE_IP_BITS));
        id |= sequence;
        return id;
    }

    // 生成字符串形式的ID
    public String strId() {
        return String.valueOf(id());
    }

    // 获取指定时间戳对应的ID序号，注意！！不用于生成消息ID，仅用于查询
    public String getSpecialSequenceId(long timestamp) {
        long specialTimestamp = timestamp - CUSTOM_EPOCH;
        long id = specialTimestamp << (TOTAL_BITS - EPOCH_BITS);
        id |= (NODE_IP << (TOTAL_BITS - EPOCH_BITS - NODE_IP_BITS));
        return String.valueOf(id);
    }

    // 获取指定时间戳对应的ID序号，注意！！不用于生成消息ID，仅用于查询
    public String getSequenceIdWithoutNodeId(long timestamp) {
        long specialTimestamp = timestamp - CUSTOM_EPOCH;
        long id = specialTimestamp << (TOTAL_BITS - EPOCH_BITS);
        return String.valueOf(id);
    }

    // 获取 sequenceId 对应 时间戳
    public long getTimestampFromSequenceId(long sequenceId) {
        return (sequenceId >> (TOTAL_BITS - EPOCH_BITS)) + CUSTOM_EPOCH;
    }

    // 获取当前时间戳
    private static long timestamp() {
        return Instant.now().toEpochMilli() - CUSTOM_EPOCH;
    }

    // 创建节点ID，取IP地址的后16位
    private static int createNodeId() {
        String[] split = NetUtils.getLocalHost().split("\\.");
        return (Integer.parseInt(split[2]) << 8) + Integer.parseInt(split[3]);
    }

    // 等待下一毫秒
    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }
}
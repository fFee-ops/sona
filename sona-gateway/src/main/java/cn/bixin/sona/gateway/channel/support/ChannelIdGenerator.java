package cn.bixin.sona.gateway.channel.support;

import cn.bixin.sona.gateway.util.NetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author qinwei
 */
@Slf4j
public final class ChannelIdGenerator {

    private static final String CH_ID_SEPARATOR = "|";

    private static final AtomicInteger SEQ = new AtomicInteger();

    private ChannelIdGenerator() {
    }

    public static String generateChannelId(InetSocketAddress remoteAddr, long timestamp) {
        // 初始化一个容量为96的StringBuilder对象
        StringBuilder sb = new StringBuilder(96);
        // 添加本地IP地址
        sb.append(NetUtil.LOCAL_IP_ADDR);
        // 添加分隔符
        sb.append(CH_ID_SEPARATOR);
        // 获取并添加远程IP地址
        sb.append(remoteAddr.getAddress().getHostAddress());
        // 添加分隔符
        sb.append(CH_ID_SEPARATOR);
        // 获取并添加远程端口
        sb.append(remoteAddr.getPort());
        // 添加分隔符
        sb.append(CH_ID_SEPARATOR);
        // 添加时间戳
        sb.append(timestamp);
        // 添加分隔符
        sb.append(CH_ID_SEPARATOR);
        // 获取并添加当前序列号的十六进制字符串表示
        sb.append(Integer.toHexString(SEQ.getAndIncrement()));
        // 返回拼接好的字符串
        return sb.toString();
    }

}

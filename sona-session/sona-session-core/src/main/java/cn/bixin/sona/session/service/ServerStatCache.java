package cn.bixin.sona.session.service;

import cn.bixin.sona.session.channel.ChannelIdInfo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author qinwei
 */
@Service
public class ServerStatCache {

    private static final Logger log = LoggerFactory.getLogger(ServerStatCache.class);

    /**
     * 长时间没有更新stat信息的server，视为无效。server信息过期时间
     */
    @Value("${server.stat.outdate.seconds:30}")
    private long serverStatOutdateSeconds;

    /**
     * 长时间没有更新stat信息的server，视为无效。是否继续保留server信息过期的连接
     */
    @Value("${server.stat.outdate.keep:false}")
    private boolean serverStatOutdateKeep;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private volatile Map<String, ServerStat> serverStats = new ConcurrentHashMap<>();

    public static class ServerStat {

        @JSONField(name = "tm")
        private long reportTime;

        private long startTime;

        private Integer authConn;

        private Integer unAuthConn;

        public long getReportTime() {
            return reportTime;
        }

        public void setReportTime(long reportTime) {
            this.reportTime = reportTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public Integer getAuthConn() {
            return authConn;
        }

        public void setAuthConn(Integer authConn) {
            this.authConn = authConn;
        }

        public Integer getUnAuthConn() {
            return unAuthConn;
        }

        public void setUnAuthConn(Integer unAuthConn) {
            this.unAuthConn = unAuthConn;
        }
    }

    public ServerStat findByServerId(String serverId) {
        return serverStats.get(serverId);
    }

    public boolean judgeChannelValid(String channelId) {
        ChannelIdInfo channelIdInfo = ChannelIdInfo.parseChannelId(channelId);
        return judgeChannelValid(channelIdInfo, channelId);
    }

    /**
     * 判断连接是否有效
     * 工作原理如下：
     * <p>
     * 1. 首先，它检查`connTime`是否为`null`。`connTime`是连接的创建时间。
     * <p>
     * 2. 然后，它检查`connTime`是否小于`ServerStat`的`startTime`。`ServerStat`是服务器的状态信息，`startTime`是服务器启动的时间。
     * <p>
     * 3. 如果`connTime`为`null`或者小于`ServerStat`的`startTime`，那么它会返回`false`，表示连接无效。这是因为如果一个连接的创建时间早于服务器的启动时间，那么这个连接肯定是在服务器上次启动之前创建的，所以这个连接已经无效了。
     * <p>
     * 这段代码的主要目的是确保连接是有效的，即连接的服务器是在线的，且服务器没有重启。
     */
    public boolean judgeChannelValid(ChannelIdInfo channelIdInfo, String channelId) {
        if (channelIdInfo == null) {
            log.warn("judgeChannelValid, InvalidChannelId, channelId={}", channelId);
            return false;
        }

        String serverId = channelIdInfo.getServerId();
        Long connTime = Long.valueOf(channelIdInfo.getTimestamp());
        ServerStat stat = findByServerId(serverId);
        if (stat == null) {
            if (connTime == null || System.currentTimeMillis() - connTime > serverStatOutdateSeconds * 1000L) {
                log.info("judgeChannelValid, ServerStatNotFound, connTime={}, serverId={}, serverStat=null", connTime, serverId);
                return serverStatOutdateKeep;
            }
            return true;
        }
        if (connTime == null || connTime < stat.getStartTime()) {
            log.info("judgeChannelValid(), ServerRestart, connTime={}, serverStartTime={}", connTime, stat.getStartTime());
            return false;
        }
        if (System.currentTimeMillis() - stat.getReportTime() > serverStatOutdateSeconds * 1000L) {
            log.info("judgeChannelValid(), ServerStatOutdated, serverReportTime={}", stat.getReportTime());
            return serverStatOutdateKeep;
        }
        return true;
    }

    @Scheduled(fixedRate = 5000)
    public void refreshServerStats() {
        Map<String, ServerStat> newStats = new ConcurrentHashMap<>();
        HashOperations<String, String, String> opsForHash = redisTemplate.opsForHash();
        Map<String, String> serverStat = opsForHash.entries("server_stat");
        if (serverStat.isEmpty()) {
            log.error("refreshServerStats fail! serverStat is empty");
            return;
        }

        for (Map.Entry<String, String> entry : serverStat.entrySet()) {
            ServerStat stat = JSON.parseObject(entry.getValue(), ServerStat.class);
            newStats.put(entry.getKey(), stat);

            ServerStat oldStat = serverStats.get(entry.getKey());
            if (oldStat != null && oldStat.startTime != stat.startTime) {
                log.info("refreshServerStats(), startTime changed: server={}, old={}, new={}", entry.getKey(), oldStat.startTime, stat.startTime);
            }
        }
        if (!newStats.isEmpty()) {
            log.info("refreshServerStats, newStats={}", JSON.toJSONString(newStats));
            serverStats = newStats;
        }
    }
}

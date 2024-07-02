package cn.bixin.sona.server.im.aop;

import cn.bixin.sona.api.im.enums.PriorityEnum;
import cn.bixin.sona.api.im.request.RoomMessageRequest;
import cn.bixin.sona.server.im.ack.MessageArrivalService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * @author qinwei
 */
@Order
@Component
@Aspect
public class AckMessageInterceptor {

    @Resource
    private MessageArrivalService messageArrivalService;

    @Around("execution(* cn.bixin.sona.server.im.service.MercurySendService.sendP2pMessage(..))")
    public Object handleAckP2pMessage(ProceedingJoinPoint pjp) throws Throwable {
        Arrays.stream(pjp.getArgs())
                .filter(RoomMessageRequest.class::isInstance)
                .findFirst()
                .map(RoomMessageRequest.class::cast)
                .filter(request -> PriorityEnum.HIGH == request.getPriority())
                .ifPresent(request -> messageArrivalService.sendChatRoomAckMessage(request));
        return pjp.proceed();
    }

    /**
     * 这个方法是一个环绕通知，用于在发送聊天室消息的方法执行前后进行一些处理。
     * 如果有高优先级的消息，那么就会发送一个ACK消息。
     *ACK消息在cn.bixin.sona.server.im.listener.AckMessageListener#processNeedAckMessage进行处理
     * @param pjp ProceedingJoinPoint对象，用于获取被通知方法的信息，以及控制被通知方法的执行。
     * @return 被通知方法的返回值。
     * @throws Throwable 如果被通知方法或通知方法抛出异常，那么这个异常会被抛出。
     */
    @Around("execution(* cn.bixin.sona.server.im.service.MercurySendService.sendChatRoomMessage(..))")
    public Object handleAckRoomMessage(ProceedingJoinPoint pjp) throws Throwable {
        Arrays.stream(pjp.getArgs())
                .filter(RoomMessageRequest.class::isInstance)
                .findFirst()
                .map(RoomMessageRequest.class::cast)
                .filter(request -> PriorityEnum.HIGH == request.getPriority())
                .map(this::getChatRoomAck)
                .ifPresent(request -> messageArrivalService.sendChatRoomAckMessage(request));
        return pjp.proceed();
    }

    private RoomMessageRequest getChatRoomAck(RoomMessageRequest request) {
        Set<Long> ackUids = messageArrivalService.getNeedAckUids(request);
        if (CollectionUtils.isEmpty(ackUids)) {
            return null;
        }
        request.setAckUids(new ArrayList<>(ackUids));
        return request;
    }

}

package cn.bixin.sona.server.im.handler;

import cn.bixin.sona.api.im.enums.PriorityEnum;
import cn.bixin.sona.api.im.request.RoomMessageRequest;
import cn.bixin.sona.common.dto.Response;
import cn.bixin.sona.server.im.flow.FlowStrategy;
import org.springframework.stereotype.Service;

/**
 * @author qinwei
 */
@Service
public class MediumHighLevelHandler extends AbstractChatRoomHandler {

    @Override
    public Response<Boolean> doHandle(RoomMessageRequest request) {
        //中高等级消息有频控，通过直接发送，否则发 kafka
        if (FlowStrategy.PASS == getStrategy(request)) {
            return chatRoomMessageManager.sentChatRoomMessage(request);
        }
        //当系统负载较高时，直接发送消息可能会对系统产生较大压力，影响系统的稳定性。而将消息发送到Kafka，可以将消息的处理过程异步化，减轻系统的压力
        return sendKafka(request);
    }

    @Override
    public boolean support(RoomMessageRequest request) {
        return PriorityEnum.MEDIUM_HIGH == request.getPriority();
    }

    @Override
    public int order() {
        return 2;
    }
}

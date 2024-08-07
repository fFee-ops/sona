package cn.bixin.sona.api.im;


import cn.bixin.sona.api.im.request.GroupMessageRequest;
import cn.bixin.sona.api.im.request.RoomMessageRequest;
import cn.bixin.sona.common.annotation.CommonExecutor;
import cn.bixin.sona.common.dto.Response;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 消息发送统一入口
 *
 * @author qinwei
 */
public interface RouterRoomMessageService {

    /**
     * 当前房间是聊天室模式，比如直播间这种，一条消息发送给房间内的所有在线用户
     * @param request 消息内容
     * @return 消息ID
     */
    @CommonExecutor(desc = "发送聊天室消息", printParam = true, printResponse = true)
    Response<String> sendChatRoomMessage(@NotNull RoomMessageRequest request);

    /**
     * 当前房间是群组模式，比如游戏房这种，一条消息发送给房间内的所有用户
     * @param request 消息内容
     * @return 消息ID
     */
    @CommonExecutor(desc = "发送群组消息", printParam = true, printResponse = true)
    Response<String> sendGroupMessage(@NotNull GroupMessageRequest request);

    @CommonExecutor(desc = "批量发送聊天室消息", printParam = true)
    Response<Boolean> sendChatRoomMessages(@Size(min = 1, max = 100) List<RoomMessageRequest> list);

    @CommonExecutor(desc = "批量发送群组消息", printParam = true)
    Response<Boolean> sendGroupMessages(@Size(min = 1, max = 100) List<GroupMessageRequest> list);

    @CommonExecutor(desc = "发送聊天室点对点消息", printParam = true, printResponse = true)
    Response<String> sendChatRoomMessageToUids(@NotNull RoomMessageRequest request, @Size(min = 1, max = 100) List<Long> uids);

    @CommonExecutor(desc = "广播消息给所有聊天室", printParam = true, printResponse = true)
    Response<String> boardCastChatRoomMessage(@NotNull RoomMessageRequest request);

    @CommonExecutor(desc = "广播消息给指定聊天室", printParam = true, printResponse = true)
    Response<String> boardCastChatRoomMessage(@NotNull RoomMessageRequest request, @Size(min = 1, max = 100) List<String> roomIds);

}

package com.unique.module.im;


import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.unique.core.config.ApplicationContextHolder;
import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.core.utils.BaseUtil;
import com.unique.core.utils.UserUtil;
import com.unique.module.entity.po.ModuleUser;
import com.unique.module.utils.UserCacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.websocket.common.WsRequest;
import org.tio.websocket.server.handler.IWsMsgHandler;

/**
 * 默认的websocket消息处理
 *
 * @author UNIQUE
 */
@Slf4j
@Component
public class WsMsgHandler implements IWsMsgHandler {

    /**
     * <li>对httpResponse参数进行补充并返回，如果返回null表示不想和对方建立连接，框架会断开连接，如果返回非null，框架会把这个对象发送给对方</li>
     * <li>注：请不要在这个方法中向对方发送任何消息，因为这个时候握手还没完成，发消息会导致协议交互失败。</li>
     * <li>对于大部分业务，该方法只需要一行代码：return httpResponse;</li>
     */
    @Override
    public HttpResponse handshake(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) throws Exception {
        String token = httpRequest.getParam("token");
        String roomId = httpRequest.getParam("roomId");
        String mapId = httpRequest.getParam("mapId");
        //1.房间内的对战信息
        if (StrUtil.isNotEmpty(token) && StrUtil.isNotEmpty(roomId)) {
            Object obj = UserCacheUtil.getRedis().get(token);
            if (obj instanceof ModuleUser) {
                String bsKey = ImConst.GROUP_Key ;
                Tio.bindUser(channelContext, ((ModuleUser) obj).getId().toString());
//                //查询进行的房间
//                GameRoom gameRoom = ApplicationContextHolder.getBean(GameRoomService.class).queryById(Long.valueOf(roomId));
//                if (ObjectUtil.isNotEmpty(gameRoom) && ObjectUtil.isNotEmpty(gameRoom.getMapId()) && gameRoom.getMapId().equals(Long.valueOf(mapId))) {
//                    bsKey = bsKey + gameRoom.getId() + "-" + gameRoom.getMapId();
//                    Tio.bindBsId(channelContext, bsKey);
//                    return httpResponse;
//                }
            }
        }
        return null;
    }

    /**
     * 握手成功后触发该方法
     */
    @Override
    public void onAfterHandshaked(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) throws Exception {
        //绑定到群组，后面会有群发
        if (UserCacheUtil.getRedis().hashMapKey(channelContext.getBsId(),channelContext.userid)) {
            //绑定
            Tio.bindGroup(channelContext.tioConfig, channelContext.userid, channelContext.getBsId());
        }
    }

    /**
     * <li>当收到Opcode.BINARY消息时，执行该方法。也就是说如何你的ws是基于BINARY传输的，就会走到这个方法</li>
     */
    @Override
    public Object onBytes(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) throws Exception {
        return null;
    }

    /**
     * 当收到Opcode.CLOSE时，执行该方法，业务层在该方法中一般不需要写什么逻辑，空着就好
     */
    @Override
    public Object onClose(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) throws Exception {
        return null;
    }

    /**
     * <li>当收到Opcode.TEXT消息时，执行该方法。也就是说如何你的ws是基于TEXT传输的，就会走到这个方法</li>
     */
    @Override
    public Object onText(WsRequest wsRequest, String text, ChannelContext channelContext) throws Exception {
        if (BaseUtil.isJSONObject(text)) {
            try {
                long userId = Long.parseLong(channelContext.userid);
                SimpleUser userInfo = UserCacheUtil.getUserInfo(userId);
                UserUtil.setUser(userInfo);

                CommandUtil.parseText(text, channelContext);
            } finally {
                UserUtil.removeUser();
            }
        }
        return null;
    }
}

package com.unique.module.im;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.unique.module.utils.UserCacheUtil;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 处理ws中的信息
 * @author UNIQUE
 * @date 2024/10/16
 */
public class CommandUtil {

    /**
     * 处理ws数据
     *
     * @param text           文本
     * @param channelContext 当前ws对象
     */
    public static void parseText(String text, ChannelContext channelContext){
        JSONObject object = JSON.parseObject(text);
        int cmd = object.getIntValue("cmd");
        CmdEnum imCommand = CmdEnum.valueOf(cmd);
        switch (imCommand) {
            case GAME_ROOM_END:
            case GAME_ROOM_START:
            case GAME_ROOM_USER_JOIN:
            case GAME_ROOM_USER_OUT:
            case GAME_ROOM_FIGHT_PUSH:
            case GAME_ROOM_MSG_PUSH:
            case GAME_ROOM_ERROR_STATE:
                BsParse(imCommand,object,channelContext);
                break;
        }
    }

    /**
     * bs消息处理
     *
     * @param object         json
     * @param channelContext 当前ws对象
     */
    private static void BsParse(CmdEnum imCommand,JSONObject object, ChannelContext channelContext) {
        long userId = Long.parseLong(channelContext.userid);
        long roomId = object.getLongValue("roomId");
        long mapId = object.getLongValue("mapId");
        //返回参数
        Map<String,Object> msg = new HashMap<>();
        msg.put("cmd",imCommand.getCode());
        msg.put("msg",imCommand.getMsg());
        //组内人数
        int groupCount = ObjectUtil.isNotEmpty(Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId())) && ObjectUtil.isNotEmpty(Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId()).getObj())?Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId()).getObj().size():0;
        msg.put("roomCount",groupCount);
        switch (imCommand) {
            case GAME_ROOM_END:
                //断开连接
                Tio.removeGroup(channelContext.tioConfig, channelContext.getBsId(), imCommand.getMsg());
                //房间关闭，执行后续逻辑
                break;
            case GAME_ROOM_START:
                //游戏开始
                WsPacket wsResponseRoom = WsPacket.fromText(imCommand, channelContext.userid,msg);
                Tio.sendToGroup(channelContext.tioConfig, channelContext.getBsId(), wsResponseRoom);
                break;
            case GAME_ROOM_USER_JOIN:
                msg.put("msg", UserCacheUtil.getUserInfo(userId).getRealname() + imCommand.getMsg());
                //加入房间
                Tio.bindGroup(channelContext.tioConfig, channelContext.userid, channelContext.getBsId());
                //方便重连
                UserCacheUtil.getRedis().put(channelContext.getBsId(), channelContext.userid,1);
                //组内人数
                int groupCountJoin = ObjectUtil.isNotEmpty(Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId())) && ObjectUtil.isNotEmpty(Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId()).getObj())?Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId()).getObj().size():0;
                msg.put("roomCount",groupCountJoin);
                //推送消息
                WsPacket wsRespJoin = WsPacket.fromText(imCommand, channelContext.userid,msg);
                Tio.sendToGroup(channelContext.tioConfig, channelContext.getBsId(), wsRespJoin);
                break;
            case GAME_ROOM_USER_OUT:
                msg.put("msg",UserCacheUtil.getUserInfo(userId).getRealname() + imCommand.getMsg());
                //退出房间
                Tio.unbindGroup(channelContext.tioConfig, channelContext.userid, channelContext.getBsId());
                //移除组内
                UserCacheUtil.getRedis().hdel(channelContext.getBsId(), channelContext.userid);
                //组内人数
                int groupCountOut = ObjectUtil.isNotEmpty(Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId())) && ObjectUtil.isNotEmpty(Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId()).getObj())?Tio.getByGroup(channelContext.tioConfig, channelContext.getBsId()).getObj().size():0;
                msg.put("roomCount",groupCountOut);
                //推送消息
                WsPacket wsRespOut = WsPacket.fromText(imCommand, channelContext.userid,msg);
                Tio.sendToGroup(channelContext.tioConfig, channelContext.getBsId(), wsRespOut);
                break;
            case GAME_ROOM_FIGHT_PUSH:
                //战斗推送
            case GAME_ROOM_MSG_PUSH:
                msg.put("msg",object.toJSONString());
                //推送消息
                WsPacket wsResponse = WsPacket.fromText(imCommand, channelContext.userid,msg);
                Tio.sendToGroup(channelContext.tioConfig, channelContext.getBsId(), wsResponse);
                break;
            case GAME_ROOM_ERROR_STATE:
                break;
        }
        //入库

    }
}

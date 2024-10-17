package com.unique.module.im;

import lombok.Getter;


/**
 * @author UNIQUE
 * @date 2024/10/17
 */
@Getter
public enum CmdEnum {

    GAME_ROOM_END(10000,"游戏结束"),
    GAME_ROOM_START(10001,"游戏开始"),

    GAME_ROOM_USER_JOIN(10002,"加入房间"),
    GAME_ROOM_USER_OUT(10003,"退出房间"),
    //战斗推送
    GAME_ROOM_FIGHT_PUSH(10004,"战斗推送"),
    //消息推送
    GAME_ROOM_MSG_PUSH(10005,"消息推送"),

    /**
     * 出现错误
     */
    GAME_ROOM_ERROR_STATE(19000,"参数异常");


    private final int code;
    private final String msg;

    CmdEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }


    public static CmdEnum valueOf(int value) {
        for (CmdEnum command : CmdEnum.values()) {
            if (command.getCode() == value) {
                return command;
            }
        }
        return GAME_ROOM_ERROR_STATE;
    }
}

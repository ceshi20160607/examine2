package com.unique.module.im;


import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import org.tio.websocket.common.Opcode;
import org.tio.websocket.common.WsResponse;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * @author UNIQUE
 * @date 2024/10/17
 */
@Getter
public class WsPacket extends WsResponse {
    private static final long serialVersionUID = 1L;

    private CmdEnum command;

    private String userId;

    public WsPacket(CmdEnum command, String userId) {
        this.command = command;
        this.userId = userId;
    }

    public static WsPacket fromText(CmdEnum command, String userId, Map<String, Object> map) {
        return fromText(command, userId, CHARSET_NAME, map);
    }

    public static WsPacket fromText(CmdEnum command, String userId, String charset, Map<String, Object> map) {
        WsPacket wsPacket = new WsPacket(command, userId);
        if (map == null) {
            map = new LinkedHashMap<>();
        }
        JSONObject object = new JSONObject(map);
        object.fluentPut("cmd", command.getCode()).fluentPut("userId", userId);
        wsPacket.setBody(object.toJSONString().getBytes(Charset.forName(charset)));
        wsPacket.setWsOpcode(Opcode.TEXT);
        return wsPacket;
    }

    @Override
    public String toString() {
        return new String(this.getBody(), StandardCharsets.UTF_8);
    }

    public void setCommand(CmdEnum command) {
        this.command = command;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

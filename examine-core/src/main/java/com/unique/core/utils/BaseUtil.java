package com.unique.core.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONValidator;
import com.unique.core.config.ApproveConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * @author ceshi
 * @description 基础工具类
 * @date 2022/11/26 9:23
 */

public class BaseUtil {

    /**
     * 获取long类型的id，雪花算法
     * @return id
     */
    public static Long getNextId(){
        return IdUtil.getSnowflake(ApproveConfig.approveProperties.workerId, ApproveConfig.approveProperties.datacenterId).nextId();
    }
    /**
     * 获取当前年月的字符串
     *
     * @return yyyyMMdd
     */
    public static String getDate() {
        return DateUtil.format(new Date(), DatePattern.PURE_DATE_FORMAT);
    }

    /**
     * 获取request对象
     *
     * @return request
     */

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
        return Optional.ofNullable(attributes).map(ServletRequestAttributes::getRequest).orElse(null);
    }
    /**
     * 获取response对象
     *
     * @return response
     */
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
        return Optional.ofNullable(attributes).map(ServletRequestAttributes::getResponse).orElse(null);
    }

    /**
     * 获取当前是否是windows系统
     *
     * @return true代表为真
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 判断字符串是否是json数组
     *
     * @param str 字符串
     * @return true代表是
     */
    public static boolean isJSONArray(String str) {
        if (str == null) {
            return false;
        }
        return JSONValidator.from(str).getType() == JSONValidator.Type.Array;
    }

    /**
     * 判断字符串是否是json对象
     *
     * @param str 字符串
     * @return true代表是
     */
    public static boolean isJSONObject(String str) {
        if (str == null) {
            return false;
        }
        return JSONValidator.from(str).getType() == JSONValidator.Type.Object;
    }

    /**
     * 判断字符串是否是json
     *
     * @param str 字符串
     * @return true代表是
     */
    public static boolean isJSON(String str) {
        return isJSONArray(str) || isJSONObject(str);
    }

    /**
     * 判断对象是否是array
     *
     * @param value 字段值
     * @return true代表是
     */
    public static boolean isArray(Object value) {
        return value instanceof Collection;
    }

    /**
     * 判断对象是否是map对象
     *
     * @param value 字段值
     * @return true代表是
     */
    public static boolean isMap(Object value) {
        return value instanceof Map;
    }

    /**
     * 判断对象是否可转换为json
     * 暂未考虑java bean
     * @param value 字段值
     * @return true代表是
     */
    public static boolean isJSON(Object value) {
        return isArray(value) || isMap(value);
    }





//
//    public static void main(String[] args) {
//        try {
//            //基础图片
//            BufferedImage base = ImageIO.read(new File("C:\\Users\\ceshi\\Desktop\\base.png"));
//            //添加图片
//            BufferedImage add = ImageIO.read(new File("C:\\Users\\ceshi\\Desktop\\add.png"));
//            //新的画布
//            BufferedImage newImage = new BufferedImage(base.getWidth(), base.getHeight()+200, BufferedImage.TYPE_INT_ARGB);
//            //画布上绘制图片
//            Graphics2D graphics = newImage.createGraphics();
//            graphics.drawImage(base,0,0,null);
//
//            //添加图片
//            graphics.drawImage(add,754,625,null);
//            graphics.drawImage(add,754,625,18, 18*add.getHeight()/add.getWidth(),null);
//            graphics.drawImage(add,0,1370,18, 18*add.getHeight()/add.getWidth(),null);
//            //关闭画布 释放绘图资源
//            graphics.dispose();
//
//            String newMapName = "C:\\Users\\ceshi\\Desktop\\newname.png";
//            //导出图片
//            ImageIO.write(newImage,"png",new File(newMapName));
//
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

//    /**
//     * 生成背景图
//     * @param mapId
//     * @param list
//     */
//    public void buildNewBaseMapPng(Long mapId, List<GameFile> list) {
//        int retWith = 18*list.size();
//        int retHeight = 100;
//        int retPublic = 0;
//        try {
//            //新的画布
//            BufferedImage newImage = new BufferedImage(retWith, retHeight, BufferedImage.TYPE_INT_ARGB);
//            //画布上绘制图片
//            Graphics2D graphics = newImage.createGraphics();
//            for (int i = 0; i < list.size(); i++) {
//                int startX = i*18;
//                //添加图片
//                UploadEntity entity = new UploadEntity();
//                entity.setPath(list.get(i).getPath());
//                InputStream inputStream = fileService.downFile(entity, getBucketName(list.get(i).getIsPublic()));
//                BufferedImage add = ImageIO.read(inputStream);
//                graphics.drawImage(add, startX, 0, 18, 18 * add.getHeight() / add.getWidth(), null);
//            }
//            graphics.dispose();
//            String newMapName =  mapId + ".png";
//            String newMapNamePath = getBucketName(retPublic) + "/" + newMapName;
//            //导出图片
//            File writeFile = new File(newMapNamePath);
//            ImageIO.write(newImage,"png", writeFile);
//
//            InputStream writeInputStream = new FileInputStream(writeFile);
//
//            // 上传到文件服务器
//
//            String batchId = IdUtil.simpleUUID();
//            Long fileId = BaseUtil.getNextId();
//            UploadEntity entity = new UploadEntity();
//            entity.setFileId(fileId.toString());
//            entity.setName(newMapName);
//            entity.setSize(writeFile.length());
//            fileService.uploadFile(writeInputStream, entity, getBucketName(retPublic));
//            if (Objects.equals(retPublic, Const.PUBLIC_KEY)) {
//                entity.setPath(entity.getUrl());
//            }
//            getUrl(entity, retPublic);
//            GameFile gameFile = buildFile(entity, fileId, batchId, retPublic, null);
//            resourceRepository.save(gameFile);
//
//            GameMap gameMap = ApplicationContextHolder.getBean(GameMapService.class).queryById(mapId);
//            gameMap.setSrcImage(entity.getPath());
//            ApplicationContextHolder.getBean(GameMapService.class).save(gameMap);
//
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
}

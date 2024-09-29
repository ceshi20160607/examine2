package com.unique.core.utils;

import cn.hutool.core.util.IdUtil;
import com.unique.core.config.ApproveConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

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

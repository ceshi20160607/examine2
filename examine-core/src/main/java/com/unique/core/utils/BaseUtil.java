package com.unique.core.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSONValidator;
import com.unique.core.config.ApproveConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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



//            //-------------------------多线程编排----------------
//            log.info("时间2111："+(System.currentTimeMillis()-l));
//            //标签
//            CompletableFuture<List<ProjectLabel>> cfLabels = CompletableFuture.supplyAsync(() -> {
//                return projectLabelService.listByIds(labelIds);
//            });
//            //人员
//            CompletableFuture<List<SimpleUser>> cfUsers = CompletableFuture.supplyAsync(() -> {
//                return UserCacheUtil.getSimpleUsers(allUserIds);
//            });
//            //状态
//            CompletableFuture<Map<Long,String>> cfEventStatus = CompletableFuture.supplyAsync(() -> {
//                List<ProjectEventStatus> eventStatusList = projectEventStatusService.queryEventStatusByProjectIds(null, null, allProjectIds);
//                return CollectionUtil.isEmpty(eventStatusList)?new HashMap<>():eventStatusList.stream().collect(Collectors.toMap(ProjectEventStatus::getId,ProjectEventStatus::getStatusName));
//            });
//            //迭代
//            CompletableFuture<Map<Long,String>> cfBelong = CompletableFuture.supplyAsync(() -> {
//                return projectTaskService.lambdaQuery().in(ProjectTask::getTaskId, allBelongTaskIds).list().stream().collect(Collectors.toMap(ProjectTask::getTaskId,ProjectTask::getName));
//            });
//            //关联
//            CompletableFuture<List<ProjectTaskRelation>> cfRelation = CompletableFuture.supplyAsync(() -> {
//                return projectTaskRelationService.lambdaQuery().in(ProjectTaskRelation::getTaskId, allTaskIds).list();
//            });
//            //关联--子关联
//            CompletableFuture<Map<Long, Map<Integer, List<Long>>>> cfRelationMap = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                return x.stream().collect(Collectors.groupingBy(ProjectTaskRelation::getTaskId, Collectors.groupingBy(ProjectTaskRelation::getType, Collectors.mapping(ProjectTaskRelation::getRelationId, Collectors.toList()))));
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubModule = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.MODULE.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = adminService.queryModuleInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubCustomer = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.CUSTOMER.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = crmService.queryCustomerInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubContacts = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.CONTACTS.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = crmService.queryContactsInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubBusiness = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.BUSINESS.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = crmService.queryBusinessInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubContract = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.CONTRACT.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = crmService.queryContractInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubReceivables = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.RECEIVABLES.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = crmService.queryReceivablesInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//            //关联--子关联
//            CompletableFuture<Map<Long, SimpleCrmEntity>> cfRelationSubQuotation = cfRelation.thenCompose(x -> CompletableFuture.supplyAsync(()->{
//                Set<Long> ids = x.stream().filter(f->CrmRelationTypeEnum.QUOTATION.getType()==f.getType()).map(ProjectTaskRelation::getRelationId).collect(Collectors.toSet());
//                List<SimpleCrmEntity> data = crmService.queryQuotationInfo(ids).getData();
//                return CollectionUtil.isNotEmpty(data)?data.stream().collect(Collectors.toMap(SimpleCrmEntity::getId,m->m)):new HashMap<>();
//            }));
//
//            log.info("时间2113："+(System.currentTimeMillis()-l));
//            /// t1  t2  t3  t4  全部并行执行结束时结束任务
//            CompletableFuture.allOf(cfLabels, cfUsers, cfEventStatus,cfBelong,cfRelationMap,cfRelationSubModule,cfRelationSubCustomer,cfRelationSubContacts,cfRelationSubBusiness,cfRelationSubContract,cfRelationSubReceivables,cfRelationSubQuotation).join();
//            // 所有任务完成后执行的操作
//            List<ProjectLabel> projectLabels = cfLabels.get();
//            List<SimpleUser> simpleUsers = cfUsers.get();
//            Map<Long,String> eventProjectStatusListMap = cfEventStatus.get();
//            Map<Long,String> allBelongTaskList = cfBelong.get();
//            Map<Long, Map<Integer, List<Long>>> finalAllRelationTaskList = cfRelationMap.get();
//            Map<Long,SimpleCrmEntity> moduleList = cfRelationSubModule.get();
//            Map<Long,SimpleCrmEntity> customerList = cfRelationSubCustomer.get();
//            Map<Long,SimpleCrmEntity> contactsList = cfRelationSubContacts.get();
//            Map<Long,SimpleCrmEntity> businessList = cfRelationSubBusiness.get();
//            Map<Long,SimpleCrmEntity> contractList = cfRelationSubContract.get();
//            Map<Long,SimpleCrmEntity> receivablesList = cfRelationSubReceivables.get();
//            Map<Long,SimpleCrmEntity> quotationList = cfRelationSubQuotation.get();
//            log.info("时间2114："+(System.currentTimeMillis()-l));
//            //-----------------------------------------




}

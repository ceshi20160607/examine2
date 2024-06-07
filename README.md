# examine2
an examine system

# 权限
1.单用户角色模式
2.多用户角色，单数据模式
3.多用户角色，多数据模式
4.单用户角色，单数据模式【数据具体到模块】

# 完善模块

# 添加记事本note--使用模块实现


List<ModuleField> fieldList = lambdaQuery()
.eq(ModuleField::getModuleId, id)
.eq(ModuleField::getAddFlag, IsOrNotEnum.ONE.getType())
.list();


    @ApiModelProperty("显示：0全部 1分配给我的 2我创建的 3我参与的")
    private Integer showFlag = 0;

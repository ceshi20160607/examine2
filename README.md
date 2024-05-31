# examine2
an examine system

# 完善模块

# 添加记事本note--使用模块实现


List<ModuleField> fieldList = lambdaQuery()
.eq(ModuleField::getModuleId, id)
.eq(ModuleField::getAddFlag, IsOrNotEnum.ONE.getType())
.list();
# mybatis generator enhanced plugins for mysql
### 包含3个插件和一个定制的中文注释生成器
* 批量插入插件 - com.zzh.mbg.plugin.MysqlBatchInsertPlugin
* 批量更新插件 - com.zzh.mbg.plugin.MysqlBatchUpdatePlugin
* 批量非空更新插件 - com.zzh.mbg.plugin.MysqlBatchUpdateSelectivePlugin
* 中文注释生成器 - com.zzh.mbg.GeneralCommentGenerator

***

> 插件配置
```xml
        <plugin type="com.zzh.mbg.plugin.MysqlBatchInsertPlugin" />
        <plugin type="com.zzh.mbg.plugin.MysqlBatchUpdatePlugin" />
        <plugin type="com.zzh.mbg.plugin.MysqlBatchUpdateSelectivePlugin" />
```

> 注释配置
```xml
        <commentGenerator type="com.zzh.mbg.GeneralCommentGenerator">
            <!--<property name="suppressAllComments" value="true" />-->
            <property name="suppressDate" value="true" />
            <property name="addRemarkComments" value="true" />
        </commentGenerator>
```
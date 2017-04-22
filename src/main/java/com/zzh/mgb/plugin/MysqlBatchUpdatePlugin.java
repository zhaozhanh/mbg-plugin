/**
 *    Copyright 2006-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.zzh.mgb.plugin;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.mybatis3.ListUtilities;
import org.mybatis.generator.logging.Log;
import org.mybatis.generator.logging.LogFactory;

import java.util.*;

import static org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities.getEscapedColumnName;
import static org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities.getParameterClause;
import static org.mybatis.generator.internal.util.StringUtility.escapeStringForJava;

/**
 * All rights reserved.
 * Created by zzh on 2017/4/16 22:25.
 */
public class MysqlBatchUpdatePlugin extends PluginAdapter {

    private static Log logger = LogFactory.getLog(MysqlBatchUpdatePlugin.class);

    private static final String BATCH_UPDATE = "batchUpdateByPrimaryKey";

    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    @Override
    public boolean clientGenerated(Interface interfaze, TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        List<IntrospectedColumn> pkColumns = introspectedTable.getPrimaryKeyColumns();
        String tableName = escapeStringForJava(introspectedTable.getFullyQualifiedTableNameAtRuntime());
        if (pkColumns == null || pkColumns.isEmpty()) {
            logger.warn("No primary key found for table: " + tableName);
            return true;
        }

        interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.UpdateProvider"));

        String modelName = introspectedTable.getTableConfiguration().getDomainObjectName();
        Method method = new Method(BATCH_UPDATE);
        FullyQualifiedJavaType type = new FullyQualifiedJavaType("java.util.List<" + modelName + ">");
        method.addParameter(new Parameter(type, "list"));
        method.setReturnType(FullyQualifiedJavaType.getIntInstance());

        FullyQualifiedJavaType providerType = new FullyQualifiedJavaType(introspectedTable.getMyBatis3SqlProviderType());
        StringBuilder sb = new StringBuilder();
        sb.append("@UpdateProvider(type=");
        sb.append(providerType.getShortName());
        sb.append(".class, method=\"");
        sb.append(BATCH_UPDATE);
        sb.append("\")");
        method.addAnnotation(sb.toString());

        interfaze.addMethod(method);
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);
        return true;
    }

    @Override
    public boolean providerGenerated(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        List<IntrospectedColumn> pkColumns = introspectedTable.getPrimaryKeyColumns();
        String tableName = escapeStringForJava(introspectedTable.getFullyQualifiedTableNameAtRuntime());
        if (pkColumns == null || pkColumns.isEmpty()) {
            logger.warn("No primary key found for table: " + tableName);
            return true;
        }

        Set<FullyQualifiedJavaType> importedTypes = new TreeSet<FullyQualifiedJavaType>();
        importedTypes.add(new FullyQualifiedJavaType("java.lang.StringBuilder"));
        importedTypes.add(FullyQualifiedJavaType.getNewListInstance());
        importedTypes.add(FullyQualifiedJavaType.getNewMapInstance());
        topLevelClass.addImportedTypes(importedTypes);

        Method method = new Method(BATCH_UPDATE);
        method.setVisibility(JavaVisibility.PUBLIC);
        String modelName = introspectedTable.getTableConfiguration().getDomainObjectName();
        method.addParameter(new Parameter(new FullyQualifiedJavaType("java.util.Map<String, List<" + modelName + ">>"), "map"));
        method.setReturnType(FullyQualifiedJavaType.getStringInstance());
        topLevelClass.addMethod(method);
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);

        method.addBodyLine(String.format("List<%s> list = map.get(\"list\");", modelName));

        // WHEN (list[i].column_name=#{list[i].columnName})
        StringBuilder whenBuilder = new StringBuilder("WHEN (");
        String primaryKeys = "(";
        String primaryKeyParams = "(";
        for (IntrospectedColumn introspectedColumn : introspectedTable.getPrimaryKeyColumns()) {
            String columnName = escapeStringForJava(getEscapedColumnName(introspectedColumn));
            String paramClause = getParameterClause(introspectedColumn, "list[@index].");
            whenBuilder.append(String.format("%s = %s and ", columnName, paramClause));
            primaryKeys += columnName + ",";
            primaryKeyParams += paramClause + ",";
        }
        String whenStr = whenBuilder.substring(0, whenBuilder.length() - 5) + ")";
        primaryKeys = primaryKeys.substring(0, primaryKeys.length() -1) + ")";
        primaryKeyParams = primaryKeyParams.substring(0, primaryKeyParams.length() - 1) + ")";

        // column_name = CASE
        List<String> cases = new ArrayList<String>();
        // <case, whenThen> map
        Map<String, String> caseWhens = new HashMap<String, String>();
        for (IntrospectedColumn introspectedColumn : ListUtilities.removeGeneratedAlwaysColumns(introspectedTable.getNonPrimaryKeyColumns())) {
            String columnName = escapeStringForJava(getEscapedColumnName(introspectedColumn));
            String caseStr = String.format("%s = CASE", columnName);
            String whenThenStr = whenStr + " THEN " + getParameterClause(introspectedColumn, "list[@index].");
            cases.add(String.format("%s = CASE", columnName));
            caseWhens.put(caseStr, whenThenStr);
        }

        method.addBodyLine("StringBuilder sql = new StringBuilder(\"UPDATE \").append(\"" + tableName + "\").append(\" SET \");");
        method.addBodyLine("String whenThen = \"\";");
        for (String caseStr : cases) {
            method.addBodyLine("sql.append(\"" + caseStr + "\").append(\"\\n\");");

            method.addBodyLine("whenThen = \"" + caseWhens.get(caseStr) + "\";");
            method.addBodyLine("for (int i=0; i < list.size(); i++) {");

            method.addBodyLine("sql.append(whenThen.replaceAll(\"@index\", String.valueOf(i)));");
            method.addBodyLine("sql.append(\"\\n\");");

            method.addBodyLine("}");

            method.addBodyLine("sql.append(\"END, \");");
            method.addBodyLine("");
        }
        method.addBodyLine("sql.replace(sql.length() - 2, sql.length() -1, \"\");");

        method.addBodyLine("sql.append(\"WHERE \").append(\"" + primaryKeys + "\").append(\" IN (\");");
        method.addBodyLine("String primaryKeyParams = \"" + primaryKeyParams + "\";");
        method.addBodyLine("for (int i=0; i < list.size(); i++) {");
        method.addBodyLine("sql.append(primaryKeyParams.replaceAll(\"@index\", String.valueOf(i)));");
        method.addBodyLine("sql.append(\",\");");
        method.addBodyLine("}");

        method.addBodyLine("return sql.substring(0, sql.length() - 1) + \")\";");

        return true;
    }
}
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
package com.zzh.mbg.plugin;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.codegen.mybatis3.ListUtilities;
import org.mybatis.generator.config.GeneratedKey;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities.getEscapedColumnName;
import static org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities.getParameterClause;
import static org.mybatis.generator.internal.util.StringUtility.escapeStringForJava;

/**
 * All rights reserved.
 * Created by zzh on 2017/4/16 18:54.
 */
public class MysqlBatchInsertPlugin extends PluginAdapter {

    static final String BATCH_INSERT = "batchInsert";
    static final String BUILDER_PREFIX = "sql.";

    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    @Override
    public boolean clientGenerated(Interface interfaze, TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.InsertProvider")); //$NON-NLS-1$

        String modelName = introspectedTable.getTableConfiguration().getDomainObjectName();
        Method method = new Method(BATCH_INSERT);
        FullyQualifiedJavaType type = new FullyQualifiedJavaType("java.util.List<" + modelName + ">");
        method.addParameter(new Parameter(type, "list"));
        method.setReturnType(FullyQualifiedJavaType.getIntInstance());

        FullyQualifiedJavaType providerType = new FullyQualifiedJavaType(introspectedTable.getMyBatis3SqlProviderType());
        StringBuilder sb = new StringBuilder();
        sb.append("@InsertProvider(type=");
        sb.append(providerType.getShortName());
        sb.append(".class, method=\"");
        sb.append(BATCH_INSERT);
        sb.append("\")");
        method.addAnnotation(sb.toString());

        GeneratedKey gk = introspectedTable.getGeneratedKey();
        if (gk != null) {
            addGeneratedKeyAnnotation(interfaze, method, gk, introspectedTable);
        }

        interfaze.addMethod(method);
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);
        return true;
    }

    @Override
    public boolean providerGenerated(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        Set<FullyQualifiedJavaType> importedTypes = new TreeSet<FullyQualifiedJavaType>();
        importedTypes.add(new FullyQualifiedJavaType("org.apache.ibatis.jdbc.SQL"));
        importedTypes.add(FullyQualifiedJavaType.getNewListInstance());
        importedTypes.add(FullyQualifiedJavaType.getNewMapInstance());

        Method method = new Method(BATCH_INSERT);
        method.setVisibility(JavaVisibility.PUBLIC);
        String modelName = introspectedTable.getTableConfiguration().getDomainObjectName();
        method.addParameter(new Parameter(new FullyQualifiedJavaType("java.util.Map<String, List<" + modelName + ">>"), "map"));
        method.setReturnType(FullyQualifiedJavaType.getStringInstance());
        topLevelClass.addMethod(method);
        context.getCommentGenerator().addGeneralMethodComment(method, introspectedTable);

        method.addBodyLine(String.format("List<%s> list = map.get(\"list\");", modelName));
        method.addBodyLine("SQL sql = new SQL();");

        method.addBodyLine(String.format("%sINSERT_INTO(\"%s\");", //$NON-NLS-1$
                BUILDER_PREFIX, escapeStringForJava(introspectedTable.getFullyQualifiedTableNameAtRuntime())));

        method.addBodyLine("");

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (IntrospectedColumn introspectedColumn : ListUtilities.removeIdentityAndGeneratedAlwaysColumns(introspectedTable.getAllColumns())) {
            String columnName = escapeStringForJava(getEscapedColumnName(introspectedColumn));
            columns.append(columnName).append(",");
            values.append(getParameterClause(introspectedColumn)).append(",");
        }

        method.addBodyLine("String values = \"" + values.substring(0, values.length() - 1) + "\";");
        method.addBodyLine("StringBuilder valuesList = new StringBuilder();");
        method.addBodyLine("for (int i = 0; i < list.size(); i++) {");
        method.addBodyLine("String valuesOfIndex = values.replaceAll(\"#\\\\{\", \"#{list[\" +i + \"].\");");
        method.addBodyLine("valuesList.append(\"(\").append(valuesOfIndex).append(\"),\");");

        method.addBodyLine("}");

        method.addBodyLine("String batchValues = valuesList.substring(1, valuesList.length() - 2);");
        method.addBodyLine(BUILDER_PREFIX + "VALUES(\"" + columns.substring(0, columns.length() - 1) + "\", batchValues);");

        method.addBodyLine("return sql.toString();");

        topLevelClass.addImportedTypes(importedTypes);
        return true;
    }

    protected void addGeneratedKeyAnnotation(Interface interfaze, Method method,
                                             GeneratedKey gk, IntrospectedTable introspectedTable) {
        StringBuilder sb = new StringBuilder();
        IntrospectedColumn introspectedColumn = introspectedTable.getColumn(gk.getColumn());
        if (introspectedColumn != null) {
            addGeneratedKeyImports(interfaze, gk, introspectedTable);

            if (gk.isJdbcStandard()) {
                interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.Options")); //$NON-NLS-1$
                sb.append("@Options(useGeneratedKeys=true,keyProperty=\""); //$NON-NLS-1$
                sb.append(introspectedColumn.getJavaProperty());
                sb.append("\")"); //$NON-NLS-1$
                method.addAnnotation(sb.toString());
            } else {
                interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.SelectKey")); //$NON-NLS-1$
                FullyQualifiedJavaType fqjt = introspectedColumn.getFullyQualifiedJavaType();
                interfaze.addImportedType(fqjt);
                sb.append("@SelectKey(statement=\""); //$NON-NLS-1$
                sb.append(gk.getRuntimeSqlStatement());
                sb.append("\", keyProperty=\""); //$NON-NLS-1$
                sb.append(introspectedColumn.getJavaProperty());
                sb.append("\", before="); //$NON-NLS-1$
                sb.append(gk.isIdentity() ? "false" : "true"); //$NON-NLS-1$ //$NON-NLS-2$
                sb.append(", resultType="); //$NON-NLS-1$
                sb.append(fqjt.getShortName());
                sb.append(".class)"); //$NON-NLS-1$
                method.addAnnotation(sb.toString());
            }
        }
    }

    protected void addGeneratedKeyImports(Interface interfaze, GeneratedKey gk, IntrospectedTable introspectedTable) {
        IntrospectedColumn introspectedColumn = introspectedTable.getColumn(gk.getColumn());
        if (introspectedColumn != null) {
            if (gk.isJdbcStandard()) {
                interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.Options")); //$NON-NLS-1$
            } else {
                interfaze.addImportedType(new FullyQualifiedJavaType("org.apache.ibatis.annotations.SelectKey")); //$NON-NLS-1$
                FullyQualifiedJavaType fqjt = introspectedColumn.getFullyQualifiedJavaType();
                interfaze.addImportedType(fqjt);
            }
        }
    }
}

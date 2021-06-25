/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.qlangtech.tis.plugin.ds.clickhouse;

import com.alibaba.citrus.turbine.Context;
import com.google.common.collect.Lists;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.ds.ColumnMetaData;
import com.qlangtech.tis.plugin.ds.DataSourceFactory;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-06-09 14:38
 **/
public class ClickHouseDataSourceFactory extends DataSourceFactory {

    private static final String JDBC_DRIVER = "ru.yandex.clickhouse.ClickHouseDriver";
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseDataSourceFactory.class);

    static {
        try {
            Class.forName(JDBC_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String DS_TYPE_CLICK_HOUSE = "ClickHouse";
    @FormField(identity = true, ordinal = 0, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.identity})
    public String name;

    @FormField(ordinal = 1, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
    public String jdbcUrl;
    @FormField(ordinal = 2, type = FormFieldType.INPUTTEXT, validate = {})
    public String username;
    @FormField(ordinal = 3, type = FormFieldType.PASSWORD, validate = {})
    public String password;


    @Override
    public String identityValue() {
        return this.name;
    }

    @Override
    public List<String> getTablesInDB() {

        List<String> tables = Lists.newArrayList();
        validateConnection(this.jdbcUrl, (conn) -> {

            DatabaseMetaData metaData = conn.getMetaData();

            ResultSet tablesResult = metaData.getTables(conn.getCatalog(), null, null, new String[]{"TABLE"});

            while (tablesResult.next()) {
                //System.out.println(tablesResult.getString(2) + "," + tablesResult.getString(3));
                if (!"default".equalsIgnoreCase(tablesResult.getString(2))) {
                    continue;
                }
                tables.add(tablesResult.getString(3));
            }

//            try (Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
//                try (ResultSet result = statement.executeQuery("show tables")) {
//                    while (result.next()) {
//                        tables.add(result.getString(1));
//                    }
//                }
//            }
        });
        return tables;
    }

    @Override
    public Connection getConnection(String jdbcUrl) throws SQLException {
        // return super.getConnection(jdbcUrl, username, password);
        return DriverManager.getConnection(jdbcUrl, StringUtils.trimToNull(username), StringUtils.trimToNull(password));
    }

    @Override
    public List<ColumnMetaData> getTableMetadata(String table) {
        return parseTableColMeta(table, this.jdbcUrl);
    }

    @TISExtension
    public static class DefaultDescriptor extends DataSourceFactory.BaseDataSourceFactoryDescriptor {
        @Override
        protected String getDataSourceName() {
            return DS_TYPE_CLICK_HOUSE;
        }

        @Override
        public boolean supportFacade() {
            return false;
        }

        private static Pattern PatternClickHouse = Pattern.compile("jdbc:clickhouse://(.+):\\d+/.*");


        public boolean validateJdbcUrl(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            Matcher matcher = PatternClickHouse.matcher(value);
            if (!matcher.matches()) {
                msgHandler.addFieldError(context, fieldName, "不符合格式规范:" + PatternClickHouse);
                return false;
            }
//            File rootDir = new File(value);
//            if (!rootDir.exists()) {
//                msgHandler.addFieldError(context, fieldName, "path:" + rootDir.getAbsolutePath() + " is not exist");
//                return false;
//            }
            return true;
        }

//        @Override
//        protected boolean validateDSFactory(IControlMsgHandler msgHandler, Context context, DataSourceFactory dsFactory) {
//            return super.validateDSFactory(msgHandler, context, dsFactory);
//        }
//
//        @Override
//        protected boolean validate(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
//
//            ParseDescribable<DataSourceFactory> ds = this.newInstance((IPluginContext) msgHandler, postFormVals.rawFormData, Optional.empty());
//
//            try {
//                List<String> tables = ds.instance.getTablesInDB();
//                // msgHandler.addActionMessage(context, "find " + tables.size() + " table in db");
//            } catch (Exception e) {
//                logger.warn(e.getMessage(), e);
//                msgHandler.addErrorMessage(context, e.getMessage());
//                return false;
//            }
//
//            return true;
//        }
    }
}
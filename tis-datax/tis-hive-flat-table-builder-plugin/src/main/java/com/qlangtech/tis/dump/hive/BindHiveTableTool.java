/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qlangtech.tis.dump.hive;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qlangtech.tis.fs.IPath;
import com.qlangtech.tis.fs.ITISFileSystem;
import com.qlangtech.tis.fullbuild.indexbuild.IDumpTable;
import com.qlangtech.tis.hdfs.impl.HdfsPath;
import com.qlangtech.tis.hive.HdfsFormat;
import com.qlangtech.tis.hive.HiveColumn;
import com.qlangtech.tis.manage.common.TisUTF8;
import com.qlangtech.tis.plugin.ds.ColumnMetaData;
import com.qlangtech.tis.plugin.ds.DataSourceMeta;
import com.qlangtech.tis.plugin.ds.DataType;
import com.qlangtech.tis.plugin.ds.TableNotFoundException;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 将hdfs上的数据和hive database中的表绑定
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2015年12月15日 下午4:49:42
 */
public class BindHiveTableTool {
    private static final Logger logger = LoggerFactory.getLogger(HiveTableBuilder.class);

    public static void bindHiveTables(DataSourceMeta engine, DataSourceMeta.JDBCConnection hiveConn, ITISFileSystem fileSystem, Map<EntityName
            , Callable<HiveBindConfig>> hiveTables, String timestamp) {

        Objects.nonNull(fileSystem);
        Objects.nonNull(timestamp);
        try {
            // Dump 任务结束,开始绑定hive partition
            HiveTableBuilder hiveTableBuilder = new HiveTableBuilder(timestamp);
            hiveTableBuilder.setFileSystem(fileSystem);
            hiveTableBuilder.bindHiveTables(engine, hiveTables, hiveConn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void bindHiveTables(DataSourceMeta engine, DataSourceMeta.JDBCConnection hiveConn, ITISFileSystem fileSystem
            , Map<EntityName, Callable<HiveBindConfig>> hiveTables
            , String timestamp, HiveTableBuilder.IsTableSchemaSame isTableSchemaSame
            , HiveTableBuilder.CreateHiveTableAndBindPartition createHiveTableAndBindPartition) {
        Objects.nonNull(fileSystem);
        Objects.nonNull(timestamp);
        try {
            // Dump 任务结束,开始绑定hive partition
            HiveTableBuilder hiveTableBuilder = new HiveTableBuilder(timestamp);
            hiveTableBuilder.setFileSystem(fileSystem);
            hiveTableBuilder.bindHiveTables(engine, hiveTables, hiveConn, isTableSchemaSame, createHiveTableAndBindPartition);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("all")
    public static List<HiveColumn> getColumns(ITISFileSystem fs, EntityName hiveTable, String timestamp) throws IOException {
        String hivePath = hiveTable.getNameWithPath();
        InputStream input = null;
        List<HiveColumn> cols = new ArrayList<>();
        try {
            input = fs.open(fs.getPath(fs.getRootDir() + "/" + hivePath + "/all/" + timestamp + "/" + ColumnMetaData.KEY_COLS_METADATA));
            // input = fileSystem.open(path);
            String content = IOUtils.toString(input, TisUTF8.getName());
            JSONArray array = (JSONArray) JSON.parse(content);
            for (Object anArray : array) {
                JSONObject o = (JSONObject) anArray;
                HiveColumn col = new HiveColumn();
                col.setName(o.getString("key"));
                col.setIndex(o.getIntValue("index"));
                // col.setType(getHiveType(o.getIntValue("type")).name());
                col.setDataType(new DataType(o.getIntValue("type")));
                cols.add(col);
            }
        } finally {
            IOUtils.closeQuietly(input);
        }
        return cols;
    }

//    private static SupportHiveDataType getHiveType(int type) {
//        // java.sql.Types
//        // https://cwiki.apache.org/confluence/display/Hive/LanguageManual+DDL#LanguageManualDDL-CreateTableCreate/Drop/TruncateTable
//        switch (type) {
//            case BIT:
//            case TINYINT:
//            case SMALLINT:
//            case INTEGER:
//                return SupportHiveDataType.INT;
//            case BIGINT:
//                return SupportHiveDataType.BIGINT;
//            case FLOAT:
//            case REAL:
//            case DOUBLE:
//            case NUMERIC:
//            case DECIMAL:
//                return SupportHiveDataType.DOUBLE;
//            case TIMESTAMP:
//                return SupportHiveDataType.TIMESTAMP;
//            case DATE:
//                return SupportHiveDataType.DATE;
//            default:
//                return SupportHiveDataType.STRING; //HiveColumn.HIVE_TYPE_STRING;
//        }
//    }

    /*
     * 在hive中生成新表，和在新表上创建创建Partition
     *
     * @author 百岁（baisui@qlangtech.com）
     * @date 2015年10月31日 下午3:43:14
     */
    public static class HiveTableBuilder {

        private final String timestamp;

        protected ITISFileSystem fileSystem;
        private final HdfsFormat fsFormat;


        public HiveTableBuilder(String timestamp) {
            this(timestamp, HdfsFormat.DEFAULT_FORMAT);
        }

        public HiveTableBuilder(String timestamp, HdfsFormat fsFileFormat) {
            super();
            if (StringUtils.isEmpty(timestamp)) {
                throw new IllegalArgumentException("param timestamp can not be null");
            }
            this.timestamp = timestamp;
            this.fsFormat = fsFileFormat;
        }

//        /**
//         * @param
//         * @return
//         * @throws Exception
//         */
//        public static List<String> getExistTables(Connection conn) throws Exception {
//            final List<String> tables = new ArrayList<>();
//            HiveDBUtils.query(conn, "show tables", new HiveDBUtils.ResultProcess() {
//                @Override
//                public boolean callback(ResultSet result) throws Exception {
//                    tables.add(result.getString(1));
//                    return true;
//                }
//            });
//            return tables;
//        }

        /**
         * @param
         * @return
         * @throws Exception
         */
        public static List<String> getExistTables(DataSourceMeta.JDBCConnection conn, String dbName) throws Exception {
            final List<String> tables = new ArrayList<>();
            if (!isDBExists(conn, dbName)) {
                // DB都不存在，table肯定就不存在啦
                return tables;
            }
            HiveDBUtils.query(conn, "show tables from " + dbName, result -> tables.add(result.getString(2)));
            return tables;
        }

        /**
         * Reference:https://cwiki.apache.org/confluence/display/Hive/LanguageManual+DDL#LanguageManualDDL-CreateTableCreate/Drop/TruncateTable
         *
         * @param conn
         * @param table
         * @param cols
         * @param sqlCommandTailAppend
         * @throws Exception
         */
        public void createHiveTableAndBindPartition(
                DataSourceMeta sourceMeta, DataSourceMeta.JDBCConnection conn, EntityName table, List<HiveColumn> cols, SQLCommandTailAppend sqlCommandTailAppend) throws Exception {
            if (StringUtils.isEmpty(table.getDbName())) {
                throw new IllegalArgumentException("table.getDbName can not be empty");
            }

            int maxColLength = 0;
            int tmpLength = 0;
            for (HiveColumn c : cols) {
                tmpLength = StringUtils.length(c.getName());
                if (tmpLength < 1) {
                    throw new IllegalStateException("col name length can not small than 1,cols size:" + cols.size());
                }
                if (tmpLength > maxColLength) {
                    maxColLength = tmpLength;
                }
            }
            HiveColumn o = null;
            String colformat = "%-" + (++maxColLength) + "s";
            StringBuffer hiveSQl = new StringBuffer();
            HiveDBUtils.executeNoLog(conn, "CREATE DATABASE IF NOT EXISTS " + table.getDbName() + "");
            hiveSQl.append("CREATE EXTERNAL TABLE IF NOT EXISTS " + table.getFullName(Optional.of(sourceMeta.getEscapeChar())) + " (\n");
            final int colsSize = cols.size();
            for (int i = 0; i < colsSize; i++) {
                o = cols.get(i);
                if (i != o.getIndex()) {
                    throw new IllegalStateException("i:" + i + " shall equal with index:" + o.getIndex());
                }
                hiveSQl.append("  ").append(sourceMeta.getEscapeChar())
                        .append(String.format(colformat, StringUtils.remove(o.getName(), sourceMeta.getEscapeChar()) + sourceMeta.getEscapeChar()))
                        .append(" ").append(o.getDataType());
                if ((i + 1) < colsSize) {
                    hiveSQl.append(",");
                }
                hiveSQl.append("\n");
            }
            hiveSQl.append(") COMMENT 'tis_tmp_" + table + "' PARTITIONED BY(" + IDumpTable.PARTITION_PT + " string," + IDumpTable.PARTITION_PMOD + " string)   ");
            hiveSQl.append(this.fsFormat.getRowFormat());
            hiveSQl.append("STORED AS " + this.fsFormat.getFileType().getType());
            sqlCommandTailAppend.append(hiveSQl);
            logger.info(hiveSQl.toString());
            HiveDBUtils.executeNoLog(conn, hiveSQl.toString());
        }

        /**
         * 和hdfs上已经导入的数据进行绑定
         *
         * @param hiveTables
         * @throws Exception
         */
        public void bindHiveTables(DataSourceMeta engine, Map<EntityName, Callable<HiveBindConfig>> hiveTables, DataSourceMeta.JDBCConnection conn) throws Exception {
            bindHiveTables(engine, hiveTables, conn,
                    (columns, hiveTable) -> {
                        return isTableSame(engine, conn, columns.colsMeta, hiveTable);
                    },
                    (columns, hiveTable) -> {
                        this.createHiveTableAndBindPartition(engine, conn, columns, hiveTable);
                    });
        }


        /**
         * 和hdfs上已经导入的数据进行绑定
         *
         * @param hiveTables
         * @throws Exception
         */
        public void bindHiveTables(DataSourceMeta engine, Map<EntityName, Callable<HiveBindConfig>> hiveTables, DataSourceMeta.JDBCConnection conn
                , IsTableSchemaSame isTableSchemaSame, CreateHiveTableAndBindPartition createHiveTableAndBindPartition) throws Exception {

            try {
                EntityName hiveTable = null;

                for (Map.Entry<EntityName, Callable<HiveBindConfig>> entry : hiveTables.entrySet()) {
                    // String hivePath = hiveTable.getNameWithPath();
                    hiveTable = entry.getKey();

                    final List<String> tables = engine.getTablesInDB().getTabs();// engine.getTabs(conn, hiveTable);// getExistTables(conn, hiveTable.getDbName());
                    HiveBindConfig columns = entry.getValue().call();// getColumns(hiveTable, timestamp);
                    if (tables.contains(hiveTable.getTableName())) {
                        //isTableSame(conn, columns.colsMeta, hiveTable)
                        if (!isTableSchemaSame.isSame(columns, hiveTable)) {
                            // 原表有改动，需要把表drop掉
                            logger.info("table:" + hiveTable.getTableName() + " exist but table col metadta has been changed");
                            HiveDBUtils.execute(conn, "DROP TABLE " + hiveTable);
                            createHiveTableAndBindPartition.run(columns, hiveTable);
                            // this.createHiveTableAndBindPartition(conn, columns, hiveTable);
                            //return;
                        } else {
                            logger.info("table:" + hiveTable.getTableName() + " exist will bind new partition");
                        }
                    } else {
                        logger.info("table not exist,will create now:" + hiveTable.getTableName() + ",exist table:["
                                + tables.stream().collect(Collectors.joining(",")) + "]");
                        //  this.createHiveTableAndBindPartition(conn, columns, hiveTable);
                        createHiveTableAndBindPartition.run(columns, hiveTable);
                        //return;
                    }

                    // 生成 hive partitiion
                    this.bindPartition(conn, columns, hiveTable, 0);
                }
            } finally {
//                try {
//                    conn.close();
//                } catch (Throwable e) {
//                }
            }
        }

        public interface CreateHiveTableAndBindPartition {
            void run(HiveBindConfig columns, EntityName hiveTable) throws Exception;
        }

        public interface IsTableSchemaSame {
            boolean isSame(HiveBindConfig columns, EntityName hiveTable) throws Exception;
        }


        public static boolean isTableSame(DataSourceMeta mrEngine, DataSourceMeta.JDBCConnection conn, List<HiveColumn> columns, EntityName tableName) throws Exception {
            boolean isTableSame;
            final StringBuffer errMsg = new StringBuffer();
            final StringBuffer equalsCols = new StringBuffer("compar equals:");
            final AtomicBoolean compareOver = new AtomicBoolean(false);
            HiveDBUtils.query(conn, "desc " + tableName.getFullName(Optional.of(mrEngine.getEscapeChar())), new HiveDBUtils.ResultProcess() {

                int index = 0;

                @Override
                public boolean callback(ResultSet result) throws Exception {
                    if (errMsg.length() > 0) {
                        return false;
                    }
                    final String keyName = result.getString(1);
                    if (compareOver.get() || (StringUtils.isBlank(keyName) && compareOver.compareAndSet(false, true))) {
                        // 所有列都解析完成
                        return false;
                    }
                    if (IDumpTable.preservedPsCols.contains(keyName)) {
                        // 忽略pt和pmod
                        return true;
                    }
                    if (StringUtils.startsWith(keyName, "#")) {
                        return false;
                    }
                    if (index > (columns.size() - 1)) {
                        errMsg.append("create table " + tableName + " col:[" + keyName + "] is not exist");
                        return false;
                    }
                    HiveColumn column = columns.get(index++);
                    if (column.getIndex() != (index - 1)) {
                        throw new IllegalStateException("col:" + column.getName() + " index shall be " + (index - 1) + " but is " + column.getIndex());
                    }
                    if (!StringUtils.equals(keyName, column.getName())) {
                        errMsg.append("target table keyName[" + index + "]:" + keyName + " is not equal with source table col:" + column.getName());
                        return false;
                    } else {
                        equalsCols.append(keyName).append(",");
                    }
                    return true;
                }
            });
            if (errMsg.length() > 0) {
                isTableSame = false;
                logger.warn("create table has been modify,error:" + errMsg);
                logger.warn(equalsCols.toString());
            } else {
                // 没有改动，不过需要把元表清空
                isTableSame = true;
            }
            return isTableSame;
        }

        /**
         * 判断表是否存在
         *
         * @param connection
         * @param dumpTable
         * @return
         * @throws Exception
         */
        public static boolean isTableExists(DataSourceMeta mrEngine, DataSourceMeta.JDBCConnection connection, EntityName dumpTable) throws Exception {
            // 判断表是否存在
            if (!isDBExists(connection, dumpTable.getDbName())) {
                // DB都不存在，table肯定就不存在啦
                logger.debug("dumpTable'DB is not exist:{}", dumpTable);
                return false;
            }

            try {
                mrEngine.getTableMetadata(connection, dumpTable);
                return true;
            } catch (TableNotFoundException e) {
                return false;
            }

          //  final List<String> tables = mrEngine.getTablesInDB().getTabs();// mrEngine.getTabs(connection, dumpTable);// new ArrayList<>();
            // SPARK:
            //        +-----------+---------------+--------------+--+
            //        | database  |   tableName   | isTemporary  |
            //        +-----------+---------------+--------------+--+
            //        | order     | totalpayinfo  | false        |
            //        +-----------+---------------+--------------+--+
            // Hive
//            HiveDBUtils.query(connection, "show tables in " + dumpTable.getDbName()
//                    , result -> tables.add(result.getString(2)));
//            boolean contain = tables.contains(dumpTable.getTableName());
//            if (!contain) {
//                logger.debug("table:{} is not exist in[{}]", dumpTable.getTableName()
//                        , tables.stream().collect(Collectors.joining(",")));
//            }
//            return contain;
        }

        private static boolean isDBExists(DataSourceMeta.JDBCConnection connection, String dbName) throws Exception {
            AtomicBoolean dbExist = new AtomicBoolean(false);
            HiveDBUtils.query(connection, "show databases", result -> {
                if (StringUtils.equals(result.getString(1), dbName)) {
                    dbExist.set(true);
                }
                return true;
            });
            return dbExist.get();
        }

        /**
         * 创建表分区
         *
         * @param
         * @throws Exception
         */
        void bindPartition(DataSourceMeta.JDBCConnection conn, HiveBindConfig columns, EntityName table, int startIndex) throws Exception {

            visitSubPmodPath(table, columns, startIndex, (pmod, path) -> {
                String sql = "alter table " + table + " add if not exists partition(" + IDumpTable.PARTITION_PT + "='"
                        + timestamp + "'," + IDumpTable.PARTITION_PMOD + "='" + pmod + "') location '" + path.toString() + "'";
                logger.info(sql);
                HiveDBUtils.executeNoLog(conn, sql);
                return true;
            });
        }

        private IPath visitSubPmodPath(EntityName table, HiveBindConfig colsMeta, int startIndex, FSPathVisitor pathVisitor) throws Exception {
            final String hivePath = table.getNameWithPath();
            ITISFileSystem fs = this.fileSystem;
            IPath path = null;

            while (true) {
                //path = fs.getPath(this.fileSystem.getRootDir() + "/" + hivePath + "/all/" + timestamp + "/" + (startIndex));
                path = fs.getPath(new HdfsPath(colsMeta.tabDumpParentPath), String.valueOf(startIndex));
                if (!fs.exists(path)) {
                    return path;
                }
                if (!pathVisitor.process(startIndex, path)) { return path;}
                startIndex++;
            }
        }

        private interface FSPathVisitor {
            boolean process(int pmod, IPath path) throws Exception;
        }


        private void createHiveTableAndBindPartition(DataSourceMeta sourceMeta, DataSourceMeta.JDBCConnection conn, HiveBindConfig columns, EntityName tableName) throws Exception {
            createHiveTableAndBindPartition(sourceMeta, conn, tableName, columns.colsMeta, (hiveSQl) -> {
                        //final String hivePath = tableName.getNameWithPath();
                        int startIndex = 0;
                        //final StringBuffer tableLocation = new StringBuffer();
                        AtomicBoolean hasSubPmod = new AtomicBoolean(false);
                        IPath p = this.visitSubPmodPath(tableName, columns, startIndex, (pmod, path) -> {
                            // hiveSQl.append(" partition(pt='" + timestamp + "',pmod='" + pmod + "') location '" + path.toString() + "'");

                            hiveSQl.append(" location '" + path.toString() + "'");

                            hasSubPmod.set(true);
                            return false;
                        });

                        if (!hasSubPmod.get()) {
                            throw new IllegalStateException("dump table:" + tableName.getFullName() + " has any valid of relevant fs in timestamp path:" + p);
                        }
                    }
            );
        }

        public void setFileSystem(ITISFileSystem fileSystem) {
            this.fileSystem = fileSystem;
        }

        public interface SQLCommandTailAppend {
            public void append(StringBuffer hiveSQl) throws Exception;
        }
    }

    public static class HiveBindConfig {
        public final List<HiveColumn> colsMeta;
        public final Path tabDumpParentPath;

        public HiveBindConfig(List<HiveColumn> colsMeta, Path tabDumpParentPath) {
            this.colsMeta = colsMeta;
            this.tabDumpParentPath = tabDumpParentPath;
        }
    }
}

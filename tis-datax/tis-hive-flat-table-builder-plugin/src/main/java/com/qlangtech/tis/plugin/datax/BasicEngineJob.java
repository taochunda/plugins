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

package com.qlangtech.tis.plugin.datax;

import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.hdfswriter.HdfsWriterErrorCode;
import com.alibaba.datax.plugin.writer.hdfswriter.Key;
import com.alibaba.datax.plugin.writer.hdfswriter.SupportHiveDataType;
import com.google.common.collect.Lists;
import com.qlangtech.tis.dump.hive.BindHiveTableTool;
import com.qlangtech.tis.fs.ITaskContext;
import com.qlangtech.tis.fullbuild.indexbuild.IDumpTable;
import com.qlangtech.tis.fullbuild.taskflow.hive.JoinHiveTask;
import com.qlangtech.tis.hdfs.impl.HdfsFileSystemFactory;
import com.qlangtech.tis.hdfs.impl.HdfsPath;
import com.qlangtech.tis.hive.HdfsFileType;
import com.qlangtech.tis.hive.HdfsFormat;
import com.qlangtech.tis.hive.HiveColumn;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-07-08 16:34
 **/
public abstract class BasicEngineJob<TT extends DataXHiveWriter> extends BasicHdfsWriterJob<TT> {
    private static final Logger logger = LoggerFactory.getLogger(BasicEngineJob.class);
    private EntityName dumpTable = null;
    private List<HiveColumn> colsExcludePartitionCols = null;
    private String dumpTimeStamp;
    private Integer ptRetainNum;


    @Override
    public void init() {
        try {
            super.init();
        } catch (Throwable e) {
            if (ExceptionUtils.indexOfType(e, JobPropInitializeException.class) > -1) {
                throw new RuntimeException(e);
            } else {
                TisDataXHiveWriter.logger.warn("init alibaba hdfs writer Job faild,errmsg:" + StringUtils.substringBefore(e.getMessage(), "\n"));
            }
        }

        this.ptRetainNum = getPtRetainNum();
        TT writerPlugin = this.getWriterPlugin();
        Objects.requireNonNull(writerPlugin, "writerPlugin can not be null");
        //this.getDumpTable();

        Objects.requireNonNull(this.dumpTimeStamp, "dumpTimeStamp can not be null");

        // try {
//                this.tabDumpParentPath = new Path(this.writerPlugin.getFs().getFileSystem().getRootDir(), getHdfsSubPath());
//                Path pmodPath = getPmodPath();
//                // 将path创建
//                this.writerPlugin.getFs().getFileSystem().mkdirs(new HdfsPath(pmodPath));
//                jobPath.set(this, pmodPath.toString());
//                logger.info("hive writer hdfs path:{}", pmodPath);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }


    }

    protected int getPtRetainNum() {
        return Integer.parseInt(this.cfg.getNecessaryValue("ptRetainNum", HdfsWriterErrorCode.REQUIRED_VALUE));
    }

    public void prepare() {
        super.prepare();

        this.colsExcludePartitionCols = getCols();
        int[] appendStartIndex = new int[]{colsExcludePartitionCols.size()};
        List<HiveColumn> cols = Lists.newArrayList(colsExcludePartitionCols);

        IDumpTable.preservedPsCols.forEach((c) -> {
            HiveColumn hiveCol = new HiveColumn();
            hiveCol.setName(c);
            hiveCol.setType(SupportHiveDataType.STRING.name());
            hiveCol.setIndex(appendStartIndex[0]++);
            cols.add(hiveCol);
        });
        initializeHiveTable(cols);
    }

    @Override
    public Configuration getPluginJobConf() {
        Configuration cfg = super.getPluginJobConf();
        // this.writerPlugin = TisDataXHiveWriter.getHdfsWriterPlugin(cfg);


        // 写了一个默认的可以骗过父类校验
        return cfg;
    }

    protected Path createPath() throws IOException {
        SimpleDateFormat timeFormat = new SimpleDateFormat(this.cfg.getNecessaryValue("ptFormat", HdfsWriterErrorCode.REQUIRED_VALUE));
        this.dumpTimeStamp = timeFormat.format(new Date());
        this.dumpTable = this.createDumpTable();
        TT writerPlugin = this.getWriterPlugin();
        this.tabDumpParentPath = new Path(writerPlugin.getFs().getFileSystem().getRootDir(), getHdfsSubPath());
        Path pmodPath = getPmodPath();
        // 将path创建
        HdfsFileSystemFactory hdfsFactory = (HdfsFileSystemFactory) writerPlugin.getFs();
        hdfsFactory.getFileSystem().mkdirs(new HdfsPath(pmodPath));
        return pmodPath;
    }

    static class JobPropInitializeException extends RuntimeException {
        public JobPropInitializeException(String message, Throwable cause) {
            super(message, cause);
        }

        public JobPropInitializeException(String message) {
            super(message);
        }
    }


    protected Path getPmodPath() {
        return new Path(tabDumpParentPath, "0");
    }

    protected String getHdfsSubPath() {
        Objects.requireNonNull(dumpTable, "dumpTable can not be null");
        return this.dumpTable.getNameWithPath() + "/" + this.dumpTimeStamp;
    }

    protected EntityName createDumpTable() {
        String hiveTableName = cfg.getString(TisDataXHiveWriter.KEY_HIVE_TAB_NAME);
        if (StringUtils.isBlank(hiveTableName)) {
            throw new IllegalStateException("config key " + TisDataXHiveWriter.KEY_HIVE_TAB_NAME + " can not be null");
        }
//        if (!(writerPlugin instanceof DataXHiveWriter)) {
//            throw new IllegalStateException("hiveWriterPlugin must be type of DataXHiveWriter");
//        }
        return EntityName.create(getWriterPlugin().getHiveConnGetter().getDbName(), hiveTableName);
    }


    protected void initializeHiveTable(List<HiveColumn> cols) {
        try {
            TT writerPlugin = getWriterPlugin();
            try (Connection conn = writerPlugin.getConnection()) {
                Objects.requireNonNull(this.tabDumpParentPath, "tabDumpParentPath can not be null");
                JoinHiveTask.initializeHiveTable(fileSystem, fileSystem.getPath(new HdfsPath(this.tabDumpParentPath), ".."), writerPlugin.getEngineType(), parseFSFormat()
                        , cols, colsExcludePartitionCols, conn, dumpTable, this.ptRetainNum);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HdfsFormat parseFSFormat() {
        try {
            HdfsFormat fsFormat = new HdfsFormat();
            fsFormat.setFieldDelimiter((String) TisDataXHiveWriter.jobFieldDelimiter.get(this));
            fsFormat.setFileType(HdfsFileType.parse((String) TisDataXHiveWriter.jobFileType.get(this)));
            return fsFormat;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private List<HiveColumn> getCols() {
        try {
            List<Configuration> cols = (List<Configuration>) TisDataXHiveWriter.jobColumnsField.get(this);
            AtomicInteger index = new AtomicInteger();
            return cols.stream().map((c) -> {
                HiveColumn hivCol = new HiveColumn();
                SupportHiveDataType columnType = SupportHiveDataType.valueOf(
                        StringUtils.upperCase(c.getString(Key.TYPE)));
                String name = StringUtils.remove(c.getString(Key.NAME), "`");
                if (StringUtils.isBlank(name)) {
                    throw new IllegalStateException("col name can not be blank");
                }
                hivCol.setName(name);
                hivCol.setType(columnType.name());
                hivCol.setIndex(index.getAndIncrement());
                return hivCol;
            }).collect(Collectors.toList());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void post() {
        super.post();
        // 需要将刚导入的hdfs和hive的parition绑定
        // Set<EntityName> hiveTables = Collections.singleton(this.dumpTable);

        if (CollectionUtils.isEmpty(colsExcludePartitionCols)) {
            throw new IllegalStateException("table:" + this.dumpTable + " relevant colsExcludePartitionCols can not be empty");
        }
        Objects.requireNonNull(tabDumpParentPath, "tabDumpParentPath can not be null");


        this.bindHiveTables();
    }

    protected void bindHiveTables() {
        try {
            try (Connection hiveConn = this.getWriterPlugin().getConnection()) {
                BindHiveTableTool.bindHiveTables(this.getWriterPlugin().getEngineType(), fileSystem
                        , Collections.singletonMap(this.dumpTable, new Callable<BindHiveTableTool.HiveBindConfig>() {
                            @Override
                            public BindHiveTableTool.HiveBindConfig call() throws Exception {
                                return new BindHiveTableTool.HiveBindConfig(colsExcludePartitionCols, tabDumpParentPath);
                            }
                        })
                        , this.dumpTimeStamp //
                        , new ITaskContext() {
                            @Override
                            public Connection getObj() {
                                return hiveConn;
                            }
                        });
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
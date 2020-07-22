/**
 * Copyright 2020 QingLang, Inc.
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qlangtech.tis.fullbuild.indexbuild.impl;

import com.alibaba.citrus.turbine.Context;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.build.task.TaskMapper;
import com.qlangtech.tis.config.ParamsConfig;
import com.qlangtech.tis.config.yarn.IYarnConfig;
import com.qlangtech.tis.dump.INameWithPathGetter;
import com.qlangtech.tis.dump.hive.BindHiveTableTool;
import com.qlangtech.tis.dump.hive.HiveRemoveHistoryDataTask;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.fs.ITableBuildTask;
import com.qlangtech.tis.fs.ITaskContext;
import com.qlangtech.tis.fullbuild.indexbuild.IDumpTable;
import com.qlangtech.tis.fullbuild.indexbuild.IRemoteJobTrigger;
import com.qlangtech.tis.fullbuild.indexbuild.TaskContext;
import com.qlangtech.tis.offline.FileSystemFactory;
import com.qlangtech.tis.offline.FlatTableBuilder;
import com.qlangtech.tis.offline.TableDumpFactory;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 基于YARN容器的表Dump实现
 *
 * @author 百岁（baisui@qlangtech.com）
 * @create: 2020-03-31 15:39
 * @date 2020/04/13
 */
public class YarnTableDumpFactory extends TableDumpFactory implements IContainerPodSpec {

    private static final Logger logger = LoggerFactory.getLogger(YarnTableDumpFactory.class);

    private static final String KEY_FIELD_YARN_CONTAINER = "yarnCfg";

    private static final String KEY_FIELD_FS_NAME = "fsName";

    private static final String KEY_FIELD_FLAT_TABLE_BUILDER_NAME = "destination";

    @FormField(ordinal = 0, validate = {Validator.require, Validator.identity})
    public String name;

    @FormField(ordinal = 1, validate = {Validator.require, Validator.identity}, type = FormFieldType.SELECTABLE)
    public String yarnCfg;

    @FormField(ordinal = 3, validate = {Validator.require, Validator.identity}, type = FormFieldType.SELECTABLE)
    public String fsName;

    @FormField(ordinal = 4, validate = {Validator.require, Validator.identity}, type = FormFieldType.SELECTABLE)
    public String destination;

    @FormField(ordinal = 5, validate = {Validator.require}, type = FormFieldType.INT_NUMBER, dftVal = "1024")
    public int maxHeapMemory;

    @FormField(ordinal = 6, validate = {Validator.require}, type = FormFieldType.INT_NUMBER, dftVal = "1")
    public int maxCPUCores;

    @FormField(ordinal = 7, type = FormFieldType.INT_NUMBER)
    public int runjdwpPort;


    @Override
    public int getMaxHeapMemory() {
        return maxHeapMemory;
    }

    @Override
    public int getMaxCPUCores() {
        return maxCPUCores;
    }

    @Override
    public int getRunjdwpPort() {
        return runjdwpPort;
    }

    private transient FileSystemFactory fileSystem;

    private transient HiveRemoveHistoryDataTask removeHistoryDataTask;

    @Override
    public FileSystemFactory getFileSystem() {
        return this.getFs();
    }

    /**
     * 构建宽表
     *
     * @param task
     */
    @Override
    public void startTask(ITableBuildTask task) {
        FlatTableBuilder flatTableBuilder = TIS.getPluginStore(FlatTableBuilder.class).find(this.destination);
        flatTableBuilder.startTask(task);
    }


    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getJoinTableStorePath(INameWithPathGetter pathGetter) {
        return HiveRemoveHistoryDataTask.getJoinTableStorePath(this.getFs().getRootDir(), pathGetter);
    }

    private HiveRemoveHistoryDataTask getHiveRemoveHistoryDataTask() {
        if (removeHistoryDataTask == null) {
            this.removeHistoryDataTask = new HiveRemoveHistoryDataTask(getFs());
        }
        return removeHistoryDataTask;
    }

    private FileSystemFactory getFs() {
        if (fileSystem == null) {
            fileSystem = FileSystemFactory.getFsFactory(this.fsName);
        }
        return fileSystem;
    }

    @Override
    public void dropHistoryTable(EntityName dumpTable, ITaskContext context) {
        Connection conn = context.getObj();
        this.getHiveRemoveHistoryDataTask().dropHistoryHiveTable(dumpTable, conn);
    }

    @Override
    public void deleteHistoryFile(EntityName dumpTable, ITaskContext context) {
        Connection hiveConnection = context.getObj();
        getHiveRemoveHistoryDataTask().deleteHdfsHistoryFile(dumpTable, hiveConnection);
    }

    @Override
    public void deleteHistoryFile(EntityName dumpTable, ITaskContext context, String timestamp) {
        Connection hiveConnection = context.getObj();
        getHiveRemoveHistoryDataTask().deleteHdfsHistoryFile(dumpTable, hiveConnection, timestamp);
    }

    @Override
    public void bindTables(Set<EntityName> hiveTables, String timestamp, ITaskContext context) {
        BindHiveTableTool.bindHiveTables(this.getFs(), hiveTables, timestamp, context);
    }

    @Override
    public IRemoteJobTrigger createSingleTableDumpJob(IDumpTable table, String startTime, TaskContext context) {
        //  org.apache.hadoop.security.JniBasedUnixGroupsMappingWithFallback not org.apache.hadoop.security.GroupMappingServiceProvider

//        try {
//            // 因为TIS工程中有使用hadoop-rpc，加载Configuration会报
//            // err:java.lang.RuntimeException: class org.apache.hadoop.security.JniBasedUnixGroupsMappingWithFallback not org.apache.hadoop.security.GroupMappingServiceProvider
//            // 所以要在插件中保证
//            ClassLoader cl = Hadoop020RemoteJobTriggerFactory.class.getClassLoader();
//            logger.info("show classloader==============================");
//            logger.info(cl.loadClass("org.apache.hadoop.security.JniBasedUnixGroupsMappingWithFallback").getClassLoader().toString());
//            logger.info(cl.loadClass("org.apache.hadoop.security.GroupMappingServiceProvider").getClassLoader().toString());
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }

        Hadoop020RemoteJobTriggerFactory dumpTriggerFactory
                = new Hadoop020RemoteJobTriggerFactory(getYarnConfig(), getFs(), this);
        return dumpTriggerFactory.createSingleTableDumpJob(table, startTime, context);
    }

    /**
     * 执行服务端表dump任务
     *
     * @param taskMapper
     * @param taskContext
     */
    @Override
    public void startTask(TaskMapper taskMapper, TaskContext taskContext) throws Exception {
        ServerTaskExecutor taskExecutor = new ServerTaskExecutor(this.getYarnConfig());
        DefaultCallbackHandler callbackHandler = new DefaultCallbackHandler();
        taskExecutor.startTask(taskMapper, taskContext, callbackHandler);
    }

    private IYarnConfig getYarnConfig() {
        return ParamsConfig.getItem(this.yarnCfg, IYarnConfig.class);
    }


    @TISExtension()
    public static class DefaultDescriptor extends Descriptor<TableDumpFactory> {

        public DefaultDescriptor() {
            super();
            this.registerSelectOptions(KEY_FIELD_YARN_CONTAINER, () -> ParamsConfig.getItems(IYarnConfig.class));
            this.registerSelectOptions(KEY_FIELD_FS_NAME, () -> TIS.getPluginStore(FileSystemFactory.class).getPlugins());
            this.registerSelectOptions(KEY_FIELD_FLAT_TABLE_BUILDER_NAME, () -> TIS.getPluginStore(FlatTableBuilder.class).getPlugins());
        }

        @Override
        public String getDisplayName() {
            return "yarn";
        }

//        public boolean validateName(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
//            Matcher matcher = pattern_identity.matcher(value);
//            if (!matcher.matches()) {
//                msgHandler.addFieldError(context, fieldName, MSG_IDENTITY_ERROR);
//                return false;
//            }
//            return true;
//        }
    }
}

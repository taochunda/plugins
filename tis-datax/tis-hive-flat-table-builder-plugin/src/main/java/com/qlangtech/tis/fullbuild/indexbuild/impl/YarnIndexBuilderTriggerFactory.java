/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 *   This program is free software: you can use, redistribute, and/or modify
 *   it under the terms of the GNU Affero General Public License, version 3
 *   or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *   FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.qlangtech.tis.fullbuild.indexbuild.impl;

import com.qlangtech.tis.TIS;
import com.qlangtech.tis.build.task.TaskMapper;
import com.qlangtech.tis.config.ParamsConfig;
import com.qlangtech.tis.config.yarn.IYarnConfig;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.fs.FSHistoryFileUtils;
import com.qlangtech.tis.fs.ITISFileSystem;
import com.qlangtech.tis.fullbuild.indexbuild.*;
import com.qlangtech.tis.offline.FileSystemFactory;
import com.qlangtech.tis.offline.IndexBuilderTriggerFactory;
import com.qlangtech.tis.order.center.IJoinTaskContext;
import com.qlangtech.tis.order.center.IParamContext;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author 百岁（baisui@qlangtech.com）
 * @create: 2020-04-08 14:11
 * @date 2020/04/13
 */
public class YarnIndexBuilderTriggerFactory extends IndexBuilderTriggerFactory implements IContainerPodSpec {

    private static final Logger logger = LoggerFactory.getLogger(YarnIndexBuilderTriggerFactory.class);

    public static final String FIELD_CONTAINER_NAME = "containerName";

    public static final String FIELD_FS_NAME = "fsName";

    @FormField(identity = true, ordinal = 0, validate = {Validator.require, Validator.identity})
    public String name;

    @FormField(ordinal = 1, validate = {Validator.require, Validator.identity}, type = FormFieldType.SELECTABLE)
    public String containerName;


    @FormField(ordinal = 2, validate = {Validator.require}, type = FormFieldType.INT_NUMBER, dftVal = "1024")
    public int maxHeapMemory;

    @FormField(ordinal = 3, validate = {Validator.require}, type = FormFieldType.INT_NUMBER, dftVal = "1")
    public int maxCPUCores;

    @FormField(ordinal = 4, type = FormFieldType.INT_NUMBER)
    public int runjdwpPort;

    @FormField(ordinal = 5, validate = {Validator.require, Validator.identity}, type = FormFieldType.SELECTABLE)
    public String fsName;

    @FormField(ordinal = 6, validate = {Validator.require}, type = FormFieldType.INT_NUMBER)
    public int maxDocMakeFaild;



    @Override
    public IndexBuildSourcePathCreator createIndexBuildSourcePathCreator(IJoinTaskContext ctx, ITabPartition ps) {
        IndexBuildSourcePathCreator pathCreator = ((group) -> {
            // 需要构建倒排索引的表名称
            EntityName targetTableName = ctx.getAttribute(IParamContext.KEY_BUILD_TARGET_TABLE_NAME);
            Objects.requireNonNull(targetTableName, "targetTableName can not be null");
            String fsPath = FSHistoryFileUtils.getJoinTableStorePath(getFileSystem().getRootDir(), targetTableName)
                    + "/" + IDumpTable.PARTITION_PT + "=%s/" + IDumpTable.PARTITION_PMOD + "=%s";
            logger.info("hdfs sourcepath:" + fsPath);
            return fsPath;
        });

        return pathCreator;
    }


    @Override
    public ITISFileSystem getFileSystem() {
        return FileSystemFactory.getFsFactory(this.fsName).getFileSystem();
    }


    private IYarnConfig getYarnConfig() {
        return ParamsConfig.getItem(this.containerName, IYarnConfig.class);
    }

    @Override
    public IRemoteJobTrigger createBuildJob(IJoinTaskContext execContext, String timePoint, String indexName
            , String groupNum, IIndexBuildParam buildParam) throws Exception {
        Hadoop020RemoteJobTriggerFactory indexBuilderTriggerFactory
                = new Hadoop020RemoteJobTriggerFactory(getYarnConfig(), getFileSystem(), this);
        return indexBuilderTriggerFactory.createBuildJob(execContext, timePoint, indexName, groupNum, buildParam);
    }

    @Override
    public int getMaxMakeFaild() {
        return this.maxDocMakeFaild;
    }

    /**
     * 服务端开始执行任务
     *
     * @param taskMapper
     */
    @Override
    public void startTask(TaskMapper taskMapper, final TaskContext taskContext) throws Exception {
        ServerTaskExecutor taskExecutor = new ServerTaskExecutor(this.getYarnConfig());

        final DefaultCallbackHandler callbackHandler = new DefaultCallbackHandler() {
            @Override
            public float getProgress() {
//                if (indexBuilder == null || taskContext == null) {
//                    return 0;
//                }
                final long allRowCount = taskContext.getAllRowCount();
                long indexMakeCounter = taskContext.getIndexMakerComplete();
                float mainProgress = (float) (((double) indexMakeCounter) / allRowCount);
                return (float) (((mainProgress > 1.0) ? 1.0 : mainProgress));
            }
        };

        taskExecutor.startTask(taskMapper, taskContext, callbackHandler);
    }


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


    @TISExtension
    public static class DefaultDescriptor extends Descriptor<IndexBuilderTriggerFactory> {

        public DefaultDescriptor() {
            super();
            registerSelectOptions(FIELD_CONTAINER_NAME, () -> ParamsConfig.getItems(IYarnConfig.class));
            registerSelectOptions(FIELD_FS_NAME, () -> TIS.getPluginStore(FileSystemFactory.class).getPlugins());
        }

        @Override
        public String getDisplayName() {
            return "yarn";
        }
    }
}
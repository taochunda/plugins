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
package com.qlangtech.tis.dump;

import com.qlangtech.tis.TIS;
import com.qlangtech.tis.TisZkClient;
import com.qlangtech.tis.build.task.TaskMapper;
import com.qlangtech.tis.fs.ITISFileSystem;
import com.qlangtech.tis.fs.ITISFileSystemFactory;
import com.qlangtech.tis.fs.ITableBuildTask;
import com.qlangtech.tis.fs.ITaskContext;
import com.qlangtech.tis.fs.local.LocalFileSystem;
import com.qlangtech.tis.fullbuild.indexbuild.IDumpTable;
import com.qlangtech.tis.fullbuild.indexbuild.IRemoteJobTrigger;
import com.qlangtech.tis.fullbuild.indexbuild.RunningStatus;
import com.qlangtech.tis.fullbuild.indexbuild.TaskContext;
import com.qlangtech.tis.offline.DbScope;
import com.qlangtech.tis.offline.TableDumpFactory;
import com.qlangtech.tis.order.dump.task.SingleTableDumpTask;
import com.qlangtech.tis.plugin.PluginStore;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.ds.DataSourceFactory;
import com.qlangtech.tis.plugin.ds.PostedDSProp;
import com.qlangtech.tis.sql.parser.tuple.creator.EntityName;
import com.tis.hadoop.rpc.RpcServiceReference;
import com.tis.hadoop.rpc.StatusRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author: baisui 百岁
 * @create: 2021-03-03 09:10
 **/
public class LocalTableDumpFactory extends TableDumpFactory implements ITISFileSystemFactory {
    private static final Logger logger = LoggerFactory.getLogger(LocalTableDumpFactory.class);

    @FormField(identity = true, ordinal = 0, validate = {Validator.require, Validator.identity})
    public String name;

    @FormField(ordinal = 1, validate = {Validator.require})
    public String rootDir;

    private transient ITISFileSystem fileSystem;

    private transient DetailedDataSourceFactoryGetter dataSourceFactoryGetter = (tab) -> {
        PluginStore<DataSourceFactory> dbPlugin = TIS.getDataBasePluginStore(new PostedDSProp(tab.getDbName(), DbScope.DETAILED));
        return dbPlugin.getPlugin();
    };


    private DataSourceFactory getDataSourceFactory(IDumpTable table) {
        return dataSourceFactoryGetter.get(table);
    }

    interface DetailedDataSourceFactoryGetter {
        DataSourceFactory get(IDumpTable table);
    }

    public void setDataSourceFactoryGetter(DetailedDataSourceFactoryGetter dataSourceFactoryGetter) {
        this.dataSourceFactoryGetter = dataSourceFactoryGetter;
    }

    @Override
    public ITISFileSystem getFileSystem() {
        if (fileSystem == null) {
            fileSystem = new LocalFileSystem(this.rootDir);
        }
        return fileSystem;
    }


    @Override
    public IRemoteJobTrigger createSingleTableDumpJob(final IDumpTable table, TaskContext context) {

        TisZkClient zk = context.getCoordinator().unwrap();
        Objects.requireNonNull(zk, "zk(TisZkClient) can not be null");

        AtomicReference<Throwable> errRef = new AtomicReference<>();
        CountDownLatch countDown = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor((r) -> {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler((thread, e) -> {
                errRef.set(e);
                logger.error("execute local table:" + table + " dump faild", e);
                countDown.countDown();
            });
            return t;
        });

        return new IRemoteJobTrigger() {
            @Override
            public void submitJob() {

                executor.execute(() -> {
                    RpcServiceReference statusRpc = null;
                    try {
                        statusRpc = StatusRpcClient.getService(zk);
                        SingleTableDumpTask tableDumpTask = new SingleTableDumpTask(LocalTableDumpFactory.this, getDataSourceFactory(table), zk, statusRpc) {
                            protected void registerZKDumpNodeIn(TaskContext context) {
                            }
                        };
                        // 开始执行数据dump
                        tableDumpTask.map(context);
                        countDown.countDown();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            statusRpc.get().close();
                        } catch (Throwable e) {

                        }
                    }
                });
            }

            @Override
            public RunningStatus getRunningStatus() {
                RunningStatus runningStatus = null;
                // 反馈执行状态
                if (countDown.getCount() > 0) {
                    runningStatus = new RunningStatus(0, false, false);
                } else {
                    executor.shutdown();
                    runningStatus = new RunningStatus(1, true, errRef.get() == null);
                }

                return runningStatus;
            }
        };
    }


    @Override
    public void bindTables(Set<EntityName> hiveTables, String timestamp, ITaskContext context) {

    }

    @Override
    public void deleteHistoryFile(EntityName dumpTable, ITaskContext taskContext) {

    }

    @Override
    public void deleteHistoryFile(EntityName dumpTable, ITaskContext taskContext, String timestamp) {

    }

    @Override
    public void dropHistoryTable(EntityName dumpTable, ITaskContext taskContext) {

    }

    @Override
    public String getJoinTableStorePath(INameWithPathGetter pathGetter) {
        return null;
    }


    @Override
    public void startTask(TaskMapper taskMapper, TaskContext taskContext) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void startTask(ITableBuildTask dumpTask) {
        ITaskContext ctx = new ITaskContext() {
            @Override
            public Object getObj() {
                throw new UnsupportedOperationException();
            }
        };
        try {
            dumpTask.process(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

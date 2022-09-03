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

package com.qlangtech.tis.plugins.incr.flink.connector;

import com.alibaba.citrus.turbine.Context;
import com.dtstack.chunjun.conf.*;
import com.dtstack.chunjun.connector.jdbc.TableCols;
import com.dtstack.chunjun.connector.jdbc.conf.JdbcConf;
import com.dtstack.chunjun.connector.jdbc.converter.JdbcColumnConverter;
import com.dtstack.chunjun.connector.jdbc.dialect.JdbcDialect;
import com.dtstack.chunjun.connector.jdbc.sink.JdbcOutputFormat;
import com.dtstack.chunjun.connector.jdbc.sink.JdbcOutputFormatBuilder;
import com.dtstack.chunjun.connector.jdbc.sink.JdbcSinkFactory;
import com.dtstack.chunjun.constants.ConfigConstant;
import com.dtstack.chunjun.sink.DtOutputFormatSinkFunction;
import com.dtstack.chunjun.sink.SinkFactory;
import com.dtstack.chunjun.util.TableUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qlangtech.plugins.incr.flink.chunjun.sink.SinkTabPropsExtends;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.datax.IDataxProcessor;
import com.qlangtech.tis.datax.IDataxReader;
import com.qlangtech.tis.datax.IStreamTableCreator;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.datax.IncrSelectedTabExtend;
import com.qlangtech.tis.plugin.datax.SelectedTab;
import com.qlangtech.tis.plugin.datax.common.BasicDataXRdbmsWriter;
import com.qlangtech.tis.plugin.ds.*;
import com.qlangtech.tis.plugin.incr.IIncrSelectedTabExtendFactory;
import com.qlangtech.tis.realtime.BasicTISSinkFactory;
import com.qlangtech.tis.realtime.TabSinkFunc;
import com.qlangtech.tis.runtime.module.misc.IControlMsgHandler;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.flink.api.common.io.OutputFormat;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

//import com.qlangtech.tis.plugin.datax.DataxMySQLWriter;

/**
 * WRITER extends BasicDataXRdbmsWriter, DS extends BasicDataSourceFactory
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-08-10 13:45
 **/
public abstract class ChunjunSinkFactory extends BasicTISSinkFactory<RowData> implements IStreamTableCreator {
    public static final String DISPLAY_NAME_FLINK_CDC_SINK = "Chunjun-Sink-";
    public static final String KEY_FULL_COLS = "fullColumn";
    //    描述：sink 端是否支持二阶段提交
//    注意：
//    如果此参数为空，默认不开启二阶段提交，即 sink 端不支持 exactly_once 语义；
//    当前只支持 exactly-once 和 at-least-once
//    必选：否
//    参数类型：String
//    示例："semantic": "exactly-once"
    @FormField(ordinal = 1, type = FormFieldType.ENUM, validate = {Validator.require})
    public String semantic;
    //    描述：一次性批量提交的记录数大小，该值可以极大减少 ChunJun 与数据库的网络交互次数，并提升整体吞吐量。但是该值设置过大可能会造成 ChunJun 运行进程 OOM 情况
//    必选：否
//    参数类型：int
//    默认值：1
    @FormField(ordinal = 3, type = FormFieldType.INT_NUMBER, validate = {Validator.require})
    public int batchSize;

    @FormField(ordinal = 4, type = FormFieldType.INT_NUMBER, validate = {Validator.require})
    public int flushIntervalMills;

    @FormField(ordinal = 5, type = FormFieldType.INT_NUMBER, validate = {Validator.require})
    public Integer parallelism;

    private transient Map<String, SelectedTab> selTabs;


    @Override
    public Map<IDataxProcessor.TableAlias, TabSinkFunc<RowData>> createSinkFunction(IDataxProcessor dataxProcessor) {
        Map<IDataxProcessor.TableAlias, TabSinkFunc<RowData>> sinkFuncs = Maps.newHashMap();
        IDataxProcessor.TableAlias tableName = null;
        BasicDataXRdbmsWriter dataXWriter = (BasicDataXRdbmsWriter) dataxProcessor.getWriter(null);
        Map<String, IDataxProcessor.TableAlias> selectedTabs = dataxProcessor.getTabAlias();
        if (MapUtils.isEmpty(selectedTabs)) {
            throw new IllegalStateException("selectedTabs can not be empty");
        }
        IDataxReader reader = dataxProcessor.getReader(null);
        List<ISelectedTab> tabs = reader.getSelectedTabs();

        // 清空一下tabs的缓存以免有脏数据
        this.selTabs = null;
        for (Map.Entry<String, IDataxProcessor.TableAlias> tabAliasEntry : selectedTabs.entrySet()) {
            tableName = tabAliasEntry.getValue();

            Objects.requireNonNull(tableName, "tableName can not be null");
            if (StringUtils.isEmpty(tableName.getFrom())) {
                throw new IllegalStateException("tableName.getFrom() can not be empty");
            }

            AtomicReference<CreateChunjunSinkFunctionResult> sinkFuncRef = new AtomicReference<>();
            CreateChunjunSinkFunctionResult sinkFunc = null;
            final IDataxProcessor.TableAlias tabName = tableName;
            AtomicReference<Object[]> exceptionLoader = new AtomicReference<>();
            final String targetTabName = tableName.getTo();
            BasicDataSourceFactory dsFactory = (BasicDataSourceFactory) dataXWriter.getDataSourceFactory();
            if (dsFactory == null) {
                throw new IllegalStateException("dsFactory can not be null");
            }
            DBConfig dbConfig = dsFactory.getDbConfig();
            dbConfig.vistDbURL(false, (dbName, dbHost, jdbcUrl) -> {
                try {
                    Optional<ISelectedTab> selectedTab = tabs.stream()
                            .filter((tab) -> StringUtils.equals(tabName.getFrom(), tab.getName())).findFirst();
                    if (!selectedTab.isPresent()) {
                        throw new IllegalStateException("target table:" + tabName.getFrom()
                                + " can not find matched table in:["
                                + tabs.stream().map((t) -> t.getName()).collect(Collectors.joining(",")) + "]");
                    }
                    /**
                     * 需要先初始化表MySQL目标库中的表
                     */
                    dataXWriter.initWriterTable(targetTabName, Collections.singletonList(jdbcUrl));
// FIXME 这里不能用 MySQLSelectedTab
                    sinkFuncRef.set(createSinkFunction(dbName, targetTabName
                            , (SelectedTab) selectedTab.get(), jdbcUrl, dsFactory, dataXWriter));

                } catch (Throwable e) {
                    exceptionLoader.set(new Object[]{jdbcUrl, e});
                }
            });
            if (exceptionLoader.get() != null) {
                Object[] error = exceptionLoader.get();
                throw new RuntimeException((String) error[0], (Throwable) error[1]);
            }
            Objects.requireNonNull(sinkFuncRef.get(), "sinkFunc can not be null");
            sinkFunc = sinkFuncRef.get();
            if (this.parallelism == null) {
                throw new IllegalStateException("param parallelism can not be null");
            }

            sinkFuncs.put(tableName, new RowDataSinkFunc(tableName
                    , sinkFunc.getSinkFunction(), this.getColsMeta(tableName, dsFactory, sinkFunc), supportUpsetDML()
                    , this.parallelism));
        }

        if (sinkFuncs.size() < 1) {
            throw new IllegalStateException("size of sinkFuncs can not be small than 1");
        }
        return sinkFuncs;
    }

    protected List<IColMetaGetter> getColsMeta(IDataxProcessor.TableAlias tableName, BasicDataSourceFactory dsFactory
            , CreateChunjunSinkFunctionResult sinkFunc) {
        return sinkFunc.getOutputFormat().colsMeta.stream().collect(Collectors.toList());
    }

    protected abstract boolean supportUpsetDML();

    /**
     * @param dbName
     * @param targetTabName
     * @param tab
     * @param jdbcUrl
     * @param dsFactory
     * @param dataXWriter
     * @return
     * @see JdbcSinkFactory
     */
    private CreateChunjunSinkFunctionResult createSinkFunction(
            String dbName, final String targetTabName, SelectedTab tab, String jdbcUrl
            , BasicDataSourceFactory dsFactory, BasicDataXRdbmsWriter dataXWriter) {
        SyncConf syncConf = new SyncConf();

        JobConf jobConf = new JobConf();
        ContentConf content = new ContentConf();
        OperatorConf writer = new OperatorConf();
        writer.setName("mysqlwriter");
        Map<String, Object> params = Maps.newHashMap();
        params.put("username", dsFactory.getUserName());
        params.put("password", dsFactory.getPassword());

        ((SinkTabPropsExtends) tab.getIncrSinkProps()).getIncrMode().set(params);

        List<Map<String, Object>> cols = Lists.newArrayList();
        Map<String, Object> col = null;
        // com.dtstack.chunjun.conf.FieldConf.getField(List)

        for (ISelectedTab.ColMeta cm : tab.getCols()) {
            col = Maps.newHashMap();
            col.put("name", cm.getName());
            col.put("type", parseType(cm));
            cols.add(col);
        }


        params.put(ConfigConstant.KEY_COLUMN, cols);
        params.put(KEY_FULL_COLS, tab.getCols().stream().map((c) -> c.getName()).collect(Collectors.toList()));
        params.put("batchSize", this.batchSize);
        params.put("flushIntervalMills", this.flushIntervalMills);
        params.put("semantic", this.semantic);
        Map<String, Object> conn = Maps.newHashMap();
        conn.put("jdbcUrl", jdbcUrl);
        conn.put("table", Lists.newArrayList(targetTabName));
        setSchema(conn, dbName, dsFactory);
        params.put("connection", Lists.newArrayList(conn));
        setParameter(dsFactory, dataXWriter, writer, params, targetTabName);
        content.setWriter(writer);
        jobConf.setContent(Lists.newLinkedList(Collections.singleton(content)));
        syncConf.setJob(jobConf);
        CreateChunjunSinkFunctionResult sinkFunc
                = createChunjunSinkFunction(jdbcUrl, dsFactory, dataXWriter, syncConf);
        return sinkFunc;

    }

    protected void setSchema(Map<String, Object> conn, String dbName, BasicDataSourceFactory dsFactory) {
        conn.put("schema", dbName);
    }

    protected void setParameter(BasicDataSourceFactory dsFactory, BasicDataXRdbmsWriter dataXWriter
            , OperatorConf writer, Map<String, Object> params, final String targetTabName) {
        writer.setParameter(params);
    }

    private CreateChunjunSinkFunctionResult createChunjunSinkFunction(
            String jdbcUrl, BasicDataSourceFactory dsFactory, BasicDataXRdbmsWriter dataXWriter, SyncConf syncConf) {
        // AtomicReference<Triple<SinkFunction<RowData>, JdbcColumnConverter, JdbcOutputFormat>> ref = new AtomicReference<>();

        CreateChunjunSinkFunctionResult sinkFactory = createSinkFactory(jdbcUrl, dsFactory, dataXWriter, syncConf);
        sinkFactory.initialize();
        return Objects.requireNonNull(sinkFactory, "create result can not be null");
    }


    public static class CreateChunjunSinkFunctionResult {
        SinkFunction<RowData> sinkFunction;
        JdbcColumnConverter columnConverter;
        JdbcOutputFormat outputFormat;
        SinkFactory sinkFactory;

        public void initialize() {
            sinkFactory.createSink(null);
        }

        public SinkFunction<RowData> getSinkFunction() {
            return sinkFunction;
        }

        public void setSinkFunction(SinkFunction<RowData> sinkFunction) {
            this.sinkFunction = sinkFunction;
        }

//        public CreateChunjunSinkFunctionResult(SinkFactory sinkFactory, SinkFunction<RowData> sinkFunction) {
//            this.sinkFunction = sinkFunction;
//            this.sinkFactory = sinkFactory;
//        }

        public JdbcColumnConverter getColumnConverter() {
            return columnConverter;
        }

        public void setColumnConverter(JdbcColumnConverter columnConverter) {
            this.columnConverter = columnConverter;
        }

        public JdbcOutputFormat getOutputFormat() {
            return outputFormat;
        }

        public void setOutputFormat(JdbcOutputFormat outputFormat) {
            this.outputFormat = outputFormat;
        }

        public SinkFactory getSinkFactory() {
            return sinkFactory;
        }

        public void setSinkFactory(SinkFactory sinkFactory) {
            this.sinkFactory = sinkFactory;
        }
    }

    protected CreateChunjunSinkFunctionResult createSinkFactory(String jdbcUrl, BasicDataSourceFactory dsFactory
            , BasicDataXRdbmsWriter dataXWriter, SyncConf syncConf) {
        final CreateChunjunSinkFunctionResult createResult = new CreateChunjunSinkFunctionResult();

        createResult.setSinkFactory(new JdbcSinkFactory(syncConf, createJdbcDialect(syncConf)) {
            @Override
            public void initCommonConf(ChunJunCommonConf commonConf) {
                super.initCommonConf(commonConf);
                initChunjunJdbcConf(this.jdbcConf);
            }

            @Override
            protected JdbcOutputFormatBuilder getBuilder() {
                return new JdbcOutputFormatBuilder(createChunjunOutputFormat(dataXWriter.getDataSourceFactory()));
            }

            @Override
            protected DataStreamSink<RowData> createOutput(
                    DataStream<RowData> dataSet, OutputFormat<RowData> outputFormat) {
                JdbcOutputFormat routputFormat = (JdbcOutputFormat) outputFormat;

                try (Connection conn = dsFactory.getConnection(jdbcUrl)) {
                    routputFormat.dbConn = conn;
                    routputFormat.initColumnList();
                } catch (SQLException e) {
                    throw new RuntimeException("jdbcUrl:" + jdbcUrl, e);
                }
                TableCols tableCols = new TableCols(routputFormat.colsMeta);
                RowType rowType =
                        TableUtil.createRowType(
                                tableCols.filterBy(jdbcConf.getColumn()), jdbcDialect.getRawTypeConverter());
                JdbcColumnConverter rowConverter = (JdbcColumnConverter) jdbcDialect.getColumnConverter(rowType, jdbcConf);
                DtOutputFormatSinkFunction<RowData> sinkFunction =
                        new DtOutputFormatSinkFunction<>(outputFormat);

                createResult.setColumnConverter(rowConverter);
                createResult.setSinkFunction(sinkFunction);
                createResult.setOutputFormat(routputFormat);
                //   ref.set(Triple.of(sinkFunction, rowConverter, routputFormat));
                return null;
            }
        });


        return createResult;
    }


    protected abstract JdbcDialect createJdbcDialect(SyncConf syncConf);


    protected abstract JdbcOutputFormat createChunjunOutputFormat(DataSourceFactory dsFactory);


    /**
     * ==========================================================
     * impl: IStreamTableCreator
     * ===========================================================
     */
    @Override
    public IStreamTableMeta getStreamTableMeta(String tableName) {

//        if (this.selTabs == null) {
//            DataxProcessor dataXProcessor = DataxProcessor.load(null, this.dataXName);
//            IDataxReader reader = dataXProcessor.getReader(null);
//
//            List<SelectedTab> tabs = reader.getSelectedTabs();
//            this.selTabs
//                    = tabs.stream()
//                    .collect(Collectors.toMap((t) -> tableName, (t) -> t));
//        }
//
//        return new IStreamTableMeta() {
//            @Override
//            public List<HdfsColMeta> getColsMeta() {
//                SelectedTab tab = Objects.requireNonNull(selTabs.get(tableName), "tableName:" + tableName + " relevant tab can not be null");
//                return tab.getCols().stream().map((c) -> {
//                    return new HdfsColMeta(c.getName(), c.isNullable(), c.isPk(), c.getType());
//                }).collect(Collectors.toList());
//                // return tabMeta.getRight().colMetas;
//            }
//        };
        throw new UnsupportedOperationException();
    }

    @Override
    public String getFlinkStreamGenerateTemplateFileName() {
        return "flink_source_handle_rowdata_scala.vm";
    }

    @Override
    public IStreamTemplateData decorateMergeData(IStreamTemplateData mergeData) {
        return mergeData;
    }

    /**
     * ==========================================================
     * End impl: IStreamTableCreator
     * ===========================================================
     */
    protected abstract Object parseType(ISelectedTab.ColMeta cm);


    protected final <TT extends BaseSinkFunctionDescriptor> Class<TT> getExpectDescClass() {
        return (Class<TT>) BasicChunjunSinkDescriptor.class;
    }

    protected abstract void initChunjunJdbcConf(JdbcConf jdbcConf);

    protected static abstract class BasicChunjunSinkDescriptor extends BaseSinkFunctionDescriptor implements IIncrSelectedTabExtendFactory {
        @Override
        public final String getDisplayName() {
            return DISPLAY_NAME_FLINK_CDC_SINK + this.getTargetType().name();
        }

        @Override
        protected boolean validateAll(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            return super.validateAll(msgHandler, context, postFormVals);
        }

        public boolean validateFlushIntervalMills(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            // return validateFileDelimiter(msgHandler, context, fieldName, value);
            int interval = Integer.parseInt(value);
            if (interval < 1000) {
                msgHandler.addFieldError(context, fieldName, "不能小于1秒");
                return false;
            }
            return true;
        }

        public boolean validateParallelism(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            int val = Integer.parseInt(value);
            if (val < 1) {
                msgHandler.addFieldError(context, fieldName, "并发度不能小于1");
                return false;
            }
            return true;
        }

        @Override
        public Descriptor<IncrSelectedTabExtend> getSelectedTableExtendDescriptor() {
            return TIS.get().getDescriptor(SinkTabPropsExtends.class);
        }
    }
}

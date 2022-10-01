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

package com.qlangtech.tis.plugin.datax;

import com.alibaba.citrus.turbine.Context;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.datax.IDataxReaderContext;
import com.qlangtech.tis.datax.IGroupChildTaskIterator;
import com.qlangtech.tis.datax.impl.DataxReader;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.PluginFormProperties;
import com.qlangtech.tis.extension.impl.SuFormProperties;
import com.qlangtech.tis.extension.impl.XmlFile;
import com.qlangtech.tis.extension.util.PluginExtraProps;
import com.qlangtech.tis.plugin.BasicTest;
import com.qlangtech.tis.plugin.KeyedPluginStore;
import com.qlangtech.tis.plugin.common.ReaderTemplate;
import com.qlangtech.tis.plugin.datax.test.TestSelectedTabs;
import com.qlangtech.tis.plugin.ds.*;
import com.qlangtech.tis.plugin.ds.mysql.MySQLDataSourceFactory;
import com.qlangtech.tis.trigger.util.JsonUtil;
import com.qlangtech.tis.util.DescriptorsJSON;
import com.qlangtech.tis.util.IPluginContext;
import com.qlangtech.tis.util.UploadPluginMeta;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.easymock.EasyMock;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author: baisui 百岁
 * @create: 2021-04-08 11:31
 **/
public class TestDataxMySQLReader extends BasicTest {

    public static String dbName = "baisuitestdb";
    String userName = "root";
    String password = "123456";
    final String dataXName = "dataXName";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TIS.dataXReaderPluginStore.clear();
    }

    /**
     * 这个单元测试很重要
     *
     * @throws Exception
     */
    public void testUpdateDataxReader() throws Exception {
        KeyedPluginStore<DataxReader> readerStore = DataxReader.getPluginStore(null, dataXName);
        final SuFormProperties props = EasyMock.createMock("subformProp", SuFormProperties.class);
        EasyMock.expect(props.getSubFormFieldName()).andReturn("selectedTabs").anyTimes();
        EasyMock.replay(props);
        DataxReader.SubFieldFormAppKey<DataxReader> subFieldKey
                = new DataxReader.SubFieldFormAppKey<>(null, false, dataXName, props, DataxReader.class);
        KeyedPluginStore<DataxReader> subFieldStore = KeyedPluginStore.getPluginStore(subFieldKey);


        XmlFile targetFile = readerStore.getTargetFile();
        FileUtils.deleteQuietly(targetFile.getFile());
        FileUtils.deleteQuietly(subFieldStore.getTargetFile().getFile());

        DataxMySQLReader dataxReader = (DataxMySQLReader) readerStore.getPlugin();
        assertNull("targetFile:" + targetFile.getFile().getAbsolutePath(), dataxReader);

        List<Descriptor.ParseDescribable<DataxReader>> dlist = Lists.newArrayList();

        dataxReader = new DataxMySQLReader();
        dataxReader.dbName = dbName;
        dataxReader.fetchSize = 1999;
        dataxReader.template = DataxMySQLReader.getDftTemplate();
        dataxReader.dataXName = dataXName;

        dlist.add(new Descriptor.ParseDescribable<>(dataxReader));


        readerStore.setPlugins(null, Optional.empty(), dlist);

        dataxReader = (DataxMySQLReader) readerStore.getPlugin();
        assertNotNull(dataxReader);
        assertTrue("dataxReader.getSelectedTabs() must be empty"
                , CollectionUtils.isEmpty(dataxReader.getSelectedTabs()));


        dlist = Lists.newArrayList();
        DataxMySQLReader subformReader = new DataxMySQLReader();

        SelectedTab tabOrder = new SelectedTab();
        tabOrder.name = TestSelectedTabs.tabNameOrderDetail;
        tabOrder.setCols(TestSelectedTabs.tabColsMetaOrderDetail
                .stream().map((c) -> c.getName()).collect(Collectors.toList()));
        subformReader.setSelectedTabs(Collections.singletonList(tabOrder));
        dlist.add(new Descriptor.ParseDescribable(subformReader));
        subFieldStore.setPlugins(null, Optional.empty(), dlist);

        assertFalse("must not dirty", dataxReader.isDirty());
        // 这里有点像变魔术，这是通过 DataXReader中addPluginsUpdateListener添加的回调函数实现的
        assertTrue(dataxReader.getSelectedTabs().size() > 0);
        dataxReader.getSelectedTabs().forEach((t) -> {
            t.getCols().forEach((c) -> {
                assertNotNull("col:" + c.getName(), c.isPk());
                assertNotNull("col:" + c.getName(), c.getType());
            });
        });

        // 子form中再加一个tab
        SelectedTab tabTotalpayinfo = new SelectedTab();
        tabTotalpayinfo.name = TestSelectedTabs.tabNameTotalpayinfo;
        tabTotalpayinfo.setCols(TestSelectedTabs.tabColsMetaTotalpayinfo
                .stream().map((c) -> c.getName()).collect(Collectors.toList()));
        subformReader.setSelectedTabs(Lists.newArrayList(tabOrder, tabTotalpayinfo));
        dlist = Lists.newArrayList();
        dlist.add(new Descriptor.ParseDescribable(subformReader));
        subFieldStore.setPlugins(null, Optional.empty(), dlist);

        assertEquals(2, dataxReader.getSelectedTabs().size());
        dataxReader.getSelectedTabs().forEach((t) -> {
            t.getCols().forEach((c) -> {
                assertNotNull("tab:" + t.getName() + ",col:" + c.getName() + " relevant PK can not be null", c.isPk());
                assertNotNull("tab:" + t.getName() + ",col:" + c.getName() + " relevant Type can not be null", c.getType());
            });
        });

        DataxMySQLReader oldReader = dataxReader;

        dataxReader = new DataxMySQLReader();
        dataxReader.dbName = dbName;
        dataxReader.fetchSize = 2999;
        dataxReader.template = DataxMySQLReader.getDftTemplate();
        dataxReader.dataXName = dataXName;

        readerStore.setPlugins(null
                , Optional.empty(), Collections.singletonList(new Descriptor.ParseDescribable(dataxReader)));

        assertTrue("oldReader must be dirty", oldReader.isDirty());
        assertTrue(dataxReader.getSelectedTabs().size() > 0);

        // 清空测试
        dlist = Lists.newArrayList();
        subformReader = new DataxMySQLReader();
        dlist.add(new Descriptor.ParseDescribable(subformReader));
        subFieldStore.setPlugins(null, Optional.empty(), dlist);
        assertTrue("getSelectedTabs shall be empty", dataxReader.getSelectedTabs().size() < 1);

        EasyMock.verify(props);
    }

    public void testDescriptorsJSONGenerate() {
        DataxMySQLReader esWriter = new DataxMySQLReader();
        DescriptorsJSON descJson = new DescriptorsJSON(esWriter.getDescriptor());
        //System.out.println(descJson.getDescriptorsJSON().toJSONString());

        JsonUtil.assertJSONEqual(DataxMySQLReader.class, "mysql-datax-reader-descriptor.json"
                , descJson.getDescriptorsJSON(), (m, e, a) -> {
                    assertEquals(m, e, a);
                });


        UploadPluginMeta pluginMeta
                = UploadPluginMeta.parse("dataxReader:require,targetDescriptorName_MySQL,subFormFieldName_selectedTabs,dataxName_baisuitestTestcase");

        JSONObject subFormDescriptorsJSON = descJson.getDescriptorsJSON(pluginMeta.getSubFormFilter());

        JsonUtil.assertJSONEqual(DataxMySQLReader.class, "mysql-datax-reader-selected-tabs-subform-descriptor.json"
                , subFormDescriptorsJSON, (m, e, a) -> {
                    assertEquals(m, e, a);
                });
    }

    public void testGetPluginFormPropertyTypes() {
        DataxMySQLReader mySQLReader = new DataxMySQLReader();
        Descriptor<DataxReader> descriptor = mySQLReader.getDescriptor();
        assertNotNull(descriptor);

        PluginFormProperties propertyTypes = descriptor.getPluginFormPropertyTypes();
        assertEquals(4, propertyTypes.getKVTuples().size());
    }

    public void testGetSubTasks() {
        MySQLDataSourceFactory mysqlDs = new MySQLDataSourceFactory() {
//            @Override
//            protected Connection getConnection(String jdbcUrl, String username, String password) throws SQLException {
//                throw new UnsupportedOperationException();
//            }

            @Override
            public List<ColumnMetaData> getTableMetadata(String table) {
                switch (table) {
                    case TestSelectedTabs.tabNameOrderDetail:
                        return TestSelectedTabs.tabColsMetaOrderDetail;
                    case TestSelectedTabs.tabNameTotalpayinfo:
                        return TestSelectedTabs.tabColsMetaTotalpayinfo;
                    default:
                        throw new IllegalArgumentException("table:" + table);
                }
            }
        };
        mysqlDs.dbName = dbName;
        mysqlDs.port = 3306;
        mysqlDs.encode = "utf8";
        mysqlDs.userName = userName;
        mysqlDs.password = password;
        mysqlDs.nodeDesc = "192.168.28.200[0-7]";


        Descriptor.ParseDescribable<DataSourceFactory> desc = new Descriptor.ParseDescribable<>(mysqlDs);
        Context context = EasyMock.createMock("context", Context.class);
        EasyMock.expect(context.hasErrors()).andReturn(false);
        IPluginContext pluginContext = EasyMock.createMock("pluginContext", IPluginContext.class);
        pluginContext.addDb(desc, dbName, context, true);
        EasyMock.replay(pluginContext, context);
        DataSourceFactoryPluginStore dbStore = TIS.getDataBasePluginStore(new PostedDSProp(dbName));
        assertTrue("save mysql db Config faild", dbStore.setPlugins(pluginContext, Optional.of(context), Collections.singletonList(desc)).success);

        DataxMySQLReader mySQLReader = new DataxMySQLReader() {
            @Override
            public MySQLDataSourceFactory getDataSourceFactory() {
                return mysqlDs;
            }
        };
        mySQLReader.dataXName = this.dataXName;
        List<SelectedTab> selectedTabs = TestSelectedTabs.createSelectedTabs();

        mySQLReader.setSelectedTabs(selectedTabs);
        List<SelectedTab> selectedTabs2 = mySQLReader.getSelectedTabs();
        assertEquals(2, selectedTabs2.size());
        for (SelectedTab tab : selectedTabs2) {
            tab.getCols().forEach((c) -> {
                assertNotNull("table:" + tab.getName() + "'s col " + c.getName() + " relevant type can not be null", c.getType());
            });
        }


        List<String> tabs = Lists.newArrayList();
        for (SelectedTab tab : selectedTabs) {
            for (int i = 0; i < 8; i++) {
                tabs.add(tab.name);
            }
        }


        int readerContextCount = 0;
        IDataxReaderContext readerContext = null;
        IGroupChildTaskIterator subTasks = mySQLReader.getSubTasks();

        while (subTasks.hasNext()) {
            readerContext = subTasks.next();
            assertEquals(tabs.get(readerContextCount), readerContext.getSourceEntityName());
            assertEquals(tabs.get(readerContextCount) + "_" + readerContextCount, readerContext.getTaskName());
            System.out.println(readerContext.getSourceEntityName() + " " + readerContext.getTaskName());
            assertNotNull(readerContext);
            readerContextCount++;
        }
        assertEquals(16, readerContextCount);


        Map<String, List<String>> groupedInfo = subTasks.getGroupedInfo();
        assertNotNull("groupedInfo can not be null", groupedInfo);

        List<String> subTabs = groupedInfo.get(TestSelectedTabs.tabNameOrderDetail);
        assertEquals(8, subTabs.size());

        subTabs = groupedInfo.get(TestSelectedTabs.tabNameTotalpayinfo);
        assertEquals(8, subTabs.size());


        EasyMock.verify(pluginContext, context);
    }

    public void testTempateGenerate() throws Exception {

        Optional<PluginExtraProps> extraProps = PluginExtraProps.load(DataxMySQLReader.class);
        assertTrue("DataxMySQLReader extraProps shall exist", extraProps.isPresent());


        //IPluginContext pluginContext = null;


        // IDataxProcessor processor = EasyMock.mock("dataxProcessor", IDataxProcessor.class);
        //IDataxWriter dataxWriter = EasyMock.mock("dataxWriter", IDataxWriter.class);

        //  EasyMock.expect(dataxWriter.getSubTask(Optional.empty())).andReturn(null).anyTimes();
        // IDataxGlobalCfg dataxGlobalCfg = EasyMock.mock("dataxGlobalCfg", IDataxGlobalCfg.class);
        // IDataxReaderContext dataxReaderContext = EasyMock.mock("dataxReaderContext", IDataxReaderContext.class);


//        MySQLDataXReaderContext dataxReaderContext = new MySQLDataXReaderContext(tabName + "_0", tabName);
//        dataxReaderContext.jdbcUrl = TestDataxMySQLWriter.mysqlJdbcUrl;
//        dataxReaderContext.tabName = tabName;
//        dataxReaderContext.username = userName;
//        dataxReaderContext.password = password;
//        dataxReaderContext.cols = Lists.newArrayList("col1", "col2", "col3");//tableMetadata.stream().map((t) -> t.getValue()).collect(Collectors.toList());

//        EasyMock.expect(processor.getWriter()).andReturn(dataxWriter).anyTimes();
//        EasyMock.expect(processor.getDataXGlobalCfg()).andReturn(dataxGlobalCfg).anyTimes();


        MySQLDataSourceFactory mysqlDataSource = EasyMock.createMock("mysqlDataSourceFactory", MySQLDataSourceFactory.class);
        EasyMock.expect(mysqlDataSource.getPassword()).andReturn(password).anyTimes();
        EasyMock.expect(mysqlDataSource.getUserName()).andReturn(userName).anyTimes();
        IDataSourceDumper dataDumper = EasyMock.createMock(TestSelectedTabs.tabNameOrderDetail + "TableDumper", IDataSourceDumper.class);
        EasyMock.expect(dataDumper.getDbHost()).andReturn(TestDataxMySQLWriter.mysqlJdbcUrl).times(2);
// int index, String key, int type, boolean pk
        TISTable targetTable = new TISTable();
        targetTable.setTableName(TestSelectedTabs.tabNameOrderDetail);

        EasyMock.expect(mysqlDataSource.getTableMetadata(TestSelectedTabs.tabNameOrderDetail))
                .andReturn(TestSelectedTabs.tabColsMetaOrderDetail).anyTimes();


        EasyMock.expect(mysqlDataSource.getDataDumpers(targetTable)).andDelegateTo(new MySQLDataSourceFactory() {
//            @Override
//            protected Connection getConnection(String jdbcUrl, String username, String password) throws SQLException {
//                throw new UnsupportedOperationException();
//            }

            @Override
            public DataDumpers getDataDumpers(TISTable table) {
                return new DataDumpers(1, Collections.singletonList(dataDumper).iterator());
            }
        }).times(2);//.andReturn(new DataDumpers(1, Collections.singletonList(dataDumper).iterator()));


        EasyMock.replay(mysqlDataSource, dataDumper);


        DataxMySQLReader mySQLReader = new DataxMySQLReader() {
            @Override
            public MySQLDataSourceFactory getDataSourceFactory() {
                return mysqlDataSource;
            }

            @Override
            public Class<?> getOwnerClass() {
                return DataxMySQLReader.class;
            }
        };
        mySQLReader.dataXName = dataXName;
        mySQLReader.template = DataxMySQLReader.getDftTemplate();

        SelectedTab selectedTab = new SelectedTab();
        selectedTab.setCols(Lists.newArrayList("col2", "col1", "col3"));
        selectedTab.setWhere("delete = 0");
        selectedTab.name = TestSelectedTabs.tabNameOrderDetail;
        mySQLReader.setSelectedTabs(Collections.singletonList(selectedTab));
        //校验证列和 where条件都设置的情况
        // valiateReaderCfgGenerate("mysql-datax-reader-assert.json", processor, mySQLReader);

        ReaderTemplate.validateDataXReader("mysql-datax-reader-assert.json", dataXName, mySQLReader);

//        selectedTab = new SelectedTab();
//        selectedTab.setCols(Collections.emptyList());
//        selectedTab.name = TestSelectedTabs.tabNameOrderDetail;

        mySQLReader.setSelectedTabs(TestSelectedTabs.createSelectedTabs(1));
//        valiateReaderCfgGenerate("mysql-datax-reader-asser-without-option-val.json"
//                , processor, mySQLReader);

        ReaderTemplate.validateDataXReader("mysql-datax-reader-asser-without-option-val.json", dataXName, mySQLReader);

        EasyMock.verify(mysqlDataSource, dataDumper);
    }

//    private void valiateReaderCfgGenerate(String assertFileName, IDataxProcessor processor, DataxMySQLReader mySQLReader) throws IOException {
////        List<SelectedTab> selectedTabs = Lists.newArrayList();
////
////        selectedTabs.add(selectedTab);
////        mySQLReader.setSelectedTabs(selectedTabs);
//        MySQLDataXReaderContext dataxReaderContext = null;
//        Iterator<IDataxReaderContext> subTasks = mySQLReader.getSubTasks();
//        int dataxReaderContextCount = 0;
//        while (subTasks.hasNext()) {
//            dataxReaderContext = (MySQLDataXReaderContext) subTasks.next();
//            dataxReaderContextCount++;
//        }
//        assertEquals(1, dataxReaderContextCount);
//        assertNotNull(dataxReaderContext);
//
//
//        DataXCfgGenerator dataProcessor = new DataXCfgGenerator(processor) {
//            @Override
//            public String getTemplateContent() {
//                return mySQLReader.getTemplate();
//            }
//        };
//
//        String readerCfg = dataProcessor.generateDataxConfig(dataxReaderContext, Optional.empty());
//        assertNotNull(readerCfg);
//        System.out.println(readerCfg);
//        JsonUtils.assertJSONEqual(this.getClass(), assertFileName, readerCfg);
//    }

    public void testGetDftTemplate() {
        String dftTemplate = DataxMySQLReader.getDftTemplate();
        assertNotNull("dftTemplate can not be null", dftTemplate);
    }

    public void testPluginExtraPropsLoad() throws Exception {
        Optional<PluginExtraProps> extraProps = PluginExtraProps.load(DataxMySQLReader.class);
        assertTrue(extraProps.isPresent());
    }

    public void testRealDump() throws Exception {

        DataxMySQLReader dataxReader = createHdfsReader(dataXName);
        DataxReader.dataxReaderGetter = (name) -> {
            assertEquals(dataXName, name);
            return dataxReader;
        };

        ReaderTemplate.realExecute("mysql-datax-reader-test-cfg.json", dataxReader);
    }

    protected DataxMySQLReader createHdfsReader(String dataXName) {

        DataxMySQLReader dataxReader = new DataxMySQLReader() {
            @Override
            public Class<?> getOwnerClass() {
                return DataxMySQLReader.class;
            }
        };
        dataxReader.fetchSize = 2000;
        dataxReader.dataXName = dataXName;
        dataxReader.template = DataxMySQLReader.getDftTemplate();

        return dataxReader;
    }

}

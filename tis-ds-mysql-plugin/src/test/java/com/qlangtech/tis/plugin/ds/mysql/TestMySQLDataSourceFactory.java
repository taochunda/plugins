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

package com.qlangtech.tis.plugin.ds.mysql;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.manage.common.CenterResource;
import com.qlangtech.tis.manage.common.HttpUtils;
import com.qlangtech.tis.manage.common.TisUTF8;
import com.qlangtech.tis.plugin.ds.*;
import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author: baisui 百岁
 * @create: 2020-11-24 17:42
 **/
public class TestMySQLDataSourceFactory extends TestCase {

    private static final String DB_ORDER = "order1";

    static {
        CenterResource.setNotFetchFromCenterRepository();
        HttpUtils.addMockGlobalParametersConfig();
    }

    private static final String empNo = "emp_no";

    public void testGetPlugin() throws Exception {

        DataSourceFactoryPluginStore dbPluginStore = TIS.getDataBasePluginStore(new PostedDSProp(DB_ORDER));

        DataSourceFactory dataSourceFactory = dbPluginStore.getPlugin();

        assertNotNull(dataSourceFactory);

        FacadeDataSource datasource = dbPluginStore.createFacadeDataSource();
        assertNotNull(datasource);

//        List<Descriptor<DataSourceFactory>> descList
//                = TIS.get().getDescriptorList(DataSourceFactory.class);
//        assertNotNull(descList);
//        assertEquals(1, descList.size());


//        Descriptor<DataSourceFactory> mysqlDS = descList.get(0);
//
//        mysqlDS.validate()
    }

    public void testDataDumpers() throws Exception {
        MySQLDataSourceFactory dataSourceFactory = new MySQLDataSourceFactory();
        dataSourceFactory.dbName = "employees";
        dataSourceFactory.password = null;
        dataSourceFactory.userName = "root";
        dataSourceFactory.nodeDesc = "192.168.28.202";
        dataSourceFactory.port = 4000;
        dataSourceFactory.encode = "utf8";
        TISTable dumpTable = new TISTable();
        dumpTable.setSelectSql("SELECT emp_no,birth_date,first_name,last_name,gender,hire_date FROM employees");
        dumpTable.setTableName("employees");
        DataDumpers dataDumpers = dataSourceFactory.getDataDumpers(dumpTable);
        assertNotNull("dataDumpers can not be null", dataDumpers);

        assertEquals(1, dataDumpers.splitCount);
        Iterator<IDataSourceDumper> dumpers = dataDumpers.dumpers;
        Map<String, String> row = null;
        int testRows = 0;

        List<Map<String, String>> exampleRows = getExampleRows();

        while (dumpers.hasNext()) {
            IDataSourceDumper dumper = null;
            try {
                dumper = dumpers.next();
                assertEquals(300024, dumper.getRowSize());
                Iterator<Map<String, String>> mapIterator = dumper.startDump();
                assertNotNull(mapIterator);
                while (mapIterator.hasNext()) {
                    row = mapIterator.next();
                    assertTrue(empNo + " can not empty", StringUtils.isNotEmpty(row.get(empNo)));
                    Map<String, String> exampleRow = exampleRows.get(testRows);
                    for (Map.Entry<String, String> entry : exampleRow.entrySet()) {
                        assertEquals(entry.getKey() + " shall be equal", entry.getValue(), row.get(entry.getKey()));
                    }
                    if (++testRows >= exampleRows.size()) {
                        break;
                    }
                }
            } finally {
                dumper.closeResource();
            }
        }
    }

    private List<Map<String, String>> getExampleRows() throws Exception {
        List<Map<String, String>> result = Lists.newArrayList();
        Map<String, String> r = null;
        List<String> titles = null;
        String[] row = null;
        int colIndex;
        try (InputStream input = TestMySQLDataSourceFactory.class.getResourceAsStream("employees_pre_20_rows.txt")) {
            LineIterator li = IOUtils.lineIterator(input, TisUTF8.get());
            li.hasNext();
            li.next();
            li.hasNext();
            titles = Arrays.stream(StringUtils.split(li.nextLine(), "|")).map((t) -> StringUtils.trimToEmpty(t)).collect(Collectors.toList());
            li.hasNext();
            li.next();
            while (li.hasNext()) {
                row = StringUtils.split(li.next(), "|");
                r = Maps.newHashMap();
                colIndex = 0;
                for (String title : titles) {
                    r.put(title, StringUtils.trimToNull(row[colIndex++]));
                }
                result.add(r);
            }
        }
        return result;
    }

}

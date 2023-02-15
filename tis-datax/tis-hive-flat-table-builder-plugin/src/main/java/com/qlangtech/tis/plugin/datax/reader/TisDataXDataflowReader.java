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

package com.qlangtech.tis.plugin.datax.reader;

import com.qlangtech.tis.datax.IGroupChildTaskIterator;
import com.qlangtech.tis.datax.impl.DataxProcessor;
import com.qlangtech.tis.datax.impl.DataxReader;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.plugin.StoreResourceType;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.SubForm;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.datax.DataFlowDataXProcessor;
import com.qlangtech.tis.plugin.datax.SelectedTab;
import com.qlangtech.tis.plugin.ds.DBIdentity;
import com.qlangtech.tis.plugin.ds.ISelectedTab;
import com.qlangtech.tis.plugin.ds.TableInDB;

import java.util.List;
import java.util.function.Predicate;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2023-02-15 16:35
 **/
public class TisDataXDataflowReader extends DataxReader {

    public static final String DISPLAY_NAME = "DataFlow";

    @FormField(type = FormFieldType.ENUM, validate = Validator.require)
    public String dataflow;

    @SubForm(desClazz = SelectedTab.class
            , idListGetScript = "return com.qlangtech.tis.coredefine.module.action.DataxAction.getTablesInDB(filter);", atLeastOne = true)
    public transient List<SelectedTab> selectedTabs;


    @Override
    public TableInDB getTablesInDB() {
        TableInDB tabsInDB = TableInDB.create(DBIdentity.parseId(this.dataflow));
        tabsInDB.add(null, "orderinfo");
        return tabsInDB;
    }

    @Override
    public List<SelectedTab> getSelectedTabs() {
        return this.selectedTabs;
    }

    public DataFlowDataXProcessor getProcessor() {
        return (DataFlowDataXProcessor) DataxProcessor.load(null, StoreResourceType.DataFlow, this.dataflow);
    }

    @Override
    public IGroupChildTaskIterator getSubTasks(Predicate<ISelectedTab> filter) {
        return null;
    }

    @Override
    public String getTemplate() {
        return null;
    }

    @TISExtension
    public static class DefaultDescriptor extends BaseDataxReaderDescriptor {
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        @Override
        public boolean isRdbms() {
            return true;
        }

        @Override
        public boolean isSupportIncr() {
            return true;
        }

        @Override
        public EndType getEndType() {
            return EndType.DataFlow;
        }
    }
}

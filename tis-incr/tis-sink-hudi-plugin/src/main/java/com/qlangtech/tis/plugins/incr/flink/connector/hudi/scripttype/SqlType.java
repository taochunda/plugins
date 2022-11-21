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

package com.qlangtech.tis.plugins.incr.flink.connector.hudi.scripttype;

import com.qlangtech.tis.datax.IStreamTableMeataCreator;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugins.incr.flink.connector.streamscript.BasicFlinkStreamScriptCreator;
import com.qlangtech.tis.plugins.incr.flink.connector.hudi.streamscript.SQLStyleFlinkStreamScriptCreator;
import com.qlangtech.tis.plugins.incr.flink.connector.scripttype.IStreamScriptType;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-03-31 11:43
 **/
public class SqlType extends HudiStreamScriptType {
    @FormField(ordinal = 1, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.db_col_name})
    public String catalog;

    @FormField(ordinal = 2, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.db_col_name})
    public String database;

    @Override
    public BasicFlinkStreamScriptCreator createStreamTableCreator(IStreamTableMeataCreator.ISinkStreamMetaCreator sinkStreamMetaCreator) {
        return new SQLStyleFlinkStreamScriptCreator(sinkStreamMetaCreator);
    }

    // 暂时先不用
    // @TISExtension
    public static class DefaultDescriptor extends Descriptor<HudiStreamScriptType> {
        @Override
        public String getDisplayName() {
            return "SQL";
        }
    }
}

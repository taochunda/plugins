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

package com.qlangtech.tis.plugins.incr.flink.chunjun.kafka.format;

import com.qlangtech.tis.extension.Describable;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.data.RowData;

/**
 * 内容传输格式
 * https://nightlies.apache.org/flink/flink-docs-release-1.16/docs/connectors/table/formats/overview/
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2023-04-15 12:24
 **/
public abstract class FormatFactory implements Describable<FormatFactory> {

    /**
     *
     * @param targetTabName 目标表名称
     * @return
     */
    public abstract EncodingFormat<SerializationSchema<RowData>> createEncodingFormat(final String targetTabName);
}

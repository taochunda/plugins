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

package com.qlangtech.plugins.incr.flink.chunjun.clickhouse.sink;

import com.dtstack.chunjun.connector.jdbc.conf.JdbcConf;
import com.dtstack.chunjun.connector.jdbc.dialect.JdbcDialect;
import com.dtstack.chunjun.connector.jdbc.sink.JdbcOutputFormat;
import com.google.common.collect.Sets;
import com.qlangtech.tis.compiler.incr.ICompileAndPackage;
import com.qlangtech.tis.compiler.streamcode.CompileAndPackage;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.plugin.IEndTypeGetter;
import com.qlangtech.tis.plugin.ds.DataSourceFactory;
import com.qlangtech.tis.plugins.incr.flink.chunjun.common.ColMetaUtils;
import com.qlangtech.tis.plugins.incr.flink.connector.ChunjunSinkFactory;

/**
 * https://dtstack.github.io/chunjun/documents/ChunJun%E8%BF%9E%E6%8E%A5%E5%99%A8@clickhouse@clickhouse-sink
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-08-14 22:29
 **/
public class ChunjunClickhouseSinkFactory extends ChunjunSinkFactory {


    @Override
    protected Class<? extends JdbcDialect> getJdbcDialectClass() {
        return TISClickhouseDialect.class;
    }

    @Override
    protected boolean supportUpsetDML() {
        return true;
    }

    @Override
    protected JdbcOutputFormat createChunjunOutputFormat(DataSourceFactory dsFactory, JdbcConf jdbcConf) {
        return new TISClickhouseOutputFormat(dsFactory, ColMetaUtils.getColMetasMap(this, jdbcConf));
    }

    @Override
    protected void initChunjunJdbcConf(JdbcConf jdbcConf) {

    }

    @Override
    public ICompileAndPackage getCompileAndPackageManager() {
        return new CompileAndPackage(Sets.newHashSet(
                //  "tis-sink-hudi-plugin"
                ChunjunClickhouseSinkFactory.class
                // "tis-datax-hudi-plugin"
                // , "com.alibaba.datax.plugin.writer.hudi.HudiConfig"
        ));
    }

    @TISExtension
    public static class DftDesc extends BasicChunjunSinkDescriptor {
        @Override
        protected IEndTypeGetter.EndType getTargetType() {
            return IEndTypeGetter.EndType.Clickhouse;
        }
    }
}

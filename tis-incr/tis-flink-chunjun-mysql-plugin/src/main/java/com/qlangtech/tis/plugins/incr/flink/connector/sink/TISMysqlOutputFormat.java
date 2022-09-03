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

package com.qlangtech.tis.plugins.incr.flink.connector.sink;

import com.dtstack.chunjun.connector.jdbc.TableCols;
import com.dtstack.chunjun.connector.mysql.sink.MysqlOutputFormat;
import com.qlangtech.plugins.incr.flink.chunjun.common.ColMetaUtils;
import com.qlangtech.tis.plugin.ds.DataSourceFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-07-22 13:08
 **/
public final class TISMysqlOutputFormat extends MysqlOutputFormat {
    private final DataSourceFactory dsFactory;

    public TISMysqlOutputFormat(DataSourceFactory dsFactory) {
        super();
        if (dsFactory == null) {
            throw new IllegalArgumentException("param dsFactory can not be null");
        }
        this.dsFactory = dsFactory;
    }

    @Override
    protected Connection getConnection() throws SQLException {
        DataSourceFactory dsFactory = Objects.requireNonNull(this.dsFactory, "dsFactory can not be null");
        return dsFactory.getConnection(this.jdbcConf.getJdbcUrl());
    }

    @Override
    protected Map<String,TableCols.ColMeta> getTableMetaData() {
        return ColMetaUtils.getColMetasMap(this.dsFactory, this.dbConn, this.jdbcConf);
    }
}

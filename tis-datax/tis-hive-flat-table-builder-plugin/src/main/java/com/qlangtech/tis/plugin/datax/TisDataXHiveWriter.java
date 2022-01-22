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

import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.hdfswriter.HdfsHelper;
import com.alibaba.datax.plugin.writer.hdfswriter.HdfsWriter;
import com.qlangtech.tis.datax.impl.DataxWriter;
import com.qlangtech.tis.offline.DataxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-05-27 16:55
 **/
public class TisDataXHiveWriter extends Writer {

    public static final String KEY_HIVE_TAB_NAME = "hiveTableName";


    static final Logger logger = LoggerFactory.getLogger(TisDataXHiveWriter.class);
//    static final Field jobColumnsField;
//    static final Field jobFileType;
//    static final Field jobFieldDelimiter;
//    static final Field jobPath;

//    static {
//        jobColumnsField = getJobField("columns");
//        jobFileType = getJobField("fileType");
//        jobFieldDelimiter = getJobField("fieldDelimiter");
//        jobPath = getJobField("path");
//    }

//    private static Field getJobField(String fieldName) {
//        try {
//            Field field = HdfsWriter.Job.class.getDeclaredField(fieldName);
//            field.setAccessible(true);
//            return field;
//        } catch (NoSuchFieldException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public static class Job extends BasicEngineJob<DataXHiveWriter> {

    }

    public static class Task extends HdfsWriter.Task {
       // private Configuration cfg;
        private BasicFSWriter writerPlugin;

        @Override
        public void init() {
            this.writerPlugin = getHdfsWriterPlugin(this.getPluginJobConf());
            super.init();

            validateFileNameVal();
            //  BasicHdfsWriterJob.setHdfsHelper(this.getPluginJobConf(), BasicHdfsWriterJob.taskHdfsHelperField, getHdfsWriterPlugin(getPluginJobConf()), this);
        }

        @Override
        protected HdfsHelper createHdfsHelper() {
            return BasicHdfsWriterJob.createHdfsHelper(this.getPluginJobConf(), this.writerPlugin);
            // return new HdfsHelper(this.writerPlugin.getFs().getFileSystem().unwrap());
        }

        private void validateFileNameVal() {
            Object val = null;
            try {
                Field fileName = HdfsWriter.Task.class.getDeclaredField("fileName");
                fileName.setAccessible(true);
                val = fileName.get(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (val == null) {
                throw new IllegalStateException("fileName prop have not been init valid");
            }
        }

    }


    static <TT extends BasicFSWriter> TT getHdfsWriterPlugin(Configuration cfg) {
        String dataxName = cfg.getString(DataxUtils.DATAX_NAME);
        DataxWriter dataxWriter = DataxWriter.load(null, dataxName);
        if (!(dataxWriter instanceof BasicFSWriter)) {
            throw new BasicEngineJob.JobPropInitializeException("datax Writer must be type of 'BasicFSWriter',but now is:" + dataxWriter.getClass());
        }
        return (TT) dataxWriter;
    }

}

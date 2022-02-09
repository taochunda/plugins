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

package com.qlangtech.tis.hdfs.test;

import com.qlangtech.tis.coredefine.module.action.TargetResName;
import com.qlangtech.tis.extension.impl.IOUtils;
import com.qlangtech.tis.hdfs.impl.HdfsFileSystemFactory;

/**
 * 提供给单元测试用
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-01-24 16:49
 **/
public class HdfsFileSystemFactoryTestUtils {
    public static final String FS_NAME = "testhdfs1";
    public static final TargetResName testDataXName = new TargetResName("testDataX4465");
    public static final String DEFAULT_HDFS_ADDRESS = "hdfs://namenode";

    public static HdfsFileSystemFactory getFileSystemFactory() {
        HdfsFileSystemFactory fsFactory = new HdfsFileSystemFactory();
        fsFactory.name = FS_NAME;
        fsFactory.setHdfsAddress(DEFAULT_HDFS_ADDRESS);
        fsFactory.userHostname = true;
        fsFactory.setHdfsSiteContent(IOUtils.loadResourceFromClasspath(
                HdfsFileSystemFactoryTestUtils.class, "hdfs/hdfsSiteContent.xml"));
        fsFactory.rootDir = "/user/admin";
        return fsFactory;
    }
}
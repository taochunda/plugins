/* * Copyright 2020 QingLang, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qlangtech.tis.hdfs.impl;

import com.qlangtech.tis.fs.IFileSplit;
import com.qlangtech.tis.fs.IPath;
import org.apache.hadoop.mapred.FileSplit;

/* *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2018年12月10日
 */
public class HdfsFileSplit implements IFileSplit {

    private final FileSplit split;

    private final IPath path;

    public HdfsFileSplit(FileSplit split) {
        super();
        this.split = split;
        this.path = new HdfsPath(split.getPath());
    }

    public IPath getPath() {
        return this.path;
    }

    public long getStart() {
        return split.getStart();
    }

    public long getLength() {
        return split.getLength();
    }
}
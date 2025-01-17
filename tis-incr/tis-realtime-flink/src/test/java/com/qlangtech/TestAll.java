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

package com.qlangtech;

import com.qlangtech.plugins.incr.flink.common.TestFlinkCluster;
import com.qlangtech.plugins.incr.flink.launch.TestFlinkIncrJobStatus;
import com.qlangtech.plugins.incr.flink.launch.TestFlinkTaskNodeController;
import com.qlangtech.plugins.incr.flink.launch.TestTISFlinkCDCStreamFactory;
import com.qlangtech.plugins.incr.flink.launch.ckpt.TestCheckpointFactory;
import com.qlangtech.tis.plugins.flink.client.TestFlinkClient;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2022-04-15 19:05
 **/

@RunWith(Suite.class)
@Suite.SuiteClasses(
        {TestFlinkCluster.class,
                TestTISFlinkCDCStreamFactory.class,
                TestFlinkIncrJobStatus.class
                , TestFlinkTaskNodeController.class
                , TestFlinkClient.class
                , TestCheckpointFactory.class})
public class TestAll {

}

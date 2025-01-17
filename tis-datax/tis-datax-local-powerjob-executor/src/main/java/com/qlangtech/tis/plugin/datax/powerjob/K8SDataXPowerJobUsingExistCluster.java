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

package com.qlangtech.tis.plugin.datax.powerjob;

import com.alibaba.citrus.turbine.Context;
import com.qlangtech.tis.annotation.Public;
import com.qlangtech.tis.coredefine.module.action.TargetResName;
import com.qlangtech.tis.coredefine.module.action.impl.RcDeployment;
import com.qlangtech.tis.datax.job.ITISPowerJob;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.lang.TisException;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.plugin.datax.powerjob.impl.BasicPowerjobWorker;
import com.qlangtech.tis.runtime.module.misc.IControlMsgHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.powerjob.client.PowerJobClient;
import tech.powerjob.common.response.JobInfoDTO;
import tech.powerjob.common.response.ResultDTO;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;


/**
 * 使用已经存在的Powerjob Cluster机群
 * https://www.yuque.com/powerjob/guidence/deploy_server
 *
 * @author: 百岁（baisui@qlangtech.com）
 * @create: 2021-04-23 18:16
 * // @see PowerJobClient
 **/
@Public
public class K8SDataXPowerJobUsingExistCluster extends BasicPowerjobWorker implements ITISPowerJob {

    private static final Logger logger = LoggerFactory.getLogger(K8SDataXPowerJobUsingExistCluster.class);

    @FormField(ordinal = 0, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.host})
    public String serverAddress;
    @FormField(ordinal = 1, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.db_col_name})
    public String appName;

    @FormField(ordinal = 2, type = FormFieldType.PASSWORD, validate = {Validator.require})
    public String password;

    @Override
    public TISPowerJobClient getPowerJobClient() {
        return this.createPowerJobClient();
    }

    public TISPowerJobClient createPowerJobClient() {
        TISPowerJobClient powerJobClient = new TISPowerJobClient(this.serverAddress, this.appName, this.password);


        return powerJobClient;
    }

    @Override
    public RcDeployment getRCDeployment() {
        // throw new UnsupportedOperationException();
        return new RcDeployment();
    }

    @Override
    public void remove() {
        if (!this.inService()) {
            throw new IllegalStateException("job worker is not in service, relevant clusterId:" + this.serverAddress);
        }
        this.deleteLaunchToken();
    }

    @Override
    public void launchService(Runnable launchProcess) {
        testPowerJobClient((result) -> {
        });

        launchProcess.run();

        try {
            this.writeLaunchToken();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean testPowerJobClient(Consumer<ResultDTO<List<JobInfoDTO>>> result) {
        try {
            PowerJobClient powerJobClient = this.createPowerJobClient();

            ResultDTO<List<JobInfoDTO>> jobs = powerJobClient.fetchAllJob();
            if (!jobs.isSuccess()) {
                result.accept(jobs);
                //  msgHandler.addErrorMessage(context, jobs.getMessage());
                return false;
            }
        } catch (tech.powerjob.common.exception.PowerJobException e) {
            throw TisException.create(e.getMessage(), e);
        }
        return true;
    }

    @TISExtension()
    public static class DescriptorImpl extends BasicDescriptor {

        public DescriptorImpl() {
            super();
        }


        @Override
        protected TargetResName getWorkerType() {
            return K8S_DATAX_INSTANCE_NAME;
        }

        @Override
        protected boolean verify(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            K8SDataXPowerJobUsingExistCluster existCluster = postFormVals.newInstance();

            return existCluster.testPowerJobClient((jobs) -> {
                msgHandler.addErrorMessage(context, jobs.getMessage());
            });
//            try {
//                PowerJobClient powerJobClient = existCluster.createPowerJobClient();
//
//                ResultDTO<List<JobInfoDTO>> jobs = powerJobClient.fetchAllJob();
//                if (!jobs.isSuccess()) {
//                    msgHandler.addErrorMessage(context, jobs.getMessage());
//                    return false;
//                }
//            } catch (tech.powerjob.common.exception.PowerJobException e) {
//                throw TisException.create(e.getMessage(), e);
//            }
//
//            return true;
        }

        @Override
        protected boolean validateAll(IControlMsgHandler msgHandler, Context context, PostFormVals postFormVals) {
            return true;
        }

        @Override
        protected K8SWorkerCptType getWorkerCptType() {
            return K8SWorkerCptType.UsingExistCluster;
        }
    }

}

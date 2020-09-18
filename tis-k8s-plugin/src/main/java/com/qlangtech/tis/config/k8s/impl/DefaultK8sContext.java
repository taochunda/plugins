/**
 * Copyright 2020 QingLang, Inc.
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qlangtech.tis.config.k8s.impl;

import com.alibaba.citrus.turbine.Context;
import com.qlangtech.tis.config.ParamsConfig;
import com.qlangtech.tis.config.k8s.IK8sContext;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.Reader;
import java.io.StringReader;

/**
 * DefaultK8sContext
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2020/04/13
 */
public class DefaultK8sContext extends ParamsConfig implements IK8sContext {

    @FormField(ordinal = 0, validate = {Validator.require, Validator.identity})
    public String name;

    @FormField(ordinal = 1, validate = {Validator.require, Validator.url})
    public String kubeBasePath;

    @FormField(ordinal = 2, type = FormFieldType.TEXTAREA, validate = {Validator.require})
    public String kubeConfigContent;

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ApiClient createConfigInstance() {

        ApiClient client = null;
        try {
            try (Reader reader = new StringReader(this.kubeConfigContent)) {
                client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).setBasePath(this.kubeBasePath).build();
                // 30秒连接超时
                client.setConnectTimeout(30000);
                client.setReadTimeout(30000);
                // client.getHttpClient().setReadTimeout(720, TimeUnit.SECONDS);
                Configuration.setDefaultApiClient(client);
            }
            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @TISExtension()
    public static class DefaultDescriptor extends Descriptor<ParamsConfig> {

        public DefaultDescriptor() {
            super();
            this.load();
        }

        @Override
        public String getDisplayName() {
            return "k8s";
        }

        public boolean validateKubeConfigContent(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            final Yaml yaml = new Yaml(new SafeConstructor());
            try {
                try (Reader reader = new StringReader(value)) {
                    Object config = yaml.load(reader);
                }
            } catch (Exception e) {
                msgHandler.addFieldError(context, fieldName, e.getMessage());
                return false;
            }
            return true;
        }
    }
}

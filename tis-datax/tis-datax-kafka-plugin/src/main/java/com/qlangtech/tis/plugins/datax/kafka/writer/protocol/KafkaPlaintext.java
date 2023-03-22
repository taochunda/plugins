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

package com.qlangtech.tis.plugins.datax.kafka.writer.protocol;

import com.google.common.collect.ImmutableMap;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.plugins.datax.kafka.writer.TISKafkaProtocol;

public class KafkaPlaintext extends KafkaProtocol {

//    @FormField(ordinal = 0, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
//    public String securityProtocol;


    @Override
    protected void addCustomersProps(ImmutableMap.Builder<String, String> builder) {

    }

    @Override
    protected TISKafkaProtocol getKafkaProtocol() {
        return TISKafkaProtocol.PLAINTEXT;
    }

    @TISExtension
    public static class DefaultDescriptor extends Descriptor<KafkaProtocol> {
        @Override
        public String getDisplayName() {
            return "PLAINTEXT";
        }
    }
}

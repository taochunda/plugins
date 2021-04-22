/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 *   This program is free software: you can use, redistribute, and/or modify
 *   it under the terms of the GNU Affero General Public License, version 3
 *   or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *   FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.qlangtech.tis.plugin.incr;

import com.qlangtech.tis.config.ParamsConfig;
import com.qlangtech.tis.config.k8s.IK8sContext;
import com.qlangtech.tis.coredefine.module.action.IIncrSync;
import com.qlangtech.tis.extension.Descriptor;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;

/**
 * @create: 2020-04-12 11:06
 *
 * @author 百岁（baisui@qlangtech.com）
 * @date 2020/04/13
 */
public class DefaultIncrK8sConfig extends IncrStreamFactory {

    public static final String KEY_FIELD_NAME = "k8sName";

    @FormField(identity = true, ordinal = 0, type = FormFieldType.SELECTABLE, validate = {Validator.require})
    public String k8sName;

    @FormField(ordinal = 1, type = FormFieldType.INPUTTEXT, validate = {Validator.require, Validator.identity})
    public String namespace;

    @FormField(ordinal = 2, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
    public String // = "docker-registry.default.svc:5000/tis/tis-incr:latest";
            imagePath;

//    public String getName() {
//        return this.k8sName;
//    }

    public ParamsConfig getK8SContext() {
        return (ParamsConfig) ParamsConfig.getItem(this.k8sName, IK8sContext.class);
    }

    private IIncrSync incrSync;

    @Override
    public IIncrSync getIncrSync() {
        if (incrSync != null) {
            return this.incrSync;
        }
        this.incrSync = new K8sIncrSync(this);
        return this.incrSync;
    }

    @TISExtension()
    public static class DescriptorImpl extends Descriptor<IncrStreamFactory> {

        public DescriptorImpl() {
            super();
            this.registerSelectOptions(KEY_FIELD_NAME, () -> ParamsConfig.getItems(IK8sContext.class));
        }

        @Override
        public String getDisplayName() {
            return "k8s-incr";
        }
    }
}

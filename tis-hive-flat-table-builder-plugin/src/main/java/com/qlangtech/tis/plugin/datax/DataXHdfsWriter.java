/**
 * Copyright (c) 2020 QingLang, Inc. <baisui@qlangtech.com>
 * <p>
 * This program is free software: you can use, redistribute, and/or modify
 * it under the terms of the GNU Affero General Public License, version 3
 * or later ("AGPL"), as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.qlangtech.tis.plugin.datax;

import com.alibaba.citrus.turbine.Context;
import com.alibaba.datax.plugin.writer.hdfswriter.SupportHiveDataType;
import com.qlangtech.tis.TIS;
import com.qlangtech.tis.datax.IDataxContext;
import com.qlangtech.tis.datax.IDataxProcessor;
import com.qlangtech.tis.datax.ISelectedTab;
import com.qlangtech.tis.datax.impl.DataxWriter;
import com.qlangtech.tis.extension.TISExtension;
import com.qlangtech.tis.extension.impl.IOUtils;
import com.qlangtech.tis.hdfs.impl.HdfsFileSystemFactory;
import com.qlangtech.tis.hive.HiveColumn;
import com.qlangtech.tis.offline.FileSystemFactory;
import com.qlangtech.tis.offline.flattable.HiveFlatTableBuilder;
import com.qlangtech.tis.plugin.KeyedPluginStore;
import com.qlangtech.tis.plugin.annotation.FormField;
import com.qlangtech.tis.plugin.annotation.FormFieldType;
import com.qlangtech.tis.plugin.annotation.Validator;
import com.qlangtech.tis.runtime.module.misc.IFieldErrorHandler;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * https://github.com/alibaba/DataX/blob/master/hdfswriter/doc/hdfswriter.md
 *
 * @author: baisui 百岁
 * @create: 2021-04-07 15:30
 **/
public class DataXHdfsWriter extends DataxWriter implements KeyedPluginStore.IPluginKeyAware {
    private static final String DATAX_NAME = "Hdfs";

    @FormField(ordinal = 3, type = FormFieldType.SELECTABLE, validate = {Validator.require})
    public String fsName;
    @FormField(ordinal = 4, type = FormFieldType.ENUM, validate = {Validator.require})
    public String fileType;
    //    @FormField(ordinal = 5, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
//    public String path;
//    @FormField(ordinal = 6, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
//    public String fileName;
    //    @FormField(ordinal = 4, type = FormFieldType.TEXTAREA, validate = {Validator.require})
//    public String column;
    @FormField(ordinal = 7, type = FormFieldType.ENUM, validate = {Validator.require})
    public String writeMode;
    @FormField(ordinal = 8, type = FormFieldType.INPUTTEXT, validate = {Validator.require})
    public String fieldDelimiter;
    @FormField(ordinal = 9, type = FormFieldType.ENUM, validate = {})
    public String compress;
    @FormField(ordinal = 10, type = FormFieldType.TEXTAREA, validate = {})
//    public String hadoopConfig;
//    @FormField(ordinal = 11, type = FormFieldType.ENUM, validate = {})
    public String encoding;
//    @FormField(ordinal = 12, type = FormFieldType.INPUTTEXT, validate = {})
//    public String haveKerberos;
//    @FormField(ordinal = 13, type = FormFieldType.INPUTTEXT, validate = {})
//    public String kerberosKeytabFilePath;
//    @FormField(ordinal = 14, type = FormFieldType.INPUTTEXT, validate = {})
//    public String kerberosPrincipal;


    public String dataXName;

    @Override
    public void setKey(KeyedPluginStore.Key key) {
        this.dataXName = key.keyVal;
    }

    @FormField(ordinal = 15, type = FormFieldType.TEXTAREA, validate = {Validator.require})
    public String template;

    public static String getDftTemplate() {
        return IOUtils.loadResourceFromClasspath(DataXHdfsWriter.class, "DataXHiveWriter-tpl.json");
    }

    private FileSystemFactory fileSystem;

    public FileSystemFactory getFs() {
        if (fileSystem == null) {
            this.fileSystem = FileSystemFactory.getFsFactory(fsName);
        }
        Objects.requireNonNull(this.fileSystem, "fileSystem has not be initialized");
        return fileSystem;
    }


    @Override
    public String getTemplate() {
        return this.template;
    }

    private static SupportHiveDataType convert2HiveType(ISelectedTab.DataXReaderColType type) {
        switch (type) {
            case Long:
                return SupportHiveDataType.BIGINT;
            case Double:
                return SupportHiveDataType.DOUBLE;
            case STRING:
                return SupportHiveDataType.STRING;
            case Boolean:
                return SupportHiveDataType.BOOLEAN;
            case Date:
                return SupportHiveDataType.DATE;
            case INT:
                return SupportHiveDataType.INT;
            default:
                throw new IllegalStateException("illeal type:" + type);
        }
    }

    public class HdfsDataXContext implements IDataxContext {

        final IDataxProcessor.TableMap tabMap;
        private final String dataxName;

        public HdfsDataXContext(IDataxProcessor.TableMap tabMap, String dataxName) {
            Objects.requireNonNull(tabMap, "param tabMap can not be null");
            this.tabMap = tabMap;
            this.dataxName = dataxName;
        }

        public String getDataXName() {
            return this.dataxName;
        }

        public final String getTableName() {
            String tabName = this.tabMap.getTo();
            if (StringUtils.isBlank(tabName)) {
                throw new IllegalStateException("tabName of tabMap can not be null ,tabMap:" + tabMap);
            }
            return tabName;
        }

        public List<HiveColumn> getCols() {
            return this.tabMap.getSourceCols().stream().map((c) -> {
                HiveColumn col = new HiveColumn();
                col.setName(c.getName());
                col.setType(convert2HiveType(c.getType()).name());
                return col;
            }).collect(Collectors.toList());
        }


        public String getFileType() {
            return fileType;
        }

        public String getWriteMode() {
            return writeMode;
        }

        public String getFieldDelimiter() {
            return fieldDelimiter;
        }

        public String getCompress() {
            return compress;
        }

        public String getEncoding() {
            return encoding;
        }


        public boolean isContainCompress() {
            return StringUtils.isNotEmpty(compress);
        }

        public boolean isContainEncoding() {
            return StringUtils.isNotEmpty(encoding);
        }
    }

    @Override
    public IDataxContext getSubTask(Optional<IDataxProcessor.TableMap> tableMap) {
        if (!tableMap.isPresent()) {
            throw new IllegalArgumentException("param tableMap shall be present");
        }
        if (StringUtils.isBlank(this.dataXName)) {
            throw new IllegalStateException("param 'dataXName' can not be null");
        }
        HdfsDataXContext dataXContext = new HdfsDataXContext(tableMap.get(), this.dataXName);

        return dataXContext;
        //  throw new RuntimeException("dbName:" + dbName + " relevant DS is empty");
    }


    @TISExtension()
    public static class DefaultDescriptor extends BaseDataxWriterDescriptor {
        public DefaultDescriptor() {
            super();
            this.registerSelectOptions(HiveFlatTableBuilder.KEY_FIELD_NAME_FS_NAME, () -> TIS.getPluginStore(FileSystemFactory.class).getPlugins());
        }


        public boolean validateFsName(IFieldErrorHandler msgHandler, Context context, String fieldName, String value) {
            FileSystemFactory fsFactory = FileSystemFactory.getFsFactory(value);
            if (fsFactory == null) {
                throw new IllegalStateException("can not find FileSystemFactory relevant with:" + value);
            }
            if (!(fsFactory instanceof HdfsFileSystemFactory)) {
                msgHandler.addFieldError(context, fieldName, "必须是HDFS类型的文件系统");
                return false;
            }
            return true;
        }

        @Override
        public boolean isRdbms() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return DATAX_NAME;
        }
    }
}

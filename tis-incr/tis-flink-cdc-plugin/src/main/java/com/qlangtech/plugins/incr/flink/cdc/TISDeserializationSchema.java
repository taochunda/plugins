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

package com.qlangtech.plugins.incr.flink.cdc;

import com.qlangtech.tis.realtime.transfer.DTO;
import com.ververica.cdc.debezium.DebeziumDeserializationSchema;
import io.debezium.data.Envelope;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.util.Collector;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JSON format implementation of {@link DebeziumDeserializationSchema} which deserializes the
 * received {@link SourceRecord} to JSON String.
 *
 * @see com.ververica.cdc.debezium.table.RowDataDebeziumDeserializeSchema
 */
public class TISDeserializationSchema implements DebeziumDeserializationSchema<DTO> {
    private static final Pattern PATTERN_TOPIC = Pattern.compile(".+\\.(.+)\\.(.+)");
    private static final long serialVersionUID = 1L;
    //private static final JsonConverter CONVERTER = new JsonConverter();

    public TISDeserializationSchema() {
        // this(false);
    }

//    public TISDeserializationSchema(boolean includeSchema) {
//        final HashMap<String, Object> configs = new HashMap<>();
//        configs.put(ConverterConfig.TYPE_CONFIG, ConverterType.VALUE.getName());
//        configs.put(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, includeSchema);
//        CONVERTER.configure(configs);
//    }

    @Override
    public void deserialize(SourceRecord record, Collector<DTO> out) throws Exception {
        DTO dto = new DTO();
        Envelope.Operation op = Envelope.operationFor(record);
        Struct value = (Struct) record.value();
        Schema valueSchema = record.valueSchema();
        Matcher topicMatcher = PATTERN_TOPIC.matcher(record.topic());
        if (!topicMatcher.matches()) {
            throw new IllegalStateException("topic is illegal:" + record.topic());
        }
        dto.setDbName(topicMatcher.group(1));
        dto.setTableName(topicMatcher.group(2));

        if (op != Envelope.Operation.CREATE && op != Envelope.Operation.READ) {
            if (op == Envelope.Operation.DELETE) {
                this.extractBeforeRow(dto, value, valueSchema);
                dto.setEventType(DTO.EventType.DELETE);
                // this.validator.validate(delete, RowKind.DELETE);
                out.collect(dto);
            } else {
                this.extractBeforeRow(dto, value, valueSchema);
                // dto.setEventType(RowKind.UPDATE_BEFORE);
                //out.collect(dto);
                this.extractAfterRow(dto, value, valueSchema);
                // TODO: 需要判断这条记录是否要处理
                dto.setEventType(DTO.EventType.UPDATE);
                out.collect(dto);
            }
        } else {
            this.extractAfterRow(dto, value, valueSchema);
//            this.validator.validate(delete, RowKind.INSERT);
            dto.setEventType(DTO.EventType.ADD);
            out.collect(dto);
        }
    }


    private void extractAfterRow(DTO dto, Struct value, Schema valueSchema) {
        Schema afterSchema = valueSchema.field("after").schema();
        Struct after = value.getStruct("after");

        Map<String, Object> afterVals = new HashMap<>();
        for (Field f : afterSchema.fields()) {
            afterVals.put(f.name(), after.get(f.name()));
        }
        dto.setAfter(afterVals);
    }


    private void extractBeforeRow(DTO dto, Struct value, Schema valueSchema) {
        Schema beforeSchema = valueSchema.field("before").schema();
        Struct before = value.getStruct("before");
        Map<String, Object> beforeVals = new HashMap<>();
        for (Field f : beforeSchema.fields()) {
            beforeVals.put(f.name(), before.get(f.name()));
        }
        dto.setBefore(beforeVals);
    }

    @Override
    public TypeInformation<DTO> getProducedType() {

        return TypeInformation.of(DTO.class);

//        cols.add(addCol("waitingorder_id", ISelectedTab.DataXReaderColType.STRING, true));
//        cols.add(addCol("order_id", ISelectedTab.DataXReaderColType.STRING));
//        cols.add(addCol("entity_id", ISelectedTab.DataXReaderColType.STRING));
//        cols.add(addCol("is_valid", ISelectedTab.DataXReaderColType.INT));
//        cols.add(addCol("last_ver", ISelectedTab.DataXReaderColType.INT));

//        String[] fieldNames = new String[]{"waitingorder_id", "order_id", "entity_id", "is_valid", "last_ver"};
//        TypeInformation<?>[] types = new TypeInformation<?>[]{Types.STRING, Types.STRING, Types.STRING,Types.INT,Types.INT};
////
//        // return Types.ROW_NAMED(fieldNames, types);
//        return new DTOTypeInfo(fieldNames, types);
    }
}
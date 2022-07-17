/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.chunjun.connector.rocketmq.source;

import com.dtstack.chunjun.connector.rocketmq.conf.RocketMQConf;
import com.dtstack.chunjun.connector.rocketmq.converter.RocketMQRowConverter;
import com.dtstack.chunjun.connector.rocketmq.source.deserialization.KeyValueDeserializationSchema;
import com.dtstack.chunjun.connector.rocketmq.source.deserialization.RowKeyValueDeserializationSchema;
import com.dtstack.chunjun.converter.AbstractRowConverter;
import com.dtstack.chunjun.table.connector.source.ParallelSourceFunctionProvider;

import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.source.DynamicTableSource;
import org.apache.flink.table.connector.source.ScanTableSource;
import org.apache.flink.table.connector.source.abilities.SupportsReadingMetadata;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.descriptors.DescriptorProperties;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.RowType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Defines the scan table source of RocketMQ. */
public class RocketMQScanTableSource implements ScanTableSource, SupportsReadingMetadata {

    private final DescriptorProperties properties;
    private final TableSchema schema;
    private final RocketMQConf rocketMQConf;
    private AbstractRowConverter converter;
    private List<String> metadataKeys;
    private Integer parallelism;

    public RocketMQScanTableSource(
            DescriptorProperties properties,
            TableSchema schema,
            RocketMQConf rocketMQConf,
            Integer parallelism) {
        this.properties = properties;
        this.schema = schema;
        this.rocketMQConf = rocketMQConf;
        this.parallelism = parallelism;
        this.metadataKeys = Collections.emptyList();
    }

    @Override
    public ChangelogMode getChangelogMode() {
        return ChangelogMode.insertOnly();
    }

    @Override
    public ScanRuntimeProvider getScanRuntimeProvider(ScanContext scanContext) {

        final RowType rowType = (RowType) schema.toRowDataType().getLogicalType();

        String[] fieldNames = schema.getFieldNames();
        converter = new RocketMQRowConverter(rowType, rocketMQConf.getEncoding(), fieldNames);

        return ParallelSourceFunctionProvider.of(
                new com.dtstack.chunjun.connector.rocketmq.source.RocketMQSourceFunction<>(
                        createKeyValueDeserializationSchema(), rocketMQConf),
                isBounded(),
                parallelism);
    }

    @Override
    public Map<String, DataType> listReadableMetadata() {
        return Collections.emptyMap();
    }

    @Override
    public void applyReadableMetadata(List<String> metadataKeys, DataType producedDataType) {
        this.metadataKeys = metadataKeys;
    }

    @Override
    public DynamicTableSource copy() {
        RocketMQScanTableSource tableSource =
                new RocketMQScanTableSource(properties, schema, rocketMQConf, parallelism);
        tableSource.metadataKeys = metadataKeys;
        return tableSource;
    }

    @Override
    public String asSummaryString() {
        return RocketMQScanTableSource.class.getName();
    }

    private boolean isBounded() {
        return rocketMQConf.getEndTimeMs() != Long.MAX_VALUE;
    }

    private KeyValueDeserializationSchema<RowData> createKeyValueDeserializationSchema() {
        return new RowKeyValueDeserializationSchema.Builder()
                .setProperties(properties.asMap())
                .setTableSchema(schema)
                .setConverter(converter)
                .build();
    }
}

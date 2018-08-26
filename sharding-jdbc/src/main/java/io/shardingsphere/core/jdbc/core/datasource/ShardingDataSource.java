/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.jdbc.core.datasource;

import io.shardingsphere.core.api.ConfigMapContext;
import io.shardingsphere.core.constant.ConnectionMode;
import io.shardingsphere.core.constant.properties.ShardingProperties;
import io.shardingsphere.core.constant.properties.ShardingPropertiesConstant;
import io.shardingsphere.core.executor.ShardingExecuteEngine;
import io.shardingsphere.core.jdbc.adapter.AbstractDataSourceAdapter;
import io.shardingsphere.core.jdbc.core.ShardingContext;
import io.shardingsphere.core.jdbc.core.connection.ShardingConnection;
import io.shardingsphere.core.rule.MasterSlaveRule;
import io.shardingsphere.core.rule.ShardingRule;
import lombok.Getter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database that support sharding.
 *
 * @author zhangliang
 * @author zhaojun
 * @author panjuan
 */
@Getter
public class ShardingDataSource extends AbstractDataSourceAdapter implements AutoCloseable {
    
    private ShardingContext shardingContext;
    
    private ShardingProperties shardingProperties;
    
    public ShardingDataSource(final Map<String, DataSource> dataSourceMap, final ShardingRule shardingRule) throws SQLException {
        this(dataSourceMap, shardingRule, new ConcurrentHashMap<String, Object>(), new Properties());
    }
    
    public ShardingDataSource(final Map<String, DataSource> dataSourceMap, final ShardingRule shardingRule, final Map<String, Object> configMap, final Properties props) throws SQLException {
        super(dataSourceMap.values());
        if (!configMap.isEmpty()) {
            ConfigMapContext.getInstance().getShardingConfig().putAll(configMap);
        }
        this.shardingProperties = new ShardingProperties(null == props ? new Properties() : props);
        // TODO this.shardingContext = getShardingContext(dataSourceMap, shardingRule);
        this.shardingContext = getShardingContext(getRawDataSourceMap(dataSourceMap), getShardingRuleConfiguration(dataSourceMap, shardingRule));
    }
    
    private ShardingContext getShardingContext(final Map<String, DataSource> dataSourceMap, final ShardingRule shardingRule) {
        boolean showSQL = shardingProperties.getValue(ShardingPropertiesConstant.SQL_SHOW);
        int executorSize = shardingProperties.getValue(ShardingPropertiesConstant.EXECUTOR_SIZE);
        ShardingExecuteEngine executeEngine = new ShardingExecuteEngine(executorSize);
        ConnectionMode connectionMode = ConnectionMode.valueOf(shardingProperties.<String>getValue(ShardingPropertiesConstant.CONNECTION_MODE));
        return new ShardingContext(dataSourceMap, shardingRule, getDatabaseType(), executeEngine, connectionMode, showSQL);
    }
    
    private Map<String, DataSource> getRawDataSourceMap(final Map<String, DataSource> dataSourceMap) {
        Map<String, DataSource> result = new LinkedHashMap<>();
        if (null == dataSourceMap) {
            return result;
        }
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            String dataSourceName = entry.getKey();
            DataSource dataSource = entry.getValue();
            if (dataSource instanceof MasterSlaveDataSource) {
                result.putAll(((MasterSlaveDataSource) dataSource).getAllDataSources());
            } else {
                result.put(dataSourceName, dataSource);
            }
        }
        return result;
    }
    
    private ShardingRule getShardingRuleConfiguration(final Map<String, DataSource> dataSourceMap, final ShardingRule shardingRule) {
        if (null == dataSourceMap || !shardingRule.getMasterSlaveRules().isEmpty()) {
            return shardingRule;
        }
        for (DataSource each : dataSourceMap.values()) {
            if (!(each instanceof MasterSlaveDataSource)) {
                continue;
            }
            MasterSlaveRule masterSlaveRule = ((MasterSlaveDataSource) each).getMasterSlaveRule();
            shardingRule.getMasterSlaveRules().add(masterSlaveRule);
        }
        return shardingRule;
    }
    
    /**
     * Renew sharding data source.
     *
     * @param dataSourceMap data source map
     * @param shardingRule sharding rule
     * @param shardingProperties sharding properties
     */
    public void renew(final Map<String, DataSource> dataSourceMap,
                      final ShardingRule shardingRule, final ShardingProperties shardingProperties) {
        super.renew(dataSourceMap.values());
        this.shardingProperties = shardingProperties;
        int newExecutorSize = shardingProperties.getValue(ShardingPropertiesConstant.EXECUTOR_SIZE);
        boolean newShowSQL = shardingProperties.getValue(ShardingPropertiesConstant.SQL_SHOW);
        ShardingExecuteEngine newExecuteEngine = new ShardingExecuteEngine(newExecutorSize);
        ConnectionMode newConnectionMode = ConnectionMode.valueOf(shardingProperties.<String>getValue(ShardingPropertiesConstant.CONNECTION_MODE));
        shardingContext.renew(dataSourceMap, shardingRule, getDatabaseType(), newExecuteEngine, newConnectionMode, newShowSQL);
    }
    
    /**
     * Renew disable dataSource names.
     *
     * @param disabledDataSourceNames disabled data source names
     */
    public void renewDisabledDataSourceNames(final Collection<String> disabledDataSourceNames) {
        shardingContext.renewDisabledDataSourceNames(disabledDataSourceNames);
    }
    
    /**
     * Renew circuit breaker dataSource names.
     *
     * @param isCircuitBreak is circuit break or not
     */
    public void renewCircuitBreakerDataSourceNames(final boolean isCircuitBreak) {
        shardingContext.renewCircuitBreakerDataSourceNames(isCircuitBreak);
    }
    
    @Override
    public final ShardingConnection getConnection() {
        return new ShardingConnection(shardingContext);
    }
    
    @Override
    public void close() {
        shardingContext.close();
    }
}

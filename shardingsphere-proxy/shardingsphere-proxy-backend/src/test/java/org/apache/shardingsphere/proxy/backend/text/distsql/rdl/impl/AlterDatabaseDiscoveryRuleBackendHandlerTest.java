/*
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

package org.apache.shardingsphere.proxy.backend.text.distsql.rdl.impl;

import com.google.common.collect.Maps;
import org.apache.shardingsphere.dbdiscovery.api.config.DatabaseDiscoveryRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.api.config.rule.DatabaseDiscoveryDataSourceRuleConfiguration;
import org.apache.shardingsphere.dbdiscovery.distsql.parser.segment.DatabaseDiscoveryRuleSegment;
import org.apache.shardingsphere.dbdiscovery.distsql.parser.statement.AlterDatabaseDiscoveryRuleStatement;
import org.apache.shardingsphere.dbdiscovery.spi.DatabaseDiscoveryType;
import org.apache.shardingsphere.infra.context.metadata.MetaDataContexts;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.resource.ShardingSphereResource;
import org.apache.shardingsphere.infra.metadata.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.infra.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.proxy.backend.communication.jdbc.connection.BackendConnection;
import org.apache.shardingsphere.proxy.backend.context.ProxyContext;
import org.apache.shardingsphere.proxy.backend.exception.DatabaseDiscoveryRuleNotExistedException;
import org.apache.shardingsphere.proxy.backend.exception.InvalidDatabaseDiscoveryTypesException;
import org.apache.shardingsphere.proxy.backend.exception.ResourceNotExistedException;
import org.apache.shardingsphere.proxy.backend.response.header.ResponseHeader;
import org.apache.shardingsphere.proxy.backend.response.header.update.UpdateResponseHeader;
import org.apache.shardingsphere.transaction.context.TransactionContexts;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public final class AlterDatabaseDiscoveryRuleBackendHandlerTest {
    
    @Mock
    private BackendConnection backendConnection;
    
    @Mock
    private AlterDatabaseDiscoveryRuleStatement sqlStatement;
    
    @Mock
    private MetaDataContexts metaDataContexts;
    
    @Mock
    private TransactionContexts transactionContexts;
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ShardingSphereMetaData shardingSphereMetaData;
    
    @Mock
    private ShardingSphereRuleMetaData ruleMetaData;
    
    @Mock
    private ShardingSphereResource shardingSphereResource;
    
    @Mock
    private DatabaseDiscoveryDataSourceRuleConfiguration databaseDiscoveryDataSourceRuleConfiguration;
    
    private final AlterDatabaseDiscoveryRuleBackendHandler handler = new AlterDatabaseDiscoveryRuleBackendHandler(sqlStatement, backendConnection);
    
    @Before
    public void setUp() {
        ShardingSphereServiceLoader.register(DatabaseDiscoveryType.class);
        ProxyContext.getInstance().init(metaDataContexts, transactionContexts);
        when(metaDataContexts.getAllSchemaNames()).thenReturn(Collections.singleton("test"));
        when(metaDataContexts.getMetaData("test")).thenReturn(shardingSphereMetaData);
        when(shardingSphereMetaData.getRuleMetaData()).thenReturn(ruleMetaData);
    }
    
    @Test
    public void assertExecute() {
        DatabaseDiscoveryRuleSegment databaseDiscoveryRuleSegment = new DatabaseDiscoveryRuleSegment("ha_group", Arrays.asList("ds_0", "ds_1"), "TEST", new Properties());
        when(sqlStatement.getRules()).thenReturn(Collections.singleton(databaseDiscoveryRuleSegment));
        when(ruleMetaData.getConfigurations()).thenReturn(
                Collections.singleton(new DatabaseDiscoveryRuleConfiguration(new LinkedList<>(Collections.singleton(databaseDiscoveryDataSourceRuleConfiguration)), Maps.newHashMap())));
        when(databaseDiscoveryDataSourceRuleConfiguration.getName()).thenReturn("ha_group");
        when(shardingSphereMetaData.getResource()).thenReturn(shardingSphereResource);
        ResponseHeader responseHeader = handler.execute("test", sqlStatement);
        assertNotNull(responseHeader);
        assertTrue(responseHeader instanceof UpdateResponseHeader);
    }
    
    @Test(expected = DatabaseDiscoveryRuleNotExistedException.class)
    public void assertExecuteWithNotExistDatabaseDiscoveryRule() {
        when(ruleMetaData.getConfigurations()).thenReturn(Collections.emptyList());
        handler.execute("test", sqlStatement);
    }
    
    @Test(expected = DatabaseDiscoveryRuleNotExistedException.class)
    public void assertExecuteWithNoAlteredDatabaseDiscoveryRule() {
        DatabaseDiscoveryRuleSegment databaseDiscoveryRuleSegment = new DatabaseDiscoveryRuleSegment("ha_group", Arrays.asList("ds_0", "ds_1"), "TEST", new Properties());
        when(sqlStatement.getRules()).thenReturn(Collections.singleton(databaseDiscoveryRuleSegment));
        when(ruleMetaData.getConfigurations()).thenReturn(Collections.singleton(new DatabaseDiscoveryRuleConfiguration(Collections.emptyList(), Collections.emptyMap())));
        handler.execute("test", sqlStatement);
    }
    
    @Test(expected = ResourceNotExistedException.class)
    public void assertExecuteWithNotExistResources() {
        DatabaseDiscoveryRuleSegment databaseDiscoveryRuleSegment = new DatabaseDiscoveryRuleSegment("ha_group", Arrays.asList("ds_0", "ds_1"), "TEST", new Properties());
        when(sqlStatement.getRules()).thenReturn(Collections.singleton(databaseDiscoveryRuleSegment));
        when(ruleMetaData.getConfigurations()).thenReturn(
                Collections.singleton(new DatabaseDiscoveryRuleConfiguration(Collections.singleton(databaseDiscoveryDataSourceRuleConfiguration), Collections.emptyMap())));
        when(databaseDiscoveryDataSourceRuleConfiguration.getName()).thenReturn("ha_group");
        handler.execute("test", sqlStatement);
    }
    
    @Test(expected = InvalidDatabaseDiscoveryTypesException.class)
    public void assertExecuteWithInvalidDiscoveryTypes() {
        DatabaseDiscoveryRuleSegment databaseDiscoveryRuleSegment = new DatabaseDiscoveryRuleSegment("ha_group", Arrays.asList("ds_0", "ds_1"), "notExistType", new Properties());
        when(sqlStatement.getRules()).thenReturn(Collections.singleton(databaseDiscoveryRuleSegment));
        when(ruleMetaData.getConfigurations()).thenReturn(
                Collections.singleton(new DatabaseDiscoveryRuleConfiguration(Collections.singleton(databaseDiscoveryDataSourceRuleConfiguration), Collections.emptyMap())));
        when(databaseDiscoveryDataSourceRuleConfiguration.getName()).thenReturn("ha_group");
        when(shardingSphereMetaData.getResource()).thenReturn(shardingSphereResource);
        handler.execute("test", sqlStatement);
    }
}

/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.core.runtime.component;

import static org.junit.Assert.assertEquals;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.metl.core.model.Component;
import org.jumpmind.metl.core.model.Flow;
import org.jumpmind.metl.core.model.FlowStep;
import org.jumpmind.metl.core.model.Folder;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttribute;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.model.Resource;
import org.jumpmind.metl.core.model.Setting;
import org.jumpmind.metl.core.runtime.EntityData;
import org.jumpmind.metl.core.runtime.ExecutionTrackerNoOp;
import org.jumpmind.metl.core.runtime.Message;
import org.jumpmind.metl.core.runtime.StartupMessage;
import org.jumpmind.metl.core.runtime.resource.Datasource;
import org.jumpmind.metl.core.runtime.resource.IResourceRuntime;
import org.jumpmind.metl.core.runtime.resource.ResourceFactory;
import org.jumpmind.metl.core.utils.DbTestUtils;
import org.jumpmind.metl.core.utils.TestUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class RdbmsReaderTest {

    private static IResourceRuntime resourceRuntime;
    private static IDatabasePlatform platform;
    private static FlowStep readerFlowStep;
    private static FlowStep readerFlowStepMultiQuery;

    @BeforeClass
    public static void setup() throws Exception {
        platform = createPlatformAndTestDatabase();
        readerFlowStep = createReaderFlowStep();
        readerFlowStepMultiQuery = createReaderFlowStepMultiQuery();
        Resource resource = readerFlowStep.getComponent().getResource();
        resourceRuntime = new ResourceFactory().create(resource, null);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testReaderFlowFromStartupMsg() throws Exception {
        RdbmsReader reader = new RdbmsReader();
        reader.start(0, new ComponentContext(null, readerFlowStep, null, new ExecutionTrackerNoOp(), resourceRuntime, null, null));
        Message msg = new StartupMessage();
        SendMessageCallback<ArrayList<EntityData>> msgTarget = new SendMessageCallback<ArrayList<EntityData>>();
        reader.handle( msg, msgTarget, true);

        assertEquals(2, msgTarget.getPayloadList().size());
        ArrayList<EntityData> payload = msgTarget.getPayloadList().get(0);
        assertEquals("test row 1", payload.get(0).get("tt1col2"));
        assertEquals("test row x", payload.get(0).get("tt2coly"));
    }

    @Test
    public void testReaderFlowFromSingleContentMsg() throws Exception {

        RdbmsReader reader = new RdbmsReader();
        reader.start(0, new ComponentContext(null, readerFlowStep, null, new ExecutionTrackerNoOp(), resourceRuntime, null, null));
        Message message = new Message("fake step id");
        ArrayList<EntityData> inboundPayload = new ArrayList<EntityData>();
        inboundPayload.add(new EntityData());
        message.setPayload(inboundPayload);
        
        SendMessageCallback<ArrayList<EntityData>> msgTarget = new SendMessageCallback<ArrayList<EntityData>>();
        reader.handle(message, msgTarget, true);

        assertEquals(2, msgTarget.getPayloadList().size());
        ArrayList<EntityData> payload = msgTarget.getPayloadList().get(0);
        assertEquals("test row 1", payload.get(0).get("tt1col2"));
        assertEquals("test row x", payload.get(0).get("tt2coly"));
        //TODO: can't test these like this anymore.  Need manipulatedFlow and startup message
        // as this is calculated at the runtime level based on incoming messages
//        assertEquals(false, msgTarget.getMessage(0).getHeader().isUnitOfWorkLastMessage());
//        assertEquals(true, msgTarget.getMessage(1).getHeader().isUnitOfWorkLastMessage());
    }

    @Test
    public void testReaderFlowFromMultipleContentMsgs() throws Exception {

        RdbmsReader reader = new RdbmsReader();
        reader.start(0, new ComponentContext(null, readerFlowStepMultiQuery, null, new ExecutionTrackerNoOp(), resourceRuntime, null, null));
        Message message = new Message("fake step id");
        ArrayList<EntityData> inboundPayload = new ArrayList<EntityData>();
        inboundPayload.add(new EntityData());
        message.setPayload(inboundPayload);
        
        SendMessageCallback<ArrayList<EntityData>> msgTarget = new SendMessageCallback<ArrayList<EntityData>>();
        reader.handle(message, msgTarget, true);

        assertEquals(2, msgTarget.getPayloadList().size());
        ArrayList<EntityData> payload = msgTarget.getPayloadList().get(0);
        assertEquals("test row 1", payload.get(0).get("tt1col2"));
        assertEquals("test row x", payload.get(0).get("tt2coly"));
        //TODO: can't test these like this anymore.  Need manipulatedFlow and startup message
        // as this is calculated at the runtime level based on incoming messages
//        assertEquals(false, msgTarget.getMessage(0).getHeader().isUnitOfWorkLastMessage());
//        assertEquals(true, msgTarget.getMessage(1).getHeader().isUnitOfWorkLastMessage());
    }
    
    @Test
    public void testCountColumnSeparatingCommas() {
        
        RdbmsReader reader = new RdbmsReader();
        
        int count = reader.countColumnSeparatingCommas("ISNULL(a,''), b, *");
        assertEquals(count, 2);        
        count = reader.countColumnSeparatingCommas("ISNULL(a,('')), b, *");
        assertEquals(count,2);
    }

    @Test
    public void testGetSqlColumnEntityHints() throws Exception {
        
        RdbmsReader reader = new RdbmsReader();
        String sql = "select\r\n ISNULL(a,ISNULL(z,'')) /*COLA*/, b/*COLB*/, c/*  COLC */ from test;";
        Map<Integer, String> hints = reader.getSqlColumnEntityHints(sql);
        assertEquals(hints.get(1), "COLA");
        assertEquals(hints.get(2), "COLB");
        assertEquals(hints.get(3), "COLC");
        
    }
    
    private static FlowStep createReaderFlowStep() {

        Folder folder = TestUtils.createFolder("Test Folder");
        Flow flow = TestUtils.createFlow("TestFlow", folder);
        Setting[] settingData = createReaderSettings();
        Component componentVersion = TestUtils.createComponent(RdbmsReader.TYPE, false,
                createResource(createResourceSettings()), null, createOutputModel(), null,
                null, settingData);
        FlowStep readerComponent = new FlowStep();
        readerComponent.setFlowId(flow.getId());
        readerComponent.setComponentId(componentVersion.getId());
        readerComponent.setCreateBy("Test");
        readerComponent.setCreateTime(new Date());
        readerComponent.setLastUpdateBy("Test");
        readerComponent.setLastUpdateTime(new Date());
        readerComponent.setComponent(componentVersion);
        return readerComponent;
    }
    
    private static FlowStep createReaderFlowStepMultiQuery() {

        Folder folder = TestUtils.createFolder("Test Folder");
        Flow flow = TestUtils.createFlow("TestFlow", folder);
        Setting[] settingData = createReaderSettingsMultiQuery();
        Component componentVersion = TestUtils.createComponent(RdbmsReader.TYPE, false,
                createResource(createResourceSettings()), null, createOutputModel(), null,
                null, settingData);
        FlowStep readerComponent = new FlowStep();
        readerComponent.setFlowId(flow.getId());
        readerComponent.setComponentId(componentVersion.getId());
        readerComponent.setCreateBy("Test");
        readerComponent.setCreateTime(new Date());
        readerComponent.setLastUpdateBy("Test");
        readerComponent.setLastUpdateTime(new Date());
        readerComponent.setComponent(componentVersion);
        return readerComponent;
    }

    private static Model createOutputModel() {

        ModelEntity tt1 = new ModelEntity("tt1", "TEST_TABLE_1");
        tt1.addModelAttribute(new ModelAttribute("tt1col1", tt1.getId(), "COL1"));
        tt1.addModelAttribute(new ModelAttribute("tt1col2", tt1.getId(), "COL2"));
        tt1.addModelAttribute(new ModelAttribute("tt1col3", tt1.getId(), "COL3"));

        ModelEntity tt2 = new ModelEntity("tt2", "TEST_TABLE_2");
        tt2.addModelAttribute(new ModelAttribute("tt2colx", tt1.getId(), "COLX"));
        tt2.addModelAttribute(new ModelAttribute("tt2coly", tt1.getId(), "COLY"));
        tt2.addModelAttribute(new ModelAttribute("tt2colz", tt1.getId(), "COLZ"));

        Model modelVersion = new Model();
        modelVersion.getModelEntities().add(tt1);
        modelVersion.getModelEntities().add(tt2);

        return modelVersion;
    }
    
    private static Resource createResource(List<Setting> settings) {
        Resource resource = new Resource();
        Folder folder = TestUtils.createFolder("Test Folder Resource");
        resource.setName("Test Resource");
        resource.setFolderId("Test Folder Resource");
        resource.setType(Datasource.TYPE);
        resource.setFolder(folder);
        resource.setSettings(settings);

        return resource;
    }

    private static Setting[] createReaderSettings() {

        Setting[] settingData = new Setting[2];
        settingData[0] = new Setting(RdbmsReader.SQL,
                "select * From test_table_1 tt1 inner join test_table_2 tt2"
                + " on tt1.col1 = tt2.colx order by tt1.col1");
        settingData[1] = new Setting(RdbmsReader.ROWS_PER_MESSAGE, "2");

        return settingData;
    }

    private static Setting[] createReaderSettingsMultiQuery() {

        Setting[] settingData = new Setting[2];
        settingData[0] = new Setting(RdbmsReader.SQL,
                "select * From test_table_1 tt1 inner join test_table_2 tt2"
                + " on tt1.col1 = tt2.colx order by tt1.col1;\n\n"
                + "select * from test_table_2 where colx = 4;");
        settingData[1] = new Setting(RdbmsReader.ROWS_PER_MESSAGE, "2");

        return settingData;
    }

    private static List<Setting> createResourceSettings() {
        List<Setting> settings = new ArrayList<Setting>(4);
        settings.add(new Setting(Datasource.DB_POOL_DRIVER, "org.h2.Driver"));
        settings.add(new Setting(Datasource.DB_POOL_URL, "jdbc:h2:file:build/dbs/testdb"));
        settings.add(new Setting(Datasource.DB_POOL_USER, "jumpmind"));
        settings.add(new Setting(Datasource.DB_POOL_PASSWORD, "jumpmind"));
        return settings;
    }

    private static IDatabasePlatform createPlatformAndTestDatabase() throws Exception {

        platform = DbTestUtils.createDatabasePlatform();
        Database database = createTestDatabase();
        platform.createDatabase(database, true, false);
        populateTestDatabase(platform, database);

        return platform;
    }

    private static Database createTestDatabase() {

        Table testTable1 = createTestTable1();
        Table testTable2 = createTestTable2();
        Database database = new Database();
        database.addTable(testTable1);
        database.addTable(testTable2);
        return database;
    }

    private static Table createTestTable1() {

        Table table = new Table("test_table_1");

        List<Column> columns = new ArrayList<Column>();
        columns.add(new Column("col1", true, Types.INTEGER, 4, 1));
        columns.add(new Column("col2", false, Types.VARCHAR, 50, 50));
        columns.add(new Column("col3", false, Types.DECIMAL, 9, 2));

        table.addColumns(columns);
        return table;
    }
    
    private static Table createTestTable2() {

        Table table = new Table("test_table_2");

        List<Column> columns = new ArrayList<Column>();
        columns.add(new Column("colx", true, Types.INTEGER, 4, 1));
        columns.add(new Column("coly", false, Types.VARCHAR, 50, 50));
        columns.add(new Column("colz", false, Types.DECIMAL, 9, 2));

        table.addColumns(columns);
        return table;
    }

    private static void populateTestDatabase(IDatabasePlatform platform, Database database) {

        ISqlTemplate template = platform.getSqlTemplate();
        DmlStatement statement = platform.createDmlStatement(DmlType.INSERT,
                database.findTable("test_table_1"), null);
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 1, "test row 1", 7.7 }, new Object[] { 1 }));
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 2, "test row 2", 8.8 }, new Object[] { 1 }));
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 3, "test row 3", 9.9 }, new Object[] { 1 }));
        
        statement = platform.createDmlStatement(DmlType.INSERT, database.findTable("test_table_2"),null);
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 1, "test row x", 7.7 }, new Object[] { 1 }));
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 2, "test row y", 8.8 }, new Object[] { 1 }));
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 3, "test row z", 9.9 }, new Object[] { 1 }));
        template.update(statement.getSql(),
                statement.getValueArray(new Object[] { 4, "test row zz", 4.9 }, new Object[] { 1 }));
        
    }

}
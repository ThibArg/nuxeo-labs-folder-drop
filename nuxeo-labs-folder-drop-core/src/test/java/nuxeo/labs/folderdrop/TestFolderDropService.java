/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
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
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package nuxeo.labs.folderdrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.automation.scripting")
@Deploy("org.nuxeo.ecm.platform.types")
@Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core")
public class TestFolderDropService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TREE_JSON = "["
            + "{\"name\":\"folder1\",\"relativePath\":\"folder1\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\"file1.pdf\",\"relativePath\":\"folder1/file1.pdf\",\"isFolder\":false,\"mimeType\":\"application/pdf\",\"size\":12345,\"batchFileIndex\":0},"
            + "{\"name\":\"image.png\",\"relativePath\":\"folder1/image.png\",\"isFolder\":false,\"mimeType\":\"image/png\",\"size\":67890,\"batchFileIndex\":1},"
            + "{\"name\":\"subfolder\",\"relativePath\":\"folder1/subfolder\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\"doc.txt\",\"relativePath\":\"folder1/subfolder/doc.txt\",\"isFolder\":false,\"mimeType\":\"text/plain\",\"size\":100,\"batchFileIndex\":2},"
            + "{\"name\":\"folder2\",\"relativePath\":\"folder2\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0}"
            + "]";

    @Inject
    protected CoreSession session;

    @Inject
    protected FolderDropService service;

    @Inject
    protected AutomationService automationService;

    protected DocumentModel testFolder;

    @Before
    public void init() {
        testFolder = session.createDocumentModel("/", "test", "Folder");
        testFolder = session.createDocument(testFolder);
        session.save();
    }

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(service);
    }

    @Test
    public void testNoCallbackChainByDefault() {
        assertFalse(service.hasCallbackChain());
    }

    @Test
    public void testResolveTypesNoCallback() throws IOException {
        String result = service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
        assertNotNull(result);

        ArrayNode items = (ArrayNode) MAPPER.readTree(result);
        assertEquals(6, items.size());

        // folder1 → "Folder"
        assertEquals("Folder", items.get(0).get("docType").asText());

        // file1.pdf → null (FileManager.Import)
        assertTrue(items.get(1).get("docType").isNull());

        // image.png → null
        assertTrue(items.get(2).get("docType").isNull());

        // subfolder → "Folder"
        assertEquals("Folder", items.get(3).get("docType").asText());

        // doc.txt → null
        assertTrue(items.get(4).get("docType").isNull());

        // folder2 → "Folder"
        assertEquals("Folder", items.get(5).get("docType").asText());
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-chain.xml")
    public void testResolveTypesWithCallback() throws IOException {
        assertTrue(service.hasCallbackChain());
        assertEquals("javascript.testFolderDropCallback", service.getCallbackChain());

        String result = service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
        assertNotNull(result);

        ArrayNode items = (ArrayNode) MAPPER.readTree(result);
        assertEquals(6, items.size());

        // folder1 → "Workspace" (the test chain returns Workspace for folders)
        assertEquals("Workspace", items.get(0).get("docType").asText());

        // file1.pdf → "File" (the test chain returns File for application/*)
        assertEquals("File", items.get(1).get("docType").asText());

        // image.png → "Picture" (the test chain returns Picture for image/*)
        assertEquals("Picture", items.get(2).get("docType").asText());

        // subfolder → "Workspace"
        assertEquals("Workspace", items.get(3).get("docType").asText());

        // doc.txt → "Note" (the test chain returns Note for text/*)
        assertEquals("Note", items.get(4).get("docType").asText());

        // folder2 → "Workspace"
        assertEquals("Workspace", items.get(5).get("docType").asText());
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-chain.xml")
    public void testResolveTypesOperation() throws OperationException, IOException {
        try (OperationContext ctx = new OperationContext(session)) {
            Map<String, Object> params = new HashMap<>();
            params.put("parentPath", testFolder.getPathAsString());
            params.put("treeJson", TREE_JSON);

            Blob result = (Blob) automationService.run(ctx, FolderDropResolveTypesOp.ID, params);
            assertNotNull(result);

            ArrayNode items = (ArrayNode) MAPPER.readTree(result.getString());
            assertEquals(6, items.size());
            assertEquals("Workspace", items.get(0).get("docType").asText());
            assertEquals("Picture", items.get(2).get("docType").asText());
        }
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-partial.xml")
    public void testResolveTypesCallbackReturnsPartial() throws IOException {
        // This chain only sets docType for folders, leaves files with no result → defaults
        String result = service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
        assertNotNull(result);

        ArrayNode items = (ArrayNode) MAPPER.readTree(result);

        // folder1 → "OrderedFolder" (set by chain)
        assertEquals("OrderedFolder", items.get(0).get("docType").asText());

        // file1.pdf → default "Folder" fallback won't apply; isFolder=false → null
        assertTrue(items.get(1).get("docType").isNull());

        // subfolder → "OrderedFolder"
        assertEquals("OrderedFolder", items.get(3).get("docType").asText());
    }
}

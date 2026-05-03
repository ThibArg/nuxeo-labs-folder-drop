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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Implementation of the {@link FolderDropService}.
 * <p>
 * Registers an extension point "configuration" that accepts a {@link FolderDropDescriptor}
 * with an optional callbackChain.
 *
 * @since 2025.1
 */
public class FolderDropServiceImpl extends DefaultComponent implements FolderDropService {

    private static final Logger log = LogManager.getLogger(FolderDropServiceImpl.class);

    public static final String EXT_POINT = "configuration";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected FolderDropDescriptor descriptor;

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (EXT_POINT.equals(extensionPoint)) {
            descriptor = (FolderDropDescriptor) contribution;
        }
    }

    @Override
    public boolean hasCallbackChain() {
        return descriptor != null && StringUtils.isNotBlank(descriptor.getCallbackChain());
    }

    @Override
    public String getCallbackChain() {
        return descriptor != null ? descriptor.getCallbackChain() : null;
    }

    @Override
    public String resolveTypes(CoreSession session, String treeJson, String parentPath) {
        try {
            ArrayNode items = (ArrayNode) OBJECT_MAPPER.readTree(treeJson);

            if (!hasCallbackChain()) {
                return resolveDefaults(items);
            }

            return resolveWithCallback(session, items, parentPath);
        } catch (IOException e) {
            throw new NuxeoException("Failed to parse tree JSON", e);
        }
    }

    /**
     * Default resolution: folders → "Folder", files → null (FileManager.Import).
     */
    protected String resolveDefaults(ArrayNode items) {
        for (JsonNode item : items) {
            ObjectNode obj = (ObjectNode) item;
            boolean isFolder = obj.path("isFolder").asBoolean(false);
            if (isFolder) {
                obj.put("docType", "Folder");
            } else {
                obj.putNull("docType");
            }
        }
        return items.toString();
    }

    /**
     * Resolve types by calling the configured automation chain for each item.
     * The chain receives the parent document as input and item properties as parameters.
     * It must set the context variable {@link #CALLBACK_RESULT_CTX_VAR} to a JSON string
     * like {@code {"docType": "MyType"}}.
     */
    protected String resolveWithCallback(CoreSession session, ArrayNode items, String parentPath) {
        String chainId = descriptor.getCallbackChain();
        AutomationService automationService = Framework.getService(AutomationService.class);
        DocumentModel parentDoc = session.getDocument(new PathRef(parentPath));

        for (JsonNode item : items) {
            ObjectNode obj = (ObjectNode) item;
            String name = obj.path("name").asText("");
            boolean isFolder = obj.path("isFolder").asBoolean(false);
            String mimeType = obj.path("mimeType").asText("");
            long size = obj.path("size").asLong(0);
            String relativePath = obj.path("relativePath").asText("");

            try (OperationContext ctx = new OperationContext(session)) {
                ctx.setInput(parentDoc);
                Map<String, Object> params = new HashMap<>();
                params.put(PARAM_NAME, name);
                params.put(PARAM_IS_FOLDER, isFolder);
                params.put(PARAM_MIME_TYPE, mimeType);
                params.put(PARAM_SIZE, size);
                params.put(PARAM_RELATIVE_PATH, relativePath);

                automationService.run(ctx, chainId, params);

                String resultStr = (String) ctx.get(CALLBACK_RESULT_CTX_VAR);
                if (StringUtils.isNotBlank(resultStr)) {
                    JsonNode resultJson = OBJECT_MAPPER.readTree(resultStr);
                    JsonNode docTypeNode = resultJson.get("docType");
                    if (docTypeNode != null && !docTypeNode.isNull()) {
                        obj.put("docType", docTypeNode.asText());
                    } else {
                        setDefaultDocType(obj, isFolder);
                    }
                } else {
                    setDefaultDocType(obj, isFolder);
                }
            } catch (OperationException | IOException e) {
                log.warn("Callback chain '{}' failed for item '{}', using default", chainId, name, e);
                setDefaultDocType(obj, isFolder);
            }
        }

        return items.toString();
    }

    private void setDefaultDocType(ObjectNode obj, boolean isFolder) {
        if (isFolder) {
            obj.put("docType", "Folder");
        } else {
            obj.putNull("docType");
        }
    }
}

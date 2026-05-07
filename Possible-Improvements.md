# Possible Improvements

Ideas and strategies for future versions of `nuxeo-labs-folder-drop`.

## Transaction / Rollback on Error

Currently, each document (folder or file) is created via a separate HTTP request with its own server-side transaction. If an error occurs mid-import (e.g., item #21 out of 50 fails), items 1-20 remain in Nuxeo and items 21-50 are not created, resulting in a partial import.

### Rollback Strategy

During import, track the UUIDs of top-level created items (direct children of the target document). If any creation fails, delete all top-level items in a single call — Nuxeo cascades deletion to all children automatically.

The cleanup can be done with a single NXQL query chained to `Document.Delete`:

```
SELECT * FROM Document WHERE ecm:uuid IN ('uuid1', 'uuid2', 'uuid3')
```

### Implementation Options

1. **Client-side rollback**: The browser calls the query + delete after detecting a failure. Simple to implement, but risks timeout if deletion of a large tree takes time.
2. **Server-side rollback**: A dedicated server-side operation receives the list of UUIDs and performs the cleanup in a single transaction. No progress feedback to the user, but more reliable.

## Collapsible Tree Preview

Replace the flat tree preview with an interactive collapsible/expandable tree. Folders would start collapsed and users could click to expand them. This would improve readability for large folder structures.

The current `dom-repeat` + flat array approach is already structured to support this — adding `isExpanded` / `visible` flags and a click handler on folder rows would be the main changes.

## Duplicate Handling Strategies

Currently, when dropped items have the same title as existing children of the target document, the plugin shows a warning but always creates new documents (the "Create" strategy). Future versions could offer additional strategies as a user-facing choice in the dialog (similar to Nuxeo Drive's Direct Transfer):

- **Create** (current) — always create new documents, even if same title exists
- **Reject** — refuse the import if any top-level item already exists
- **Merge** — reuse existing folders by title match, create only missing subfolders, import files into existing or new folders
- **Replace** — delete the existing item and its children, then create the new one (destructive — requires careful UX)

## Enrich `folderDropImportDone` Event with Created Document UUIDs

The `folderDropImportDone` event currently provides counts and the parent document ID. A future version could enrich the event context with the UUIDs of all created documents, allowing listeners to act on the exact documents that were imported (e.g., start a workflow, apply metadata, trigger processing).

This would require the client to collect all document UUIDs during creation (some are already tracked in the `pathToId` map for folders) and pass them back to the `FolderDrop.NotifyDone` operation as an additional parameter.

## Shared Dialog Element

The two upload elements (`nuxeo-labs-folder-drop` and `nuxeo-labs-folder-drop-s3`) duplicate ~60 lines of identical dialog HTML (drop zone, tree preview, warnings, progress phases, messages, buttons). A shared `nuxeo-labs-folder-drop-dialog` element could own this markup and all associated properties, with each outer element reduced to just the action button and its `_uploadFiles()` implementation. This would eliminate the risk of changing the dialog UI in one element and forgetting the other.

However, this adds significant complexity: property forwarding or event wiring between parent and child elements, Polymer 2 slot/binding constraints, and harder debugging. Given the duplication is purely declarative HTML with no logic, the trade-off may not be worthwhile unless a third upload variant is added.

## Validation Callback Chain (Whole-Tree)

The existing per-item callback chain (`callbackChain`) resolves document types one item at a time, with no visibility into the overall tree. A second, optional callback chain could run **after** type resolution but **before** the result is returned to the front-end, receiving the entire resolved tree as input.

**Purpose:** Apply business rules that require whole-tree context — e.g., reject imports exceeding a file count or total size, enforce naming conventions across the tree, require certain file types to be present, or validate folder depth.

**Server-side contract:**
- **Input**: the parent document (DocumentModel)
- **Parameters**: `treeJson` (String) — the full resolved tree JSON (with `docType` already populated)
- **On accept**: return the tree unchanged (or don't set any result variable)
- **On reject**: set context variable `FolderDrop_ValidationResult` = JSON string `{"rejected": true, "message": "Reason for rejection"}`

**Implementation:**
- New optional configuration element in the extension point: `<validationChain>myValidationChainId</validationChain>`
- Called in `FolderDropServiceImpl.resolveTypes()`, after `resolveDefaults()` or `resolveWithCallback()`, before returning
- If rejected, the operation returns the rejection message instead of the resolved tree
- Front-end change: `_resolveTypes` checks for a rejection response and displays the message as an error, aborting the import

**Trade-offs:** Adds a new extension point and contract to document/maintain. The per-item callback chain could partially cover some validation cases (by returning an error-like docType), but cannot handle cross-item rules. Worth implementing only if whole-tree validation is a real requirement.

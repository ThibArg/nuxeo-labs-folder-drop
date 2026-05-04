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

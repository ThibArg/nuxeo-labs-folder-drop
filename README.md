# nuxeo-labs-folder-drop

## TL;DR

Drag-and-drop folder import for Nuxeo Web UI (LTS 2025). Drop folders from your desktop onto any Folderish document to create the full hierarchy in Nuxeo. Also provides: S3 direct upload, configurable file filtering, custom document type resolution via callback chain, and a server-side event on completion.

> [!NOTE]
> This is Work In Progress - Not ready for use

> [!IMPORTANT]
> This plugin is not for mass import. See [Scope and Use Cases](#scope-and-use-cases).

## Description

A Nuxeo LTS 2025 plugin that adds **drag-and-drop folder import** to [Nuxeo Web UI](https://doc.nuxeo.com/nxdoc/web-ui/), preserving the folder hierarchy. Users can drop one or more folders from their desktop (Mac, Windows) and the plugin creates the corresponding Nuxeo documents — folders and files — mirroring the original structure.

The plugin adds a **"Drop Folder..."** button on every Folderish document in Nuxeo Web UI (Workspace, Folder, etc.). Clicking it opens a dialog where folders can be dragged and dropped from the desktop.

## Usage

### 1. Open the Drop Folder Dialog

Navigate to any Folderish document (Workspace, Folder, etc.) and click the **folder icon** button in the document actions toolbar.

<!-- ![Drop Folder Button](README-Images/01-button.png) -->

### 2. Drop Folders

Drag one or more folders from your desktop into the drop zone. The plugin reads the full tree recursively and displays:

- A **tree preview** showing the folder/file hierarchy
- A **summary** with the number of folders, files, and total size (e.g., "3 folder(s), 12 file(s), 45.2 MB")

<!-- ![Tree Preview](README-Images/02-tree-preview.png) -->

### 3. Upload

Click **Upload** to start the import. The dialog shows progress through each phase:

1. **Uploading files** — Files are uploaded to a batch on the server, with a progress bar showing the count (e.g., "Uploading files... (5/12)"). The name and size of the file currently being uploaded are displayed below the progress bar. For large files (> 10 MB), an additional animated progress bar indicates the upload is still in progress.
2. **Resolving document types** — If a callback chain is configured (see [Callback Chain](#callback-chain) below), an indeterminate progress bar is shown while the server resolves document types
3. **Creating documents** — Folders and files are created in Nuxeo, with a combined progress bar (e.g., "Creating documents... (8/15)")

Each completed phase shows a checkmark.

<!-- ![Upload Progress](README-Images/03-progress.png) -->

### 4. Done

Once the import completes, a success message is displayed. Clicking **Close** refreshes the current view to show the newly imported documents.

### S3 Direct Upload

When the [Nuxeo S3 Direct Upload](https://doc.nuxeo.com/nxdoc/amazon-s3-direct-upload/) addon is installed and configured (`s3.useDirectUpload=true`), the plugin automatically detects it and switches to an optimized upload mode:

- **Parallel uploads** — Up to 5 files upload simultaneously directly to S3, bypassing the Nuxeo server for the actual data transfer

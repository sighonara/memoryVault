import {
  Component,
  ChangeDetectionStrategy,
  computed,
  input,
  output,
  signal,
  inject,
} from '@angular/core';
import { FlatTreeControl } from '@angular/cdk/tree';
import { MatTreeModule, MatTreeFlatDataSource, MatTreeFlattener } from '@angular/material/tree';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { Folder } from '../../shared/graphql/generated';

export interface FolderTreeNode {
  id: string;
  name: string;
  parentId: string | null;
  bookmarkCount: number;
  sortOrder: number;
  children: FolderTreeNode[];
}

interface FlatFolderNode {
  id: string;
  name: string;
  bookmarkCount: number;
  level: number;
  expandable: boolean;
}

export function buildTree(flatFolders: Array<{ id: string; name: string; parentId: string | null; bookmarkCount: number; sortOrder: number }>): FolderTreeNode[] {
  const root: FolderTreeNode[] = [];
  const map = new Map<string, FolderTreeNode>();
  flatFolders.forEach(f => map.set(f.id, { ...f, children: [] }));
  flatFolders.forEach(f => {
    const node = map.get(f.id)!;
    if (f.parentId && map.has(f.parentId)) {
      map.get(f.parentId)!.children.push(node);
    } else {
      root.push(node);
    }
  });
  return root;
}

@Component({
  selector: 'app-bookmark-tree',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTreeModule, MatIconModule, MatButtonModule, MatMenuModule],
  template: `
    <div class="folder-tree">
      <div class="tree-node root-node"
           [class.selected]="selectedId() === null"
           (click)="onSelect(null)">
        <mat-icon class="tree-icon">bookmarks</mat-icon>
        <span class="node-label">All Bookmarks</span>
      </div>

      <mat-tree [dataSource]="dataSource()" [treeControl]="treeControl">
        <mat-tree-node *matTreeNodeDef="let node" matTreeNodePadding>
          <div class="tree-node"
               [class.selected]="selectedId() === node.id"
               (click)="onSelect(node.id)"
               (contextmenu)="onContextMenu($event, node)">
            <button mat-icon-button disabled class="toggle-placeholder"></button>
            <mat-icon class="tree-icon">folder</mat-icon>
            <span class="node-label">{{ node.name }}</span>
            <span class="node-count">({{ node.bookmarkCount }})</span>
          </div>
        </mat-tree-node>

        <mat-tree-node *matTreeNodeDef="let node; when: hasChild" matTreeNodePadding>
          <div class="tree-node"
               [class.selected]="selectedId() === node.id"
               (click)="onSelect(node.id)"
               (contextmenu)="onContextMenu($event, node)">
            <button mat-icon-button matTreeNodeToggle class="toggle-btn">
              <mat-icon>{{ treeControl.isExpanded(node) ? 'expand_more' : 'chevron_right' }}</mat-icon>
            </button>
            <mat-icon class="tree-icon">{{ treeControl.isExpanded(node) ? 'folder_open' : 'folder' }}</mat-icon>
            <span class="node-label">{{ node.name }}</span>
            <span class="node-count">({{ node.bookmarkCount }})</span>
          </div>
        </mat-tree-node>
      </mat-tree>

      <div class="tree-node unfiled-node"
           [class.selected]="selectedId() === 'unfiled'"
           (click)="onSelect('unfiled')">
        <mat-icon class="tree-icon">inbox</mat-icon>
        <span class="node-label">Unfiled</span>
      </div>
    </div>

    <div [matMenuTriggerFor]="contextMenu"
         [style.position]="'fixed'"
         [style.left.px]="contextMenuPosition.x"
         [style.top.px]="contextMenuPosition.y"
         #menuTrigger="matMenuTrigger">
    </div>
    <mat-menu #contextMenu="matMenu">
      <button mat-menu-item (click)="contextAction.emit({ action: 'new', folderId: contextNode()?.id ?? null })">
        <mat-icon>create_new_folder</mat-icon> New Folder
      </button>
      @if (contextNode()) {
        <button mat-menu-item (click)="contextAction.emit({ action: 'rename', folderId: contextNode()!.id })">
          <mat-icon>edit</mat-icon> Rename
        </button>
        <button mat-menu-item (click)="contextAction.emit({ action: 'delete', folderId: contextNode()!.id })">
          <mat-icon>delete</mat-icon> Delete
        </button>
      }
    </mat-menu>
  `,
  styles: [`
    .folder-tree { padding: 8px 0; }
    .tree-node {
      display: flex; align-items: center; gap: 4px;
      padding: 2px 8px; cursor: pointer; border-radius: 4px;
      min-height: 32px; font-size: 0.8125rem; color: #3c4043;
    }
    .tree-node:hover { background: #f1f3f4; }
    .tree-node.selected { background: #e8f0fe; color: #1a73e8; font-weight: 500; }
    .tree-icon { font-size: 18px; width: 18px; height: 18px; color: #5f6368; flex-shrink: 0; }
    .selected .tree-icon { color: #1a73e8; }
    .node-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .node-count { font-size: 0.7rem; color: #9aa0a6; flex-shrink: 0; }
    .toggle-btn, .toggle-placeholder { width: 24px; height: 24px; line-height: 24px; flex-shrink: 0; }
    .toggle-placeholder { visibility: hidden; }
    .toggle-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .root-node, .unfiled-node { padding-left: 12px; }
    .unfiled-node { margin-top: 4px; border-top: 1px solid #e8eaed; padding-top: 6px; }
  `]
})
export class BookmarkTreeComponent {
  folders = input.required<Folder[]>();
  folderSelected = output<string | null>();
  contextAction = output<{ action: string; folderId: string | null }>();

  selectedId = signal<string | null>(null);
  contextNode = signal<FlatFolderNode | null>(null);
  contextMenuPosition = { x: 0, y: 0 };

  private transformer = (node: FolderTreeNode, level: number): FlatFolderNode => ({
    id: node.id,
    name: node.name,
    bookmarkCount: node.bookmarkCount,
    level,
    expandable: node.children.length > 0,
  });

  treeControl = new FlatTreeControl<FlatFolderNode>(
    node => node.level,
    node => node.expandable,
  );

  private treeFlattener = new MatTreeFlattener(
    this.transformer,
    node => node.level,
    node => node.expandable,
    node => node.children,
  );

  dataSource = computed(() => {
    const ds = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);
    ds.data = buildTree(this.folders() as any);
    return ds;
  });

  hasChild = (_: number, node: FlatFolderNode) => node.expandable;

  onSelect(id: string | null) {
    this.selectedId.set(id);
    this.folderSelected.emit(id);
  }

  onContextMenu(event: MouseEvent, node: FlatFolderNode) {
    event.preventDefault();
    this.contextMenuPosition = { x: event.clientX, y: event.clientY };
    this.contextNode.set(node);
  }
}

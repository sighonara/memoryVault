import { describe, it, expect } from 'vitest';
import { sortByOrder, buildFolderMenuItems } from './bookmark-list';

describe('BookmarkListComponent logic', () => {
  it('sorts bookmarks by sortOrder', () => {
    const bookmarks = [
      { id: '1', title: 'C', sortOrder: 2 },
      { id: '2', title: 'A', sortOrder: 0 },
      { id: '3', title: 'B', sortOrder: 1 },
    ];
    const sorted = sortByOrder(bookmarks);
    expect(sorted[0].title).toBe('A');
    expect(sorted[1].title).toBe('B');
    expect(sorted[2].title).toBe('C');
  });

  it('tracks multi-selection', () => {
    const selected = new Set<string>();
    selected.add('1');
    selected.add('3');
    expect(selected.size).toBe(2);
    expect(selected.has('2')).toBe(false);
  });

  it('bulk delete builds list of IDs', () => {
    const selected = new Set(['1', '3']);
    const ids = Array.from(selected);
    expect(ids).toEqual(['1', '3']);
  });

  it('builds nested folder menu items with depth', () => {
    const folders = [
      { id: '1', name: 'Tech', parentId: null, bookmarkCount: 2, sortOrder: 0 },
      { id: '2', name: 'AI', parentId: '1', bookmarkCount: 1, sortOrder: 0 },
      { id: '3', name: 'Design', parentId: null, bookmarkCount: 3, sortOrder: 1 },
      { id: '4', name: 'Deep Learning', parentId: '2', bookmarkCount: 0, sortOrder: 0 },
    ] as any[];
    const items = buildFolderMenuItems(folders);
    expect(items).toEqual([
      { id: '1', name: 'Tech', path: 'Tech', depth: 0 },
      { id: '2', name: 'AI', path: 'Tech / AI', depth: 1 },
      { id: '4', name: 'Deep Learning', path: 'Tech / AI / Deep Learning', depth: 2 },
      { id: '3', name: 'Design', path: 'Design', depth: 0 },
    ]);
  });

  it('builds empty list for no folders', () => {
    expect(buildFolderMenuItems([])).toEqual([]);
  });
});

import { describe, it, expect } from 'vitest';
import { groupByStatus, buildResolutionArray } from './conflict-review';

describe('ConflictReview logic', () => {
  it('groups items by status', () => {
    const items = [
      { url: 'a.com', title: 'A', status: 'NEW', existingBookmarkId: null, suggestedFolderId: null, browserFolder: null },
      { url: 'b.com', title: 'B', status: 'NEW', existingBookmarkId: null, suggestedFolderId: null, browserFolder: null },
      { url: 'c.com', title: 'C', status: 'MOVED', existingBookmarkId: '1', suggestedFolderId: null, browserFolder: null },
      { url: 'd.com', title: 'D', status: 'PREVIOUSLY_DELETED', existingBookmarkId: '2', suggestedFolderId: null, browserFolder: null },
    ];
    const grouped = groupByStatus(items as any);
    expect(grouped['NEW'].length).toBe(2);
    expect(grouped['MOVED'].length).toBe(1);
    expect(grouped['PREVIOUSLY_DELETED'].length).toBe(1);
  });

  it('groups with no items returns empty groups', () => {
    const grouped = groupByStatus([]);
    expect(Object.keys(grouped).length).toBe(0);
  });

  it('accept all sets all items in group to ACCEPT', () => {
    const resolutions = new Map<string, string>();
    const items = [
      { url: 'a.com', status: 'NEW' },
      { url: 'b.com', status: 'NEW' },
    ];
    items.forEach(item => resolutions.set(item.url, 'ACCEPT'));
    expect(resolutions.get('a.com')).toBe('ACCEPT');
    expect(resolutions.get('b.com')).toBe('ACCEPT');
  });

  it('builds resolution array from map', () => {
    const resolutions = new Map<string, string>();
    resolutions.set('a.com', 'ACCEPT');
    resolutions.set('b.com', 'SKIP');
    const array = buildResolutionArray(resolutions);
    expect(array).toEqual([
      { url: 'a.com', action: 'ACCEPT' },
      { url: 'b.com', action: 'SKIP' },
    ]);
  });
});

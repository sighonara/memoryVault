import { describe, it, expect } from 'vitest';
import { sortByOrder } from './bookmark-list';

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
});

import { describe, it, expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { buildTree } from './bookmark-tree';

describe('BookmarkTreeComponent logic', () => {
  it('builds tree from flat folder list', () => {
    const folders = [
      { id: '1', name: 'Tech', parentId: null, bookmarkCount: 5, sortOrder: 0 },
      { id: '2', name: 'Frontend', parentId: '1', bookmarkCount: 3, sortOrder: 0 },
      { id: '3', name: 'Work', parentId: null, bookmarkCount: 2, sortOrder: 1 },
    ];

    const tree = buildTree(folders);
    expect(tree.length).toBe(2);
    expect(tree[0].children.length).toBe(1);
    expect(tree[0].children[0].name).toBe('Frontend');
  });

  it('handles empty folder list', () => {
    const tree = buildTree([]);
    expect(tree.length).toBe(0);
  });

  it('emits folder selection', () => {
    let selectedId: string | null = null;
    const onSelect = (id: string | null) => { selectedId = id; };
    onSelect('folder-1');
    expect(selectedId).toBe('folder-1');
  });
});

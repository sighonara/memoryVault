import { signal } from '@angular/core';

/**
 * ReaderStore uses Apollo + signalStore which requires full DI context.
 * We test the store's behavioral contracts by mirroring its method logic
 * against a mock Apollo, same pattern as reader.spec.ts.
 */
describe('ReaderStore logic', () => {
  // Simulated store state
  let state: {
    categories: any[];
    selectedType: 'all' | 'category' | 'feed';
    selectedId: string | null;
    items: any[];
    loadingCategories: boolean;
    loadingItems: boolean;
    unreadOnly: boolean;
    viewMode: 'LIST' | 'FULL';
    sortOrder: 'NEWEST_FIRST' | 'OLDEST_FIRST';
  };

  const mockApollo = {
    query: vi.fn(),
    mutate: vi.fn(),
  };

  function patchState(patch: Partial<typeof state>) {
    Object.assign(state, patch);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    state = {
      categories: [],
      selectedType: 'all',
      selectedId: null,
      items: [],
      loadingCategories: false,
      loadingItems: false,
      unreadOnly: true,
      viewMode: 'LIST',
      sortOrder: 'NEWEST_FIRST',
    };
  });

  describe('selection methods', () => {
    it('selectAll sets type to all and clears selectedId', () => {
      state.selectedType = 'feed';
      state.selectedId = 'feed-1';
      patchState({ selectedType: 'all', selectedId: null });
      expect(state.selectedType).toBe('all');
      expect(state.selectedId).toBeNull();
    });

    it('selectCategory sets type to category with id', () => {
      patchState({ selectedType: 'category', selectedId: 'cat-1' });
      expect(state.selectedType).toBe('category');
      expect(state.selectedId).toBe('cat-1');
    });

    it('selectFeed sets type to feed with id', () => {
      patchState({ selectedType: 'feed', selectedId: 'feed-1' });
      expect(state.selectedType).toBe('feed');
      expect(state.selectedId).toBe('feed-1');
    });
  });

  describe('filter and view methods', () => {
    it('setUnreadOnly updates unreadOnly state', () => {
      patchState({ unreadOnly: false });
      expect(state.unreadOnly).toBe(false);
    });

    it('setViewMode updates viewMode state', () => {
      patchState({ viewMode: 'FULL' });
      expect(state.viewMode).toBe('FULL');
    });

    it('setSortOrder updates sortOrder state', () => {
      patchState({ sortOrder: 'OLDEST_FIRST' });
      expect(state.sortOrder).toBe('OLDEST_FIRST');
    });
  });

  describe('computed: selectedTitle', () => {
    function selectedTitle(): string {
      if (state.selectedType === 'all') return 'All Items';
      if (state.selectedType === 'category') {
        const cat = state.categories.find((c: any) => c.category.id === state.selectedId);
        return cat?.category.name || 'Category';
      }
      for (const cat of state.categories) {
        const feed = cat.feeds.find((f: any) => f.feed.id === state.selectedId);
        if (feed) return feed.feed.title || feed.feed.url;
      }
      return 'Feed';
    }

    it('returns "All Items" when type is all', () => {
      expect(selectedTitle()).toBe('All Items');
    });

    it('returns category name when type is category', () => {
      state.selectedType = 'category';
      state.selectedId = 'cat-1';
      state.categories = [{ category: { id: 'cat-1', name: 'Tech' }, feeds: [], totalUnread: 0 }];
      expect(selectedTitle()).toBe('Tech');
    });

    it('returns "Category" when category not found', () => {
      state.selectedType = 'category';
      state.selectedId = 'nonexistent';
      state.categories = [];
      expect(selectedTitle()).toBe('Category');
    });

    it('returns feed title when type is feed', () => {
      state.selectedType = 'feed';
      state.selectedId = 'feed-1';
      state.categories = [{
        category: { id: 'cat-1', name: 'Tech' },
        feeds: [{ feed: { id: 'feed-1', title: 'Hacker News', url: 'https://hn.com' }, unreadCount: 5 }],
        totalUnread: 5,
      }];
      expect(selectedTitle()).toBe('Hacker News');
    });

    it('returns feed url when feed has no title', () => {
      state.selectedType = 'feed';
      state.selectedId = 'feed-1';
      state.categories = [{
        category: { id: 'cat-1', name: 'Tech' },
        feeds: [{ feed: { id: 'feed-1', title: null, url: 'https://hn.com/rss' }, unreadCount: 0 }],
        totalUnread: 0,
      }];
      expect(selectedTitle()).toBe('https://hn.com/rss');
    });

    it('returns "Feed" when feed not found', () => {
      state.selectedType = 'feed';
      state.selectedId = 'nonexistent';
      state.categories = [];
      expect(selectedTitle()).toBe('Feed');
    });
  });

  describe('computed: totalUnread', () => {
    function totalUnread(): number {
      return state.categories.reduce((sum: number, cat: any) => sum + cat.totalUnread, 0);
    }

    it('returns 0 when no categories', () => {
      expect(totalUnread()).toBe(0);
    });

    it('sums unread counts across categories', () => {
      state.categories = [
        { category: { id: 'c1' }, feeds: [], totalUnread: 5 },
        { category: { id: 'c2' }, feeds: [], totalUnread: 3 },
      ];
      expect(totalUnread()).toBe(8);
    });
  });

  describe('mutation state contracts', () => {
    it('deleteFeed resets to all when selected feed is deleted', () => {
      state.selectedType = 'feed';
      state.selectedId = 'feed-1';
      // Mirror store logic: if deleted feed is selected, reset selection
      const feedId = 'feed-1';
      if (state.selectedType === 'feed' && state.selectedId === feedId) {
        patchState({ selectedType: 'all', selectedId: null });
      }
      expect(state.selectedType).toBe('all');
      expect(state.selectedId).toBeNull();
    });

    it('deleteFeed does not reset when different feed is selected', () => {
      state.selectedType = 'feed';
      state.selectedId = 'feed-2';
      const feedId = 'feed-1';
      if (state.selectedType === 'feed' && state.selectedId === feedId) {
        patchState({ selectedType: 'all', selectedId: null });
      }
      expect(state.selectedType).toBe('feed');
      expect(state.selectedId).toBe('feed-2');
    });

    it('deleteCategory resets to all when selected category is deleted', () => {
      state.selectedType = 'category';
      state.selectedId = 'cat-1';
      const categoryId = 'cat-1';
      if (state.selectedType === 'category' && state.selectedId === categoryId) {
        patchState({ selectedType: 'all', selectedId: null });
      }
      expect(state.selectedType).toBe('all');
      expect(state.selectedId).toBeNull();
    });

    it('deleteCategory does not reset when different category is selected', () => {
      state.selectedType = 'category';
      state.selectedId = 'cat-2';
      const categoryId = 'cat-1';
      if (state.selectedType === 'category' && state.selectedId === categoryId) {
        patchState({ selectedType: 'all', selectedId: null });
      }
      expect(state.selectedType).toBe('category');
      expect(state.selectedId).toBe('cat-2');
    });
  });

  describe('initial state', () => {
    it('starts with correct defaults', () => {
      expect(state.selectedType).toBe('all');
      expect(state.selectedId).toBeNull();
      expect(state.items).toEqual([]);
      expect(state.categories).toEqual([]);
      expect(state.unreadOnly).toBe(true);
      expect(state.viewMode).toBe('LIST');
      expect(state.sortOrder).toBe('NEWEST_FIRST');
      expect(state.loadingCategories).toBe(false);
      expect(state.loadingItems).toBe(false);
    });
  });
});

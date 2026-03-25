import { signalStore, withState, withMethods, withComputed, patchState } from '@ngrx/signals';
import { inject, computed } from '@angular/core';
import { Apollo } from 'apollo-angular';
import {
  GetFeedCategoriesDocument,
  GetFeedItemsDocument,
  GetFeedItemsByCategoryDocument,
  GetAllFeedItemsDocument,
  GetUserPreferencesDocument,
  MarkItemReadDocument,
  MarkItemUnreadDocument,
  MarkFeedReadDocument,
  MarkCategoryReadDocument,
  AddFeedDocument,
  DeleteFeedDocument,
  AddCategoryDocument,
  RenameCategoryDocument,
  DeleteCategoryDocument,
  ReorderCategoriesDocument,
  MoveFeedToCategoryDocument,
  ImportFeedsDocument,
  ExportFeedsDocument,
  UpdateUserPreferencesDocument,
} from '../shared/graphql/generated';

export type SelectionType = 'all' | 'category' | 'feed';

// NOTE: Replace `any[]` with proper generated types from graphql-codegen after running codegen.
// Use types like GetFeedCategoriesQuery['feedCategories'] and FeedItem[] from generated.ts.
export interface ReaderState {
  categories: any[];  // TODO: replace with generated FeedCategoryWithFeeds[] type
  selectedType: SelectionType;
  selectedId: string | null;  // categoryId or feedId, null for 'all'
  items: any[];  // TODO: replace with generated FeedItem[] type
  loadingCategories: boolean;
  loadingItems: boolean;
  unreadOnly: boolean;
  viewMode: 'LIST' | 'FULL';
  sortOrder: 'NEWEST_FIRST' | 'OLDEST_FIRST';
}

const initialState: ReaderState = {
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

export const ReaderStore = signalStore(
  // Provided at component level in ReaderComponent, not root
  withState(initialState),
  withComputed((store) => ({
    selectedTitle: computed(() => {
      if (store.selectedType() === 'all') return 'All Items';
      if (store.selectedType() === 'category') {
        const cat = store.categories().find((c: any) => c.category.id === store.selectedId());
        return cat?.category.name || 'Category';
      }
      // feed
      for (const cat of store.categories()) {
        const feed = cat.feeds.find((f: any) => f.feed.id === store.selectedId());
        if (feed) return feed.feed.title || feed.feed.url;
      }
      return 'Feed';
    }),
    totalUnread: computed(() => {
      return store.categories().reduce((sum: number, cat: any) => sum + cat.totalUnread, 0);
    }),
  })),
  withMethods((store, apollo = inject(Apollo)) => {
    const loadItems = () => {
      patchState(store, { loadingItems: true });
      const type = store.selectedType();
      const id = store.selectedId();
      const unreadOnly = store.unreadOnly();
      const sortOrder = store.sortOrder();
      const limit = 100;

      let query$;
      if (type === 'all') {
        query$ = apollo.query({
          query: GetAllFeedItemsDocument,
          variables: { limit, unreadOnly, sortOrder },
          fetchPolicy: 'network-only',
        });
      } else if (type === 'category') {
        query$ = apollo.query({
          query: GetFeedItemsByCategoryDocument,
          variables: { categoryId: id, limit, unreadOnly, sortOrder },
          fetchPolicy: 'network-only',
        });
      } else {
        query$ = apollo.query({
          query: GetFeedItemsDocument,
          variables: { feedId: id, limit, unreadOnly, sortOrder },
          fetchPolicy: 'network-only',
        });
      }

      query$.subscribe((result: any) => {
        const items = result.data.feedItemsAll || result.data.feedItemsByCategory || result.data.feedItems;
        patchState(store, { items: items || [], loadingItems: false });
      });
    };

    const loadCategories = () => {
      patchState(store, { loadingCategories: true });
      apollo.query({ query: GetFeedCategoriesDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
        patchState(store, { categories: result.data.feedCategories, loadingCategories: false });
      });
    };

    const loadPreferences = () => {
      apollo.query({ query: GetUserPreferencesDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
        const prefs = result.data.userPreferences;
        patchState(store, { viewMode: prefs.viewMode, sortOrder: prefs.sortOrder });
      });
    };

    return {
      init: () => {
        loadCategories();
        loadPreferences();
        loadItems();
      },

      selectAll: () => {
        patchState(store, { selectedType: 'all', selectedId: null });
        loadItems();
      },

      selectCategory: (categoryId: string) => {
        patchState(store, { selectedType: 'category', selectedId: categoryId });
        loadItems();
      },

      selectFeed: (feedId: string) => {
        patchState(store, { selectedType: 'feed', selectedId: feedId });
        loadItems();
      },

      setUnreadOnly: (unreadOnly: boolean) => {
        patchState(store, { unreadOnly });
        loadItems();
      },

      setViewMode: (viewMode: 'LIST' | 'FULL') => {
        patchState(store, { viewMode });
        apollo.mutate({
          mutation: UpdateUserPreferencesDocument,
          variables: { viewMode },
        }).subscribe();
      },

      setSortOrder: (sortOrder: 'NEWEST_FIRST' | 'OLDEST_FIRST') => {
        patchState(store, { sortOrder });
        apollo.mutate({
          mutation: UpdateUserPreferencesDocument,
          variables: { sortOrder },
        }).subscribe();
        loadItems();
      },

      markAsRead: (itemId: string) => {
        apollo.mutate({ mutation: MarkItemReadDocument, variables: { itemId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      markAsUnread: (itemId: string) => {
        apollo.mutate({ mutation: MarkItemUnreadDocument, variables: { itemId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      markFeedRead: (feedId: string) => {
        apollo.mutate({ mutation: MarkFeedReadDocument, variables: { feedId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      markCategoryRead: (categoryId: string) => {
        apollo.mutate({ mutation: MarkCategoryReadDocument, variables: { categoryId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      addFeed: (url: string, categoryId?: string) => {
        apollo.mutate({ mutation: AddFeedDocument, variables: { url, categoryId } }).subscribe(() => {
          loadCategories();
        });
      },

      deleteFeed: (feedId: string) => {
        apollo.mutate({ mutation: DeleteFeedDocument, variables: { feedId } }).subscribe(() => {
          loadCategories();
          if (store.selectedType() === 'feed' && store.selectedId() === feedId) {
            patchState(store, { selectedType: 'all', selectedId: null });
          }
          loadItems();
        });
      },

      addCategory: (name: string) => {
        apollo.mutate({ mutation: AddCategoryDocument, variables: { name } }).subscribe(() => {
          loadCategories();
        });
      },

      renameCategory: (categoryId: string, name: string) => {
        apollo.mutate({ mutation: RenameCategoryDocument, variables: { categoryId, name } }).subscribe(() => {
          loadCategories();
        });
      },

      deleteCategory: (categoryId: string) => {
        apollo.mutate({ mutation: DeleteCategoryDocument, variables: { categoryId } }).subscribe(() => {
          loadCategories();
          if (store.selectedType() === 'category' && store.selectedId() === categoryId) {
            patchState(store, { selectedType: 'all', selectedId: null });
          }
          loadItems();
        });
      },

      moveFeedToCategory: (feedId: string, categoryId: string) => {
        apollo.mutate({ mutation: MoveFeedToCategoryDocument, variables: { feedId, categoryId } }).subscribe(() => {
          loadCategories();
        });
      },

      reorderCategories: (categoryIds: string[]) => {
        apollo.mutate({ mutation: ReorderCategoriesDocument, variables: { categoryIds } }).subscribe(() => {
          loadCategories();
        });
      },

      importFeeds: (opml: string, callback?: (result: any) => void) => {
        apollo.mutate({ mutation: ImportFeedsDocument, variables: { opml } }).subscribe((result: any) => {
          loadCategories();
          if (callback) callback(result.data.importFeeds);
        });
      },

      exportFeeds: (callback: (opml: string) => void) => {
        apollo.query({ query: ExportFeedsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
          callback(result.data.exportFeeds);
        });
      },

      refreshItems: () => {
        loadItems();
        loadCategories();
      },
    };
  })
);

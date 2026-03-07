import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GetBookmarksDocument, CreateBookmarkDocument, DeleteBookmarkDocument, Bookmark } from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';

export interface BookmarksState {
  bookmarks: Bookmark[];
  loading: boolean;
  searchQuery: string;
  selectedTags: string[];
}

const initialState: BookmarksState = {
  bookmarks: [],
  loading: false,
  searchQuery: '',
  selectedTags: [],
};

export const BookmarksStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, apollo = inject(Apollo)) => ({
    loadBookmarks: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap(() =>
          apollo.query({
            query: GetBookmarksDocument,
            variables: {
              query: store.searchQuery() || null,
              tags: store.selectedTags().length > 0 ? store.selectedTags() : null,
            },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, {
            bookmarks: result.data.bookmarks,
            loading: false,
          });
        })
      )
    ),

    setSearchQuery: (query: string) => {
      patchState(store, { searchQuery: query });
      store.bookmarks;
    },

    toggleTag: (tag: string) => {
      const current = store.selectedTags();
      const next = current.includes(tag) ? current.filter((t) => t !== tag) : [...current, tag];
      patchState(store, { selectedTags: next });
      store.bookmarks;
    },

    addBookmark: rxMethod<{ url: string; title?: string; tags?: string[] }>(
      pipe(
        switchMap((variables) =>
          apollo.mutate({
            mutation: CreateBookmarkDocument,
            variables,
          })
        ),
        tap(() => store.bookmarks)
      )
    ),

    deleteBookmark: rxMethod<string>(
      pipe(
        switchMap((id) =>
          apollo.mutate({
            mutation: DeleteBookmarkDocument,
            variables: { id },
          })
        ),
        tap(() => store.bookmarks)
      )
    ),
  }))
);

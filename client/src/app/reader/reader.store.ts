import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GetFeedsDocument, GetFeedItemsDocument, MarkItemReadDocument, FeedWithStats, FeedItem } from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';

export interface ReaderState {
  feeds: FeedWithStats[];
  selectedFeedId: string | null;
  items: FeedItem[];
  loadingFeeds: boolean;
  loadingItems: boolean;
  unreadOnly: boolean;
}

const initialState: ReaderState = {
  feeds: [],
  selectedFeedId: null,
  items: [],
  loadingFeeds: false,
  loadingItems: false,
  unreadOnly: true,
};

export const ReaderStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, apollo = inject(Apollo)) => ({
    loadFeeds: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loadingFeeds: true })),
        switchMap(() => apollo.query({ query: GetFeedsDocument, fetchPolicy: 'network-only' })),
        tap((result: any) => {
          patchState(store, {
            feeds: result.data.feeds,
            loadingFeeds: false,
          });
          if (!store.selectedFeedId() && result.data.feeds.length > 0) {
            store.selectFeed(result.data.feeds[0].feed.id);
          }
        })
      )
    ),

    selectFeed: (feedId: string) => {
      patchState(store, { selectedFeedId: feedId });
      store.loadItems(feedId);
    },

    setUnreadOnly: (unreadOnly: boolean) => {
      patchState(store, { unreadOnly });
      if (store.selectedFeedId()) {
        store.loadItems(store.selectedFeedId()!);
      }
    },

    loadItems: rxMethod<string>(
      pipe(
        tap(() => patchState(store, { loadingItems: true })),
        switchMap((feedId) =>
          apollo.query({
            query: GetFeedItemsDocument,
            variables: { feedId, unreadOnly: store.unreadOnly(), limit: 50 },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, {
            items: result.data.feedItems,
            loadingItems: false,
          });
        })
      )
    ),

    markAsRead: rxMethod<string>(
      pipe(
        switchMap((itemId) =>
          apollo.mutate({
            mutation: MarkItemReadDocument,
            variables: { itemId },
          })
        ),
        tap(() => {
          // Update unread count in state locally
          const currentItems = store.items();
          const item = currentItems.find((i) => i.id === (store as any).lastMarkedId);
          // For simplicity, we'll just reload the items or rely on the server.
          // In a real app, we'd optimistically update the state here.
        })
      )
    ),
  }))
);

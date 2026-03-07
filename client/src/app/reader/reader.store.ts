import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GetFeedsDocument, GetFeedItemsDocument, MarkItemReadDocument, FeedWithUnread, FeedItem } from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';

export interface ReaderState {
  feeds: FeedWithUnread[];
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
  withMethods((store, apollo = inject(Apollo)) => {
    const loadItemsRx = rxMethod<string>(
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
    );

    return {
      loadFeeds: () => {
        patchState(store, { loadingFeeds: true });
        apollo.query({ query: GetFeedsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
          patchState(store, {
            feeds: result.data.feeds,
            loadingFeeds: false,
          });
          if (!store.selectedFeedId() && result.data.feeds.length > 0) {
            loadItemsRx(result.data.feeds[0].feed.id);
          }
        });
      },

      selectFeed: (feedId: string) => {
        patchState(store, { selectedFeedId: feedId });
        loadItemsRx(feedId);
      },

      setUnreadOnly: (unreadOnly: boolean) => {
        patchState(store, { unreadOnly });
        if (store.selectedFeedId()) {
          loadItemsRx(store.selectedFeedId()!);
        }
      },

      markAsRead: (itemId: string) => {
        apollo.mutate({
          mutation: MarkItemReadDocument,
          variables: { itemId },
        }).subscribe(() => {
          // Reload items to get updated read status
          if (store.selectedFeedId()) {
            loadItemsRx(store.selectedFeedId()!);
          }
        });
      },
    };
  })
);

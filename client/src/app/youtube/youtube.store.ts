import { signalStore, withState, withMethods, patchState, withComputed } from '@ngrx/signals';
import { inject, computed } from '@angular/core';
import { Apollo } from 'apollo-angular';
import {
  GetYoutubeListsDocument,
  GetVideosDocument,
  AddYoutubeListDocument,
  DeleteYoutubeListDocument,
  RefreshYoutubeListDocument,
  YoutubeListWithStats,
  Video
} from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { WebSocketService } from '../core/services/websocket.service';
import { debounceTime } from 'rxjs/operators';

export interface YoutubeState {
  lists: YoutubeListWithStats[];
  selectedListId: string | null;
  videos: Video[];
  loadingLists: boolean;
  loadingVideos: boolean;
  searchQuery: string;
  removedOnly: boolean;
}

const initialState: YoutubeState = {
  lists: [],
  selectedListId: null,
  videos: [],
  loadingLists: false,
  loadingVideos: false,
  searchQuery: '',
  removedOnly: false,
};

export const YoutubeStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    selectedList: computed(() =>
      store.lists().find(l => l.list.id === store.selectedListId()) || null
    )
  })),
  withMethods((store, apollo = inject(Apollo), ws = inject(WebSocketService)) => {
    const loadVideosRx = rxMethod<{listId: string | null, query: string, removedOnly: boolean}>(
      pipe(
        tap(() => patchState(store, { loadingVideos: true })),
        switchMap(({listId, query, removedOnly}) =>
          apollo.query({
            query: GetVideosDocument,
            variables: { listId, query, removedOnly },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, {
            videos: result.data.videos,
            loadingVideos: false,
          });
        })
      )
    );

    ws.on('videos').pipe(debounceTime(500)).subscribe(() => {
      apollo.query({ query: GetYoutubeListsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
        patchState(store, { lists: result.data.youtubeLists });
      });
      const selectedId = store.selectedListId();
      if (selectedId) {
        loadVideosRx({ listId: selectedId, query: store.searchQuery(), removedOnly: store.removedOnly() });
      }
    });

    return {
      loadLists: () => {
        patchState(store, { loadingLists: true });
        apollo.query({ query: GetYoutubeListsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
          patchState(store, {
            lists: result.data.youtubeLists,
            loadingLists: false,
          });
          if (!store.selectedListId() && result.data.youtubeLists.length > 0) {
            const firstListId = result.data.youtubeLists[0].list.id;
            patchState(store, { selectedListId: firstListId });
            loadVideosRx({ listId: firstListId, query: store.searchQuery(), removedOnly: store.removedOnly() });
          }
        });
      },

      selectList: (listId: string | null) => {
        patchState(store, { selectedListId: listId });
        loadVideosRx({ listId, query: store.searchQuery(), removedOnly: store.removedOnly() });
      },

      setSearchQuery: (query: string) => {
        patchState(store, { searchQuery: query });
        loadVideosRx({ listId: store.selectedListId(), query, removedOnly: store.removedOnly() });
      },

      setRemovedOnly: (removedOnly: boolean) => {
        patchState(store, { removedOnly });
        loadVideosRx({ listId: store.selectedListId(), query: store.searchQuery(), removedOnly });
      },

      addList: (url: string) => {
        return apollo.mutate({
          mutation: AddYoutubeListDocument,
          variables: { url },
        }).pipe(
          tap((result: any) => {
            if (result.data?.addYoutubeList?.list) {
              // Refresh lists
              patchState(store, { loadingLists: true });
              apollo.query({ query: GetYoutubeListsDocument, fetchPolicy: 'network-only' }).subscribe((res: any) => {
                 patchState(store, {
                   lists: res.data.youtubeLists,
                   loadingLists: false,
                 });
              });
            }
          })
        );
      },

      deleteList: (listId: string) => {
        apollo.mutate({
          mutation: DeleteYoutubeListDocument,
          variables: { listId },
        }).subscribe(() => {
          patchState(store, {
            lists: store.lists().filter(l => l.list.id !== listId),
            selectedListId: store.selectedListId() === listId ? null : store.selectedListId(),
            videos: store.selectedListId() === listId ? [] : store.videos()
          });
        });
      },

      refreshList: (listId: string | null) => {
        apollo.mutate({
          mutation: RefreshYoutubeListDocument,
          variables: { listId },
        }).subscribe(() => {
          // Reload videos if the refreshed list is currently selected
          if (!listId || store.selectedListId() === listId) {
            loadVideosRx({
              listId: store.selectedListId(),
              query: store.searchQuery(),
              removedOnly: store.removedOnly()
            });
          }
        });
      }
    };
  })
);

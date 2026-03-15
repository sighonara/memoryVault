import { computed } from '@angular/core';
import { signalStore, withState, withMethods, withComputed, patchState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Apollo } from 'apollo-angular';
import {
  GetBookmarksDocument,
  CreateBookmarkDocument,
  DeleteBookmarkDocument,
  GetFoldersDocument,
  CreateFolderDocument,
  RenameFolderDocument,
  MoveFolderDocument,
  DeleteFolderDocument,
  MoveBookmarkDocument,
  ExportBookmarksDocument,
  GetPendingIngestsDocument,
  Bookmark,
  Folder,
  IngestPreview,
} from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, catchError, EMPTY } from 'rxjs';

export interface IngestResolutionInput {
  url: string;
  action: 'ACCEPT' | 'SKIP' | 'UNDELETE';
}

export interface CommitResult {
  accepted: number;
  skipped: number;
  undeleted: number;
}

export interface IngestPreviewResult {
  previewId: string;
  items: Array<{
    url: string;
    title: string;
    status: string;
    existingBookmarkId?: string;
    suggestedFolderId?: string;
    browserFolder?: string;
  }>;
  summary: {
    newCount: number;
    unchangedCount: number;
    movedCount: number;
    titleChangedCount: number;
    previouslyDeletedCount: number;
  };
}

export interface PendingIngestSummary {
  previewId: string;
  newCount: number;
  totalCount: number;
}

export interface BookmarksState {
  bookmarks: Bookmark[];
  folders: Folder[];
  selectedFolderId: string | null;
  loading: boolean;
  searchQuery: string;
  selectedTags: string[];
  ingestPreview: IngestPreviewResult | null;
  ingestLoading: boolean;
  pendingIngests: PendingIngestSummary[];
}

const initialState: BookmarksState = {
  bookmarks: [],
  folders: [],
  selectedFolderId: null,
  loading: false,
  searchQuery: '',
  selectedTags: [],
  ingestPreview: null,
  ingestLoading: false,
  pendingIngests: [],
};

export const BookmarksStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    filteredBookmarks: computed(() => {
      const folderId = store.selectedFolderId();
      const bookmarks = store.bookmarks();
      const query = store.searchQuery();
      // When searching, show results from all folders
      if (query) return bookmarks;
      if (folderId === null) return bookmarks;
      if (folderId === 'unfiled') return bookmarks.filter(b => !b.folderId);
      return bookmarks.filter(b => b.folderId === folderId);
    }),
    unfiledCount: computed(() => store.bookmarks().filter(b => !b.folderId).length),
  })),
  withMethods((store, apollo = inject(Apollo), http = inject(HttpClient)) => {
    // Define rxMethod methods as local variables so they can be called
    // from other methods within the same withMethods block.
    // The `store` parameter only has state/computed — NOT methods.
    const loadBookmarks = rxMethod<void>(
      pipe(
        tap(() => {
          if (store.bookmarks().length === 0) {
            patchState(store, { loading: true });
          }
        }),
        switchMap(() =>
          apollo.query({
            query: GetBookmarksDocument,
            variables: {
              query: store.searchQuery() || null,
              tags: store.selectedTags().length > 0 ? store.selectedTags() : null,
            },
            fetchPolicy: 'network-only',
          }).pipe(catchError(() => EMPTY))
        ),
        tap((result: any) => {
          patchState(store, {
            bookmarks: result.data.bookmarks,
            loading: false,
          });
        })
      )
    );

    const loadFolders = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetFoldersDocument,
            fetchPolicy: 'network-only',
          }).pipe(catchError(() => EMPTY))
        ),
        tap((result: any) => {
          patchState(store, { folders: result.data.folders });
        })
      )
    );

    const loadPendingIngests = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetPendingIngestsDocument,
            fetchPolicy: 'network-only',
          }).pipe(catchError(() => EMPTY))
        ),
        tap((result: any) => {
          const pending = result.data.pendingIngests.map((p: any) => ({
            previewId: p.previewId,
            newCount: p.summary.newCount,
            totalCount: p.summary.newCount + p.summary.movedCount +
              p.summary.titleChangedCount + p.summary.previouslyDeletedCount,
          }));
          patchState(store, { pendingIngests: pending });
        })
      )
    );

    return {
      loadBookmarks,
      loadFolders,
      loadPendingIngests,

      selectFolder: (folderId: string | null) => {
        patchState(store, { selectedFolderId: folderId });
      },

      setSearchQuery: (query: string) => {
        patchState(store, { searchQuery: query });
        loadBookmarks();
      },

      toggleTag: (tag: string) => {
        const current = store.selectedTags();
        const next = current.includes(tag) ? current.filter((t) => t !== tag) : [...current, tag];
        patchState(store, { selectedTags: next });
        loadBookmarks();
      },

      addBookmark: rxMethod<{ url: string; title?: string; tags?: string[]; folderId?: string }>(
        pipe(
          switchMap((variables) =>
            apollo.mutate({
              mutation: CreateBookmarkDocument,
              variables,
            }).pipe(catchError(() => EMPTY))
          ),
          tap(() => loadBookmarks())
        )
      ),

      deleteBookmark: rxMethod<string>(
        pipe(
          switchMap((id) =>
            apollo.mutate({
              mutation: DeleteBookmarkDocument,
              variables: { id },
            }).pipe(catchError(() => EMPTY))
          ),
          tap(() => loadBookmarks())
        )
      ),

      createFolder: rxMethod<{ name: string; parentId?: string }>(
        pipe(
          switchMap((input) =>
            apollo.mutate({
              mutation: CreateFolderDocument,
              variables: input,
            }).pipe(catchError(() => EMPTY))
          ),
          tap((result: any) => {
            const newFolder = result.data.createFolder;
            patchState(store, { folders: [...store.folders(), newFolder] });
          })
        )
      ),

      renameFolder: rxMethod<{ id: string; name: string }>(
        pipe(
          switchMap((input) =>
            apollo.mutate({
              mutation: RenameFolderDocument,
              variables: input,
            }).pipe(catchError(() => EMPTY))
          ),
          tap((result: any) => {
            const updated = result.data.renameFolder;
            patchState(store, {
              folders: store.folders().map(f => f.id === updated.id ? { ...f, name: updated.name } : f),
            });
          })
        )
      ),

      moveFolder: rxMethod<{ id: string; newParentId?: string }>(
        pipe(
          switchMap((input) =>
            apollo.mutate({
              mutation: MoveFolderDocument,
              variables: input,
            }).pipe(catchError(() => EMPTY))
          ),
          tap(() => loadFolders())
        )
      ),

      deleteFolder: rxMethod<string>(
        pipe(
          switchMap((id) =>
            apollo.mutate({
              mutation: DeleteFolderDocument,
              variables: { id },
            }).pipe(
              tap(() => {
                patchState(store, {
                  folders: store.folders().filter(f => f.id !== id),
                });
              }),
              catchError(() => EMPTY),
            )
          ),
        )
      ),

      moveBookmark: rxMethod<{ id: string; folderId?: string }>(
        pipe(
          switchMap((input) =>
            apollo.mutate({
              mutation: MoveBookmarkDocument,
              variables: input,
            }).pipe(catchError(() => EMPTY))
          ),
          tap(() => loadBookmarks())
        )
      ),

      fetchIngestPreview: rxMethod<string>(
        pipe(
          tap(() => patchState(store, { ingestLoading: true })),
          switchMap((previewId) =>
            http.get<IngestPreviewResult>(`/api/bookmarks/ingest/${previewId}`).pipe(
              catchError(() => {
                patchState(store, { ingestLoading: false });
                return EMPTY;
              })
            )
          ),
          tap((preview) =>
            patchState(store, { ingestPreview: preview, ingestLoading: false })
          )
        )
      ),

      commitIngest: rxMethod<{ previewId: string; resolutions: IngestResolutionInput[] }>(
        pipe(
          switchMap(({ previewId, resolutions }) =>
            http.post<CommitResult>(`/api/bookmarks/ingest/${previewId}/commit`, { resolutions }).pipe(
              catchError(() => EMPTY)
            )
          ),
          tap(() => {
            patchState(store, { ingestPreview: null });
            loadBookmarks();
            loadFolders();
            loadPendingIngests();
          })
        )
      ),

      exportBookmarks: rxMethod<void>(
        pipe(
          switchMap(() =>
            apollo.query({
              query: ExportBookmarksDocument,
              fetchPolicy: 'no-cache',
            }).pipe(catchError(() => EMPTY))
          ),
          tap((result: any) => {
            const blob = new Blob([result.data.exportBookmarks], { type: 'text/html' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'memoryvault-export.html';
            a.click();
            URL.revokeObjectURL(url);
          })
        )
      ),
    };
  })
);

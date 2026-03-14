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
  Bookmark,
  Folder,
  IngestPreview,
} from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';

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

export interface BookmarksState {
  bookmarks: Bookmark[];
  folders: Folder[];
  selectedFolderId: string | null;
  loading: boolean;
  searchQuery: string;
  selectedTags: string[];
  ingestPreview: IngestPreviewResult | null;
  ingestLoading: boolean;
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
};

export const BookmarksStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    filteredBookmarks: computed(() => {
      const folderId = store.selectedFolderId();
      const bookmarks = store.bookmarks();
      if (folderId === null) return bookmarks;
      if (folderId === 'unfiled') return bookmarks.filter(b => !b.folderId);
      return bookmarks.filter(b => b.folderId === folderId);
    }),
  })),
  withMethods((store, apollo = inject(Apollo), http = inject(HttpClient)) => ({
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

    loadFolders: rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetFoldersDocument,
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { folders: result.data.folders });
        })
      )
    ),

    selectFolder: (folderId: string | null) => {
      patchState(store, { selectedFolderId: folderId });
    },

    setSearchQuery: (query: string) => {
      patchState(store, { searchQuery: query });
      (store as any).loadBookmarks();
    },

    toggleTag: (tag: string) => {
      const current = store.selectedTags();
      const next = current.includes(tag) ? current.filter((t) => t !== tag) : [...current, tag];
      patchState(store, { selectedTags: next });
      (store as any).loadBookmarks();
    },

    addBookmark: rxMethod<{ url: string; title?: string; tags?: string[] }>(
      pipe(
        switchMap((variables) =>
          apollo.mutate({
            mutation: CreateBookmarkDocument,
            variables,
          })
        ),
        tap(() => (store as any).loadBookmarks())
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
        tap(() => (store as any).loadBookmarks())
      )
    ),

    createFolder: rxMethod<{ name: string; parentId?: string }>(
      pipe(
        switchMap((input) =>
          apollo.mutate({
            mutation: CreateFolderDocument,
            variables: input,
          })
        ),
        tap(() => (store as any).loadFolders())
      )
    ),

    renameFolder: rxMethod<{ id: string; name: string }>(
      pipe(
        switchMap((input) =>
          apollo.mutate({
            mutation: RenameFolderDocument,
            variables: input,
          })
        ),
        tap(() => (store as any).loadFolders())
      )
    ),

    moveFolder: rxMethod<{ id: string; newParentId?: string }>(
      pipe(
        switchMap((input) =>
          apollo.mutate({
            mutation: MoveFolderDocument,
            variables: input,
          })
        ),
        tap(() => (store as any).loadFolders())
      )
    ),

    deleteFolder: rxMethod<string>(
      pipe(
        switchMap((id) =>
          apollo.mutate({
            mutation: DeleteFolderDocument,
            variables: { id },
          })
        ),
        tap(() => (store as any).loadFolders())
      )
    ),

    moveBookmark: rxMethod<{ id: string; folderId?: string }>(
      pipe(
        switchMap((input) =>
          apollo.mutate({
            mutation: MoveBookmarkDocument,
            variables: input,
          })
        ),
        tap(() => (store as any).loadBookmarks())
      )
    ),

    fetchIngestPreview: rxMethod<string>(
      pipe(
        tap(() => patchState(store, { ingestLoading: true })),
        switchMap((previewId) =>
          http.get<IngestPreviewResult>(`/api/bookmarks/ingest/${previewId}`)
        ),
        tap((preview) =>
          patchState(store, { ingestPreview: preview, ingestLoading: false })
        )
      )
    ),

    commitIngest: rxMethod<{ previewId: string; resolutions: IngestResolutionInput[] }>(
      pipe(
        switchMap(({ previewId, resolutions }) =>
          http.post<CommitResult>(`/api/bookmarks/ingest/${previewId}/commit`, { resolutions })
        ),
        tap(() => {
          patchState(store, { ingestPreview: null });
          (store as any).loadBookmarks();
        })
      )
    ),

    exportBookmarks: rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: ExportBookmarksDocument,
            fetchPolicy: 'no-cache',
          })
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
  }))
);

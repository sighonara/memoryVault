import { gql } from 'apollo-angular';
import { Injectable } from '@angular/core';
import * as Apollo from 'apollo-angular';
export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  Instant: { input: any; output: any; }
  UUID: { input: any; output: any; }
};

export type Bookmark = {
  __typename?: 'Bookmark';
  createdAt: Scalars['Instant']['output'];
  folderId?: Maybe<Scalars['UUID']['output']>;
  id: Scalars['UUID']['output'];
  sortOrder: Scalars['Int']['output'];
  tags: Array<Tag>;
  title?: Maybe<Scalars['String']['output']>;
  updatedAt: Scalars['Instant']['output'];
  url: Scalars['String']['output'];
};

export type CommitResult = {
  __typename?: 'CommitResult';
  accepted: Scalars['Int']['output'];
  skipped: Scalars['Int']['output'];
  undeleted: Scalars['Int']['output'];
};

export type Feed = {
  __typename?: 'Feed';
  categoryId?: Maybe<Scalars['UUID']['output']>;
  description?: Maybe<Scalars['String']['output']>;
  failureCount: Scalars['Int']['output'];
  id: Scalars['UUID']['output'];
  lastFetchedAt?: Maybe<Scalars['Instant']['output']>;
  siteUrl?: Maybe<Scalars['String']['output']>;
  title?: Maybe<Scalars['String']['output']>;
  url: Scalars['String']['output'];
};

export type FeedCategory = {
  __typename?: 'FeedCategory';
  id: Scalars['UUID']['output'];
  name: Scalars['String']['output'];
  sortOrder: Scalars['Int']['output'];
};

export type FeedCategoryWithFeeds = {
  __typename?: 'FeedCategoryWithFeeds';
  category: FeedCategory;
  feeds: Array<FeedWithUnread>;
  totalUnread: Scalars['Int']['output'];
};

export type FeedItem = {
  __typename?: 'FeedItem';
  author?: Maybe<Scalars['String']['output']>;
  content?: Maybe<Scalars['String']['output']>;
  feedId: Scalars['UUID']['output'];
  id: Scalars['UUID']['output'];
  imageUrl?: Maybe<Scalars['String']['output']>;
  publishedAt?: Maybe<Scalars['Instant']['output']>;
  readAt?: Maybe<Scalars['Instant']['output']>;
  starredAt?: Maybe<Scalars['Instant']['output']>;
  tags: Array<Tag>;
  title?: Maybe<Scalars['String']['output']>;
  url?: Maybe<Scalars['String']['output']>;
};

export type FeedRefreshResult = {
  __typename?: 'FeedRefreshResult';
  feedId: Scalars['UUID']['output'];
  feedTitle?: Maybe<Scalars['String']['output']>;
  newItems: Scalars['Int']['output'];
};

export type FeedWithUnread = {
  __typename?: 'FeedWithUnread';
  feed: Feed;
  unreadCount: Scalars['Int']['output'];
};

export type Folder = {
  __typename?: 'Folder';
  bookmarkCount: Scalars['Int']['output'];
  bookmarks: Array<Bookmark>;
  children: Array<Folder>;
  id: Scalars['UUID']['output'];
  name: Scalars['String']['output'];
  parentId?: Maybe<Scalars['UUID']['output']>;
  sortOrder: Scalars['Int']['output'];
};

export type ImportResult = {
  __typename?: 'ImportResult';
  categoriesCreated: Scalars['Int']['output'];
  feedsAdded: Scalars['Int']['output'];
  feedsSkipped: Scalars['Int']['output'];
};

export enum IngestAction {
  Accept = 'ACCEPT',
  Skip = 'SKIP',
  Undelete = 'UNDELETE'
}

export type IngestBookmarkInputGql = {
  browserFolder?: InputMaybe<Scalars['String']['input']>;
  title: Scalars['String']['input'];
  url: Scalars['String']['input'];
};

export type IngestInput = {
  bookmarks: Array<IngestBookmarkInputGql>;
};

export type IngestItem = {
  __typename?: 'IngestItem';
  browserFolder?: Maybe<Scalars['String']['output']>;
  existingBookmarkId?: Maybe<Scalars['UUID']['output']>;
  status: IngestStatus;
  suggestedFolderId?: Maybe<Scalars['UUID']['output']>;
  title: Scalars['String']['output'];
  url: Scalars['String']['output'];
};

export type IngestPreview = {
  __typename?: 'IngestPreview';
  items: Array<IngestItem>;
  previewId: Scalars['UUID']['output'];
  summary: IngestSummary;
};

export type IngestResolutionInput = {
  action: IngestAction;
  url: Scalars['String']['input'];
};

export enum IngestStatus {
  Moved = 'MOVED',
  New = 'NEW',
  PreviouslyDeleted = 'PREVIOUSLY_DELETED',
  TitleChanged = 'TITLE_CHANGED',
  Unchanged = 'UNCHANGED'
}

export type IngestSummary = {
  __typename?: 'IngestSummary';
  movedCount: Scalars['Int']['output'];
  newCount: Scalars['Int']['output'];
  previouslyDeletedCount: Scalars['Int']['output'];
  titleChangedCount: Scalars['Int']['output'];
  unchangedCount: Scalars['Int']['output'];
};

export type LogEntry = {
  __typename?: 'LogEntry';
  level: Scalars['String']['output'];
  logger: Scalars['String']['output'];
  message: Scalars['String']['output'];
  thread: Scalars['String']['output'];
  timestamp: Scalars['Instant']['output'];
};

export type LoginResponse = {
  __typename?: 'LoginResponse';
  displayName: Scalars['String']['output'];
  email: Scalars['String']['output'];
  role: Scalars['String']['output'];
  token: Scalars['String']['output'];
};

export type Mutation = {
  __typename?: 'Mutation';
  addBookmark: Bookmark;
  addCategory: FeedCategory;
  addFeed: Feed;
  addYoutubeList: YoutubeListAddResult;
  commitIngest: CommitResult;
  createFolder: Folder;
  deleteBookmark?: Maybe<Bookmark>;
  deleteCategory: Scalars['Boolean']['output'];
  deleteFeed?: Maybe<Feed>;
  deleteFolder: Scalars['Boolean']['output'];
  deleteYoutubeList?: Maybe<YoutubeList>;
  importFeeds: ImportResult;
  ingestBookmarks: IngestPreview;
  login: LoginResponse;
  markCategoryRead: Scalars['Int']['output'];
  markFeedRead: Scalars['Int']['output'];
  markItemRead?: Maybe<FeedItem>;
  markItemUnread?: Maybe<FeedItem>;
  moveBookmark: Bookmark;
  moveFeedToCategory?: Maybe<Feed>;
  moveFolder: Folder;
  refreshFeed: Array<FeedRefreshResult>;
  refreshYoutubeList: Array<SyncResult>;
  renameCategory?: Maybe<FeedCategory>;
  renameFolder: Folder;
  reorderBookmarks: Array<Bookmark>;
  reorderCategories: Array<FeedCategory>;
  tagBookmark?: Maybe<Bookmark>;
  updateUserPreferences: UserPreferences;
};


export type MutationAddBookmarkArgs = {
  folderId?: InputMaybe<Scalars['UUID']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  title?: InputMaybe<Scalars['String']['input']>;
  url: Scalars['String']['input'];
};


export type MutationAddCategoryArgs = {
  name: Scalars['String']['input'];
};


export type MutationAddFeedArgs = {
  categoryId?: InputMaybe<Scalars['UUID']['input']>;
  url: Scalars['String']['input'];
};


export type MutationAddYoutubeListArgs = {
  url: Scalars['String']['input'];
};


export type MutationCommitIngestArgs = {
  previewId: Scalars['UUID']['input'];
  resolutions: Array<IngestResolutionInput>;
};


export type MutationCreateFolderArgs = {
  name: Scalars['String']['input'];
  parentId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationDeleteBookmarkArgs = {
  id: Scalars['UUID']['input'];
};


export type MutationDeleteCategoryArgs = {
  categoryId: Scalars['UUID']['input'];
};


export type MutationDeleteFeedArgs = {
  feedId: Scalars['UUID']['input'];
};


export type MutationDeleteFolderArgs = {
  id: Scalars['UUID']['input'];
};


export type MutationDeleteYoutubeListArgs = {
  listId: Scalars['UUID']['input'];
};


export type MutationImportFeedsArgs = {
  opml: Scalars['String']['input'];
};


export type MutationIngestBookmarksArgs = {
  input: IngestInput;
};


export type MutationLoginArgs = {
  email: Scalars['String']['input'];
  password: Scalars['String']['input'];
};


export type MutationMarkCategoryReadArgs = {
  categoryId: Scalars['UUID']['input'];
};


export type MutationMarkFeedReadArgs = {
  feedId: Scalars['UUID']['input'];
};


export type MutationMarkItemReadArgs = {
  itemId: Scalars['UUID']['input'];
};


export type MutationMarkItemUnreadArgs = {
  itemId: Scalars['UUID']['input'];
};


export type MutationMoveBookmarkArgs = {
  folderId?: InputMaybe<Scalars['UUID']['input']>;
  id: Scalars['UUID']['input'];
};


export type MutationMoveFeedToCategoryArgs = {
  categoryId: Scalars['UUID']['input'];
  feedId: Scalars['UUID']['input'];
};


export type MutationMoveFolderArgs = {
  id: Scalars['UUID']['input'];
  newParentId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationRefreshFeedArgs = {
  feedId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationRefreshYoutubeListArgs = {
  listId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationRenameCategoryArgs = {
  categoryId: Scalars['UUID']['input'];
  name: Scalars['String']['input'];
};


export type MutationRenameFolderArgs = {
  id: Scalars['UUID']['input'];
  name: Scalars['String']['input'];
};


export type MutationReorderBookmarksArgs = {
  bookmarkIds: Array<Scalars['UUID']['input']>;
  folderId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationReorderCategoriesArgs = {
  categoryIds: Array<Scalars['UUID']['input']>;
};


export type MutationTagBookmarkArgs = {
  id: Scalars['UUID']['input'];
  tags: Array<Scalars['String']['input']>;
};


export type MutationUpdateUserPreferencesArgs = {
  sortOrder?: InputMaybe<Scalars['String']['input']>;
  viewMode?: InputMaybe<Scalars['String']['input']>;
};

export type Query = {
  __typename?: 'Query';
  bookmarks: Array<Bookmark>;
  exportBookmarks: Scalars['String']['output'];
  exportFeeds: Scalars['String']['output'];
  feedCategories: Array<FeedCategoryWithFeeds>;
  feedItems: Array<FeedItem>;
  feedItemsAll: Array<FeedItem>;
  feedItemsByCategory: Array<FeedItem>;
  feeds: Array<FeedWithUnread>;
  folder?: Maybe<Folder>;
  folders: Array<Folder>;
  jobs: Array<SyncJob>;
  logs: Array<LogEntry>;
  pendingIngests: Array<IngestPreview>;
  search: Array<SearchResult>;
  stats: SystemStats;
  userPreferences: UserPreferences;
  videoStatus?: Maybe<Video>;
  videos: Array<Video>;
  youtubeLists: Array<YoutubeListWithStats>;
};


export type QueryBookmarksArgs = {
  query?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


export type QueryFeedItemsArgs = {
  feedId: Scalars['UUID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryFeedItemsAllArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryFeedItemsByCategoryArgs = {
  categoryId: Scalars['UUID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
};


export type QueryFolderArgs = {
  id: Scalars['UUID']['input'];
};


export type QueryJobsArgs = {
  limit?: InputMaybe<Scalars['Int']['input']>;
  type?: InputMaybe<Scalars['String']['input']>;
};


export type QueryLogsArgs = {
  level?: InputMaybe<Scalars['String']['input']>;
  limit?: InputMaybe<Scalars['Int']['input']>;
  service?: InputMaybe<Scalars['String']['input']>;
};


export type QuerySearchArgs = {
  query: Scalars['String']['input'];
  types?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


export type QueryVideoStatusArgs = {
  videoId: Scalars['UUID']['input'];
};


export type QueryVideosArgs = {
  listId?: InputMaybe<Scalars['UUID']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
  removedOnly?: InputMaybe<Scalars['Boolean']['input']>;
};

export type SearchResult = {
  __typename?: 'SearchResult';
  id: Scalars['UUID']['output'];
  rank: Scalars['Float']['output'];
  title?: Maybe<Scalars['String']['output']>;
  type: Scalars['String']['output'];
  url?: Maybe<Scalars['String']['output']>;
};

export type SyncJob = {
  __typename?: 'SyncJob';
  completedAt?: Maybe<Scalars['Instant']['output']>;
  errorMessage?: Maybe<Scalars['String']['output']>;
  id: Scalars['UUID']['output'];
  metadata?: Maybe<Scalars['String']['output']>;
  startedAt: Scalars['Instant']['output'];
  status: Scalars['String']['output'];
  triggeredBy: Scalars['String']['output'];
  type: Scalars['String']['output'];
};

export type SyncResult = {
  __typename?: 'SyncResult';
  downloadFailures: Scalars['Int']['output'];
  downloadSuccesses: Scalars['Int']['output'];
  listId: Scalars['UUID']['output'];
  newVideos: Scalars['Int']['output'];
  removedVideos: Scalars['Int']['output'];
};

export type SystemStats = {
  __typename?: 'SystemStats';
  bookmarkCount: Scalars['Int']['output'];
  downloadedVideoCount: Scalars['Int']['output'];
  feedCount: Scalars['Int']['output'];
  feedItemCount: Scalars['Int']['output'];
  feedsWithFailures: Scalars['Int']['output'];
  lastFeedSync?: Maybe<Scalars['Instant']['output']>;
  lastYoutubeSync?: Maybe<Scalars['Instant']['output']>;
  removedVideoCount: Scalars['Int']['output'];
  storageUsedBytes: Scalars['Float']['output'];
  tagCount: Scalars['Int']['output'];
  unreadFeedItemCount: Scalars['Int']['output'];
  videoCount: Scalars['Int']['output'];
  youtubeListCount: Scalars['Int']['output'];
  youtubeListsWithFailures: Scalars['Int']['output'];
};

export type Tag = {
  __typename?: 'Tag';
  color?: Maybe<Scalars['String']['output']>;
  id: Scalars['UUID']['output'];
  name: Scalars['String']['output'];
};

export type UserPreferences = {
  __typename?: 'UserPreferences';
  sortOrder: Scalars['String']['output'];
  viewMode: Scalars['String']['output'];
};

export type Video = {
  __typename?: 'Video';
  channelName?: Maybe<Scalars['String']['output']>;
  description?: Maybe<Scalars['String']['output']>;
  downloadedAt?: Maybe<Scalars['Instant']['output']>;
  durationSeconds?: Maybe<Scalars['Int']['output']>;
  filePath?: Maybe<Scalars['String']['output']>;
  id: Scalars['UUID']['output'];
  removedDetectedAt?: Maybe<Scalars['Instant']['output']>;
  removedFromYoutube: Scalars['Boolean']['output'];
  tags: Array<Tag>;
  thumbnailPath?: Maybe<Scalars['String']['output']>;
  title?: Maybe<Scalars['String']['output']>;
  youtubeUrl: Scalars['String']['output'];
  youtubeVideoId: Scalars['String']['output'];
};

export type YoutubeList = {
  __typename?: 'YoutubeList';
  description?: Maybe<Scalars['String']['output']>;
  failureCount: Scalars['Int']['output'];
  id: Scalars['UUID']['output'];
  lastSyncedAt?: Maybe<Scalars['Instant']['output']>;
  name?: Maybe<Scalars['String']['output']>;
  url: Scalars['String']['output'];
  youtubeListId: Scalars['String']['output'];
};

export type YoutubeListAddResult = {
  __typename?: 'YoutubeListAddResult';
  list: YoutubeList;
  newVideos: Scalars['Int']['output'];
};

export type YoutubeListWithStats = {
  __typename?: 'YoutubeListWithStats';
  downloadedVideos: Scalars['Int']['output'];
  list: YoutubeList;
  removedVideos: Scalars['Int']['output'];
  totalVideos: Scalars['Int']['output'];
};

export type GetJobsQueryVariables = Exact<{
  type?: InputMaybe<Scalars['String']['input']>;
  limit?: InputMaybe<Scalars['Int']['input']>;
}>;


export type GetJobsQuery = { __typename?: 'Query', jobs: Array<{ __typename?: 'SyncJob', id: any, type: string, status: string, startedAt: any, completedAt?: any | null, errorMessage?: string | null, triggeredBy: string, metadata?: string | null }> };

export type GetLogsQueryVariables = Exact<{
  level?: InputMaybe<Scalars['String']['input']>;
  service?: InputMaybe<Scalars['String']['input']>;
  limit?: InputMaybe<Scalars['Int']['input']>;
}>;


export type GetLogsQuery = { __typename?: 'Query', logs: Array<{ __typename?: 'LogEntry', timestamp: any, level: string, logger: string, message: string, thread: string }> };

export type GetAdminStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetAdminStatsQuery = { __typename?: 'Query', stats: { __typename?: 'SystemStats', bookmarkCount: number, feedCount: number, feedItemCount: number, unreadFeedItemCount: number, youtubeListCount: number, videoCount: number, downloadedVideoCount: number, removedVideoCount: number, tagCount: number, storageUsedBytes: number, lastFeedSync?: any | null, lastYoutubeSync?: any | null, feedsWithFailures: number, youtubeListsWithFailures: number } };

export type GetBookmarksQueryVariables = Exact<{
  query?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>> | InputMaybe<Scalars['String']['input']>>;
}>;


export type GetBookmarksQuery = { __typename?: 'Query', bookmarks: Array<{ __typename?: 'Bookmark', id: any, url: string, title?: string | null, folderId?: any | null, sortOrder: number, createdAt: any, tags: Array<{ __typename?: 'Tag', name: string }> }> };

export type CreateBookmarkMutationVariables = Exact<{
  url: Scalars['String']['input'];
  title?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>> | InputMaybe<Scalars['String']['input']>>;
  folderId?: InputMaybe<Scalars['UUID']['input']>;
}>;


export type CreateBookmarkMutation = { __typename?: 'Mutation', addBookmark: { __typename?: 'Bookmark', id: any, url: string, title?: string | null, folderId?: any | null } };

export type DeleteBookmarkMutationVariables = Exact<{
  id: Scalars['UUID']['input'];
}>;


export type DeleteBookmarkMutation = { __typename?: 'Mutation', deleteBookmark?: { __typename?: 'Bookmark', id: any } | null };

export type GetSystemStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetSystemStatsQuery = { __typename?: 'Query', stats: { __typename?: 'SystemStats', bookmarkCount: number, tagCount: number } };

export type GetFoldersQueryVariables = Exact<{ [key: string]: never; }>;


export type GetFoldersQuery = { __typename?: 'Query', folders: Array<{ __typename?: 'Folder', id: any, name: string, parentId?: any | null, bookmarkCount: number, sortOrder: number }> };

export type CreateFolderMutationVariables = Exact<{
  name: Scalars['String']['input'];
  parentId?: InputMaybe<Scalars['UUID']['input']>;
}>;


export type CreateFolderMutation = { __typename?: 'Mutation', createFolder: { __typename?: 'Folder', id: any, name: string, parentId?: any | null, sortOrder: number } };

export type RenameFolderMutationVariables = Exact<{
  id: Scalars['UUID']['input'];
  name: Scalars['String']['input'];
}>;


export type RenameFolderMutation = { __typename?: 'Mutation', renameFolder: { __typename?: 'Folder', id: any, name: string } };

export type MoveFolderMutationVariables = Exact<{
  id: Scalars['UUID']['input'];
  newParentId?: InputMaybe<Scalars['UUID']['input']>;
}>;


export type MoveFolderMutation = { __typename?: 'Mutation', moveFolder: { __typename?: 'Folder', id: any, parentId?: any | null } };

export type DeleteFolderMutationVariables = Exact<{
  id: Scalars['UUID']['input'];
}>;


export type DeleteFolderMutation = { __typename?: 'Mutation', deleteFolder: boolean };

export type MoveBookmarkMutationVariables = Exact<{
  id: Scalars['UUID']['input'];
  folderId?: InputMaybe<Scalars['UUID']['input']>;
}>;


export type MoveBookmarkMutation = { __typename?: 'Mutation', moveBookmark: { __typename?: 'Bookmark', id: any, folderId?: any | null } };

export type ExportBookmarksQueryVariables = Exact<{ [key: string]: never; }>;


export type ExportBookmarksQuery = { __typename?: 'Query', exportBookmarks: string };

export type GetPendingIngestsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetPendingIngestsQuery = { __typename?: 'Query', pendingIngests: Array<{ __typename?: 'IngestPreview', previewId: any, summary: { __typename?: 'IngestSummary', newCount: number, unchangedCount: number, movedCount: number, titleChangedCount: number, previouslyDeletedCount: number } }> };

export type GetFeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetFeedsQuery = { __typename?: 'Query', feeds: Array<{ __typename?: 'FeedWithUnread', unreadCount: number, feed: { __typename?: 'Feed', id: any, url: string, title?: string | null, siteUrl?: string | null, categoryId?: any | null } }> };

export type GetFeedCategoriesQueryVariables = Exact<{ [key: string]: never; }>;


export type GetFeedCategoriesQuery = { __typename?: 'Query', feedCategories: Array<{ __typename?: 'FeedCategoryWithFeeds', totalUnread: number, category: { __typename?: 'FeedCategory', id: any, name: string, sortOrder: number }, feeds: Array<{ __typename?: 'FeedWithUnread', unreadCount: number, feed: { __typename?: 'Feed', id: any, url: string, title?: string | null, siteUrl?: string | null, categoryId?: any | null } }> }> };

export type GetFeedItemsQueryVariables = Exact<{
  feedId: Scalars['UUID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
}>;


export type GetFeedItemsQuery = { __typename?: 'Query', feedItems: Array<{ __typename?: 'FeedItem', id: any, feedId: any, title?: string | null, url?: string | null, content?: string | null, author?: string | null, publishedAt?: any | null, readAt?: any | null }> };

export type GetFeedItemsByCategoryQueryVariables = Exact<{
  categoryId: Scalars['UUID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
}>;


export type GetFeedItemsByCategoryQuery = { __typename?: 'Query', feedItemsByCategory: Array<{ __typename?: 'FeedItem', id: any, feedId: any, title?: string | null, url?: string | null, content?: string | null, author?: string | null, publishedAt?: any | null, readAt?: any | null }> };

export type GetAllFeedItemsQueryVariables = Exact<{
  limit?: InputMaybe<Scalars['Int']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
}>;


export type GetAllFeedItemsQuery = { __typename?: 'Query', feedItemsAll: Array<{ __typename?: 'FeedItem', id: any, feedId: any, title?: string | null, url?: string | null, content?: string | null, author?: string | null, publishedAt?: any | null, readAt?: any | null }> };

export type GetUserPreferencesQueryVariables = Exact<{ [key: string]: never; }>;


export type GetUserPreferencesQuery = { __typename?: 'Query', userPreferences: { __typename?: 'UserPreferences', viewMode: string, sortOrder: string } };

export type ExportFeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type ExportFeedsQuery = { __typename?: 'Query', exportFeeds: string };

export type MarkItemReadMutationVariables = Exact<{
  itemId: Scalars['UUID']['input'];
}>;


export type MarkItemReadMutation = { __typename?: 'Mutation', markItemRead?: { __typename?: 'FeedItem', id: any, readAt?: any | null } | null };

export type MarkItemUnreadMutationVariables = Exact<{
  itemId: Scalars['UUID']['input'];
}>;


export type MarkItemUnreadMutation = { __typename?: 'Mutation', markItemUnread?: { __typename?: 'FeedItem', id: any, readAt?: any | null } | null };

export type MarkFeedReadMutationVariables = Exact<{
  feedId: Scalars['UUID']['input'];
}>;


export type MarkFeedReadMutation = { __typename?: 'Mutation', markFeedRead: number };

export type MarkCategoryReadMutationVariables = Exact<{
  categoryId: Scalars['UUID']['input'];
}>;


export type MarkCategoryReadMutation = { __typename?: 'Mutation', markCategoryRead: number };

export type AddFeedMutationVariables = Exact<{
  url: Scalars['String']['input'];
  categoryId?: InputMaybe<Scalars['UUID']['input']>;
}>;


export type AddFeedMutation = { __typename?: 'Mutation', addFeed: { __typename?: 'Feed', id: any, url: string, title?: string | null, categoryId?: any | null } };

export type DeleteFeedMutationVariables = Exact<{
  feedId: Scalars['UUID']['input'];
}>;


export type DeleteFeedMutation = { __typename?: 'Mutation', deleteFeed?: { __typename?: 'Feed', id: any } | null };

export type AddCategoryMutationVariables = Exact<{
  name: Scalars['String']['input'];
}>;


export type AddCategoryMutation = { __typename?: 'Mutation', addCategory: { __typename?: 'FeedCategory', id: any, name: string, sortOrder: number } };

export type RenameCategoryMutationVariables = Exact<{
  categoryId: Scalars['UUID']['input'];
  name: Scalars['String']['input'];
}>;


export type RenameCategoryMutation = { __typename?: 'Mutation', renameCategory?: { __typename?: 'FeedCategory', id: any, name: string } | null };

export type DeleteCategoryMutationVariables = Exact<{
  categoryId: Scalars['UUID']['input'];
}>;


export type DeleteCategoryMutation = { __typename?: 'Mutation', deleteCategory: boolean };

export type ReorderCategoriesMutationVariables = Exact<{
  categoryIds: Array<Scalars['UUID']['input']> | Scalars['UUID']['input'];
}>;


export type ReorderCategoriesMutation = { __typename?: 'Mutation', reorderCategories: Array<{ __typename?: 'FeedCategory', id: any, sortOrder: number }> };

export type MoveFeedToCategoryMutationVariables = Exact<{
  feedId: Scalars['UUID']['input'];
  categoryId: Scalars['UUID']['input'];
}>;


export type MoveFeedToCategoryMutation = { __typename?: 'Mutation', moveFeedToCategory?: { __typename?: 'Feed', id: any, categoryId?: any | null } | null };

export type ImportFeedsMutationVariables = Exact<{
  opml: Scalars['String']['input'];
}>;


export type ImportFeedsMutation = { __typename?: 'Mutation', importFeeds: { __typename?: 'ImportResult', feedsAdded: number, feedsSkipped: number, categoriesCreated: number } };

export type UpdateUserPreferencesMutationVariables = Exact<{
  viewMode?: InputMaybe<Scalars['String']['input']>;
  sortOrder?: InputMaybe<Scalars['String']['input']>;
}>;


export type UpdateUserPreferencesMutation = { __typename?: 'Mutation', updateUserPreferences: { __typename?: 'UserPreferences', viewMode: string, sortOrder: string } };

export type SearchQueryVariables = Exact<{
  query: Scalars['String']['input'];
  types?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>> | InputMaybe<Scalars['String']['input']>>;
}>;


export type SearchQuery = { __typename?: 'Query', search: Array<{ __typename?: 'SearchResult', type: string, id: any, title?: string | null, url?: string | null, rank: number }> };

export type GetYoutubeListsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetYoutubeListsQuery = { __typename?: 'Query', youtubeLists: Array<{ __typename?: 'YoutubeListWithStats', totalVideos: number, downloadedVideos: number, removedVideos: number, list: { __typename?: 'YoutubeList', id: any, url: string, name?: string | null } }> };

export type GetVideosQueryVariables = Exact<{
  listId?: InputMaybe<Scalars['UUID']['input']>;
  query?: InputMaybe<Scalars['String']['input']>;
  removedOnly?: InputMaybe<Scalars['Boolean']['input']>;
}>;


export type GetVideosQuery = { __typename?: 'Query', videos: Array<{ __typename?: 'Video', id: any, title?: string | null, youtubeUrl: string, thumbnailPath?: string | null, removedFromYoutube: boolean, downloadedAt?: any | null }> };

export type AddYoutubeListMutationVariables = Exact<{
  url: Scalars['String']['input'];
}>;


export type AddYoutubeListMutation = { __typename?: 'Mutation', addYoutubeList: { __typename?: 'YoutubeListAddResult', newVideos: number, list: { __typename?: 'YoutubeList', id: any, name?: string | null } } };

export type DeleteYoutubeListMutationVariables = Exact<{
  listId: Scalars['UUID']['input'];
}>;


export type DeleteYoutubeListMutation = { __typename?: 'Mutation', deleteYoutubeList?: { __typename?: 'YoutubeList', id: any } | null };

export type RefreshYoutubeListMutationVariables = Exact<{
  listId?: InputMaybe<Scalars['UUID']['input']>;
}>;


export type RefreshYoutubeListMutation = { __typename?: 'Mutation', refreshYoutubeList: Array<{ __typename?: 'SyncResult', listId: any, newVideos: number, downloadSuccesses: number, downloadFailures: number, removedVideos: number }> };

export const GetJobsDocument = gql`
    query GetJobs($type: String, $limit: Int) {
  jobs(type: $type, limit: $limit) {
    id
    type
    status
    startedAt
    completedAt
    errorMessage
    triggeredBy
    metadata
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetJobsGQL extends Apollo.Query<GetJobsQuery, GetJobsQueryVariables> {
    document = GetJobsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetLogsDocument = gql`
    query GetLogs($level: String, $service: String, $limit: Int) {
  logs(level: $level, service: $service, limit: $limit) {
    timestamp
    level
    logger
    message
    thread
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetLogsGQL extends Apollo.Query<GetLogsQuery, GetLogsQueryVariables> {
    document = GetLogsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetAdminStatsDocument = gql`
    query GetAdminStats {
  stats {
    bookmarkCount
    feedCount
    feedItemCount
    unreadFeedItemCount
    youtubeListCount
    videoCount
    downloadedVideoCount
    removedVideoCount
    tagCount
    storageUsedBytes
    lastFeedSync
    lastYoutubeSync
    feedsWithFailures
    youtubeListsWithFailures
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetAdminStatsGQL extends Apollo.Query<GetAdminStatsQuery, GetAdminStatsQueryVariables> {
    document = GetAdminStatsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetBookmarksDocument = gql`
    query GetBookmarks($query: String, $tags: [String]) {
  bookmarks(query: $query, tags: $tags) {
    id
    url
    title
    folderId
    sortOrder
    tags {
      name
    }
    createdAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetBookmarksGQL extends Apollo.Query<GetBookmarksQuery, GetBookmarksQueryVariables> {
    document = GetBookmarksDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const CreateBookmarkDocument = gql`
    mutation CreateBookmark($url: String!, $title: String, $tags: [String], $folderId: UUID) {
  addBookmark(url: $url, title: $title, tags: $tags, folderId: $folderId) {
    id
    url
    title
    folderId
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class CreateBookmarkGQL extends Apollo.Mutation<CreateBookmarkMutation, CreateBookmarkMutationVariables> {
    document = CreateBookmarkDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const DeleteBookmarkDocument = gql`
    mutation DeleteBookmark($id: UUID!) {
  deleteBookmark(id: $id) {
    id
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class DeleteBookmarkGQL extends Apollo.Mutation<DeleteBookmarkMutation, DeleteBookmarkMutationVariables> {
    document = DeleteBookmarkDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetSystemStatsDocument = gql`
    query GetSystemStats {
  stats {
    bookmarkCount
    tagCount
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetSystemStatsGQL extends Apollo.Query<GetSystemStatsQuery, GetSystemStatsQueryVariables> {
    document = GetSystemStatsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetFoldersDocument = gql`
    query GetFolders {
  folders {
    id
    name
    parentId
    bookmarkCount
    sortOrder
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetFoldersGQL extends Apollo.Query<GetFoldersQuery, GetFoldersQueryVariables> {
    document = GetFoldersDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const CreateFolderDocument = gql`
    mutation CreateFolder($name: String!, $parentId: UUID) {
  createFolder(name: $name, parentId: $parentId) {
    id
    name
    parentId
    sortOrder
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class CreateFolderGQL extends Apollo.Mutation<CreateFolderMutation, CreateFolderMutationVariables> {
    document = CreateFolderDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const RenameFolderDocument = gql`
    mutation RenameFolder($id: UUID!, $name: String!) {
  renameFolder(id: $id, name: $name) {
    id
    name
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class RenameFolderGQL extends Apollo.Mutation<RenameFolderMutation, RenameFolderMutationVariables> {
    document = RenameFolderDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MoveFolderDocument = gql`
    mutation MoveFolder($id: UUID!, $newParentId: UUID) {
  moveFolder(id: $id, newParentId: $newParentId) {
    id
    parentId
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MoveFolderGQL extends Apollo.Mutation<MoveFolderMutation, MoveFolderMutationVariables> {
    document = MoveFolderDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const DeleteFolderDocument = gql`
    mutation DeleteFolder($id: UUID!) {
  deleteFolder(id: $id)
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class DeleteFolderGQL extends Apollo.Mutation<DeleteFolderMutation, DeleteFolderMutationVariables> {
    document = DeleteFolderDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MoveBookmarkDocument = gql`
    mutation MoveBookmark($id: UUID!, $folderId: UUID) {
  moveBookmark(id: $id, folderId: $folderId) {
    id
    folderId
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MoveBookmarkGQL extends Apollo.Mutation<MoveBookmarkMutation, MoveBookmarkMutationVariables> {
    document = MoveBookmarkDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const ExportBookmarksDocument = gql`
    query ExportBookmarks {
  exportBookmarks
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class ExportBookmarksGQL extends Apollo.Query<ExportBookmarksQuery, ExportBookmarksQueryVariables> {
    document = ExportBookmarksDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetPendingIngestsDocument = gql`
    query GetPendingIngests {
  pendingIngests {
    previewId
    summary {
      newCount
      unchangedCount
      movedCount
      titleChangedCount
      previouslyDeletedCount
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetPendingIngestsGQL extends Apollo.Query<GetPendingIngestsQuery, GetPendingIngestsQueryVariables> {
    document = GetPendingIngestsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetFeedsDocument = gql`
    query GetFeeds {
  feeds {
    feed {
      id
      url
      title
      siteUrl
      categoryId
    }
    unreadCount
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetFeedsGQL extends Apollo.Query<GetFeedsQuery, GetFeedsQueryVariables> {
    document = GetFeedsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetFeedCategoriesDocument = gql`
    query GetFeedCategories {
  feedCategories {
    category {
      id
      name
      sortOrder
    }
    feeds {
      feed {
        id
        url
        title
        siteUrl
        categoryId
      }
      unreadCount
    }
    totalUnread
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetFeedCategoriesGQL extends Apollo.Query<GetFeedCategoriesQuery, GetFeedCategoriesQueryVariables> {
    document = GetFeedCategoriesDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetFeedItemsDocument = gql`
    query GetFeedItems($feedId: UUID!, $limit: Int, $unreadOnly: Boolean, $sortOrder: String) {
  feedItems(
    feedId: $feedId
    limit: $limit
    unreadOnly: $unreadOnly
    sortOrder: $sortOrder
  ) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetFeedItemsGQL extends Apollo.Query<GetFeedItemsQuery, GetFeedItemsQueryVariables> {
    document = GetFeedItemsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetFeedItemsByCategoryDocument = gql`
    query GetFeedItemsByCategory($categoryId: UUID!, $limit: Int, $unreadOnly: Boolean, $sortOrder: String) {
  feedItemsByCategory(
    categoryId: $categoryId
    limit: $limit
    unreadOnly: $unreadOnly
    sortOrder: $sortOrder
  ) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetFeedItemsByCategoryGQL extends Apollo.Query<GetFeedItemsByCategoryQuery, GetFeedItemsByCategoryQueryVariables> {
    document = GetFeedItemsByCategoryDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetAllFeedItemsDocument = gql`
    query GetAllFeedItems($limit: Int, $unreadOnly: Boolean, $sortOrder: String) {
  feedItemsAll(limit: $limit, unreadOnly: $unreadOnly, sortOrder: $sortOrder) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetAllFeedItemsGQL extends Apollo.Query<GetAllFeedItemsQuery, GetAllFeedItemsQueryVariables> {
    document = GetAllFeedItemsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetUserPreferencesDocument = gql`
    query GetUserPreferences {
  userPreferences {
    viewMode
    sortOrder
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetUserPreferencesGQL extends Apollo.Query<GetUserPreferencesQuery, GetUserPreferencesQueryVariables> {
    document = GetUserPreferencesDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const ExportFeedsDocument = gql`
    query ExportFeeds {
  exportFeeds
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class ExportFeedsGQL extends Apollo.Query<ExportFeedsQuery, ExportFeedsQueryVariables> {
    document = ExportFeedsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MarkItemReadDocument = gql`
    mutation MarkItemRead($itemId: UUID!) {
  markItemRead(itemId: $itemId) {
    id
    readAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MarkItemReadGQL extends Apollo.Mutation<MarkItemReadMutation, MarkItemReadMutationVariables> {
    document = MarkItemReadDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MarkItemUnreadDocument = gql`
    mutation MarkItemUnread($itemId: UUID!) {
  markItemUnread(itemId: $itemId) {
    id
    readAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MarkItemUnreadGQL extends Apollo.Mutation<MarkItemUnreadMutation, MarkItemUnreadMutationVariables> {
    document = MarkItemUnreadDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MarkFeedReadDocument = gql`
    mutation MarkFeedRead($feedId: UUID!) {
  markFeedRead(feedId: $feedId)
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MarkFeedReadGQL extends Apollo.Mutation<MarkFeedReadMutation, MarkFeedReadMutationVariables> {
    document = MarkFeedReadDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MarkCategoryReadDocument = gql`
    mutation MarkCategoryRead($categoryId: UUID!) {
  markCategoryRead(categoryId: $categoryId)
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MarkCategoryReadGQL extends Apollo.Mutation<MarkCategoryReadMutation, MarkCategoryReadMutationVariables> {
    document = MarkCategoryReadDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const AddFeedDocument = gql`
    mutation AddFeed($url: String!, $categoryId: UUID) {
  addFeed(url: $url, categoryId: $categoryId) {
    id
    url
    title
    categoryId
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class AddFeedGQL extends Apollo.Mutation<AddFeedMutation, AddFeedMutationVariables> {
    document = AddFeedDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const DeleteFeedDocument = gql`
    mutation DeleteFeed($feedId: UUID!) {
  deleteFeed(feedId: $feedId) {
    id
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class DeleteFeedGQL extends Apollo.Mutation<DeleteFeedMutation, DeleteFeedMutationVariables> {
    document = DeleteFeedDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const AddCategoryDocument = gql`
    mutation AddCategory($name: String!) {
  addCategory(name: $name) {
    id
    name
    sortOrder
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class AddCategoryGQL extends Apollo.Mutation<AddCategoryMutation, AddCategoryMutationVariables> {
    document = AddCategoryDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const RenameCategoryDocument = gql`
    mutation RenameCategory($categoryId: UUID!, $name: String!) {
  renameCategory(categoryId: $categoryId, name: $name) {
    id
    name
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class RenameCategoryGQL extends Apollo.Mutation<RenameCategoryMutation, RenameCategoryMutationVariables> {
    document = RenameCategoryDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const DeleteCategoryDocument = gql`
    mutation DeleteCategory($categoryId: UUID!) {
  deleteCategory(categoryId: $categoryId)
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class DeleteCategoryGQL extends Apollo.Mutation<DeleteCategoryMutation, DeleteCategoryMutationVariables> {
    document = DeleteCategoryDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const ReorderCategoriesDocument = gql`
    mutation ReorderCategories($categoryIds: [UUID!]!) {
  reorderCategories(categoryIds: $categoryIds) {
    id
    sortOrder
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class ReorderCategoriesGQL extends Apollo.Mutation<ReorderCategoriesMutation, ReorderCategoriesMutationVariables> {
    document = ReorderCategoriesDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MoveFeedToCategoryDocument = gql`
    mutation MoveFeedToCategory($feedId: UUID!, $categoryId: UUID!) {
  moveFeedToCategory(feedId: $feedId, categoryId: $categoryId) {
    id
    categoryId
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MoveFeedToCategoryGQL extends Apollo.Mutation<MoveFeedToCategoryMutation, MoveFeedToCategoryMutationVariables> {
    document = MoveFeedToCategoryDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const ImportFeedsDocument = gql`
    mutation ImportFeeds($opml: String!) {
  importFeeds(opml: $opml) {
    feedsAdded
    feedsSkipped
    categoriesCreated
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class ImportFeedsGQL extends Apollo.Mutation<ImportFeedsMutation, ImportFeedsMutationVariables> {
    document = ImportFeedsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const UpdateUserPreferencesDocument = gql`
    mutation UpdateUserPreferences($viewMode: String, $sortOrder: String) {
  updateUserPreferences(viewMode: $viewMode, sortOrder: $sortOrder) {
    viewMode
    sortOrder
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class UpdateUserPreferencesGQL extends Apollo.Mutation<UpdateUserPreferencesMutation, UpdateUserPreferencesMutationVariables> {
    document = UpdateUserPreferencesDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const SearchDocument = gql`
    query Search($query: String!, $types: [String]) {
  search(query: $query, types: $types) {
    type
    id
    title
    url
    rank
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class SearchGQL extends Apollo.Query<SearchQuery, SearchQueryVariables> {
    document = SearchDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetYoutubeListsDocument = gql`
    query GetYoutubeLists {
  youtubeLists {
    list {
      id
      url
      name
    }
    totalVideos
    downloadedVideos
    removedVideos
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetYoutubeListsGQL extends Apollo.Query<GetYoutubeListsQuery, GetYoutubeListsQueryVariables> {
    document = GetYoutubeListsDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const GetVideosDocument = gql`
    query GetVideos($listId: UUID, $query: String, $removedOnly: Boolean) {
  videos(listId: $listId, query: $query, removedOnly: $removedOnly) {
    id
    title
    youtubeUrl
    thumbnailPath
    removedFromYoutube
    downloadedAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class GetVideosGQL extends Apollo.Query<GetVideosQuery, GetVideosQueryVariables> {
    document = GetVideosDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const AddYoutubeListDocument = gql`
    mutation AddYoutubeList($url: String!) {
  addYoutubeList(url: $url) {
    list {
      id
      name
    }
    newVideos
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class AddYoutubeListGQL extends Apollo.Mutation<AddYoutubeListMutation, AddYoutubeListMutationVariables> {
    document = AddYoutubeListDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const DeleteYoutubeListDocument = gql`
    mutation DeleteYoutubeList($listId: UUID!) {
  deleteYoutubeList(listId: $listId) {
    id
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class DeleteYoutubeListGQL extends Apollo.Mutation<DeleteYoutubeListMutation, DeleteYoutubeListMutationVariables> {
    document = DeleteYoutubeListDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const RefreshYoutubeListDocument = gql`
    mutation RefreshYoutubeList($listId: UUID) {
  refreshYoutubeList(listId: $listId) {
    listId
    newVideos
    downloadSuccesses
    downloadFailures
    removedVideos
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class RefreshYoutubeListGQL extends Apollo.Mutation<RefreshYoutubeListMutation, RefreshYoutubeListMutationVariables> {
    document = RefreshYoutubeListDocument;
    
    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
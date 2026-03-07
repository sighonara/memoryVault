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
  id: Scalars['UUID']['output'];
  tags: Array<Tag>;
  title?: Maybe<Scalars['String']['output']>;
  updatedAt: Scalars['Instant']['output'];
  url: Scalars['String']['output'];
};

export type Feed = {
  __typename?: 'Feed';
  description?: Maybe<Scalars['String']['output']>;
  failureCount: Scalars['Int']['output'];
  id: Scalars['UUID']['output'];
  lastFetchedAt?: Maybe<Scalars['Instant']['output']>;
  siteUrl?: Maybe<Scalars['String']['output']>;
  title?: Maybe<Scalars['String']['output']>;
  url: Scalars['String']['output'];
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
  addFeed: Feed;
  addYoutubeList: YoutubeListAddResult;
  deleteBookmark?: Maybe<Bookmark>;
  deleteFeed?: Maybe<Feed>;
  deleteYoutubeList?: Maybe<YoutubeList>;
  exportBookmarks: Scalars['String']['output'];
  login: LoginResponse;
  markFeedRead: Scalars['Int']['output'];
  markItemRead?: Maybe<FeedItem>;
  markItemUnread?: Maybe<FeedItem>;
  refreshFeed: Array<FeedRefreshResult>;
  refreshYoutubeList: Array<SyncResult>;
  tagBookmark?: Maybe<Bookmark>;
};


export type MutationAddBookmarkArgs = {
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  title?: InputMaybe<Scalars['String']['input']>;
  url: Scalars['String']['input'];
};


export type MutationAddFeedArgs = {
  url: Scalars['String']['input'];
};


export type MutationAddYoutubeListArgs = {
  url: Scalars['String']['input'];
};


export type MutationDeleteBookmarkArgs = {
  id: Scalars['UUID']['input'];
};


export type MutationDeleteFeedArgs = {
  feedId: Scalars['UUID']['input'];
};


export type MutationDeleteYoutubeListArgs = {
  listId: Scalars['UUID']['input'];
};


export type MutationLoginArgs = {
  email: Scalars['String']['input'];
  password: Scalars['String']['input'];
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


export type MutationRefreshFeedArgs = {
  feedId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationRefreshYoutubeListArgs = {
  listId?: InputMaybe<Scalars['UUID']['input']>;
};


export type MutationTagBookmarkArgs = {
  id: Scalars['UUID']['input'];
  tags: Array<Scalars['String']['input']>;
};

export type Query = {
  __typename?: 'Query';
  bookmarks: Array<Bookmark>;
  feedItems: Array<FeedItem>;
  feeds: Array<FeedWithUnread>;
  jobs: Array<SyncJob>;
  logs: Array<LogEntry>;
  search: Array<SearchResult>;
  stats: SystemStats;
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
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
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

export type GetBookmarksQueryVariables = Exact<{
  query?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>> | InputMaybe<Scalars['String']['input']>>;
}>;


export type GetBookmarksQuery = { __typename?: 'Query', bookmarks: Array<{ __typename?: 'Bookmark', id: any, url: string, title?: string | null, createdAt: any, tags: Array<{ __typename?: 'Tag', name: string }> }> };

export type CreateBookmarkMutationVariables = Exact<{
  url: Scalars['String']['input'];
  title?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>> | InputMaybe<Scalars['String']['input']>>;
}>;


export type CreateBookmarkMutation = { __typename?: 'Mutation', addBookmark: { __typename?: 'Bookmark', id: any, url: string, title?: string | null } };

export type DeleteBookmarkMutationVariables = Exact<{
  id: Scalars['UUID']['input'];
}>;


export type DeleteBookmarkMutation = { __typename?: 'Mutation', deleteBookmark?: { __typename?: 'Bookmark', id: any } | null };

export type GetSystemStatsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetSystemStatsQuery = { __typename?: 'Query', stats: { __typename?: 'SystemStats', bookmarkCount: number, tagCount: number } };

export type GetFeedsQueryVariables = Exact<{ [key: string]: never; }>;


export type GetFeedsQuery = { __typename?: 'Query', feeds: Array<{ __typename?: 'FeedWithUnread', unreadCount: number, feed: { __typename?: 'Feed', id: any, url: string, title?: string | null, siteUrl?: string | null } }> };

export type GetFeedItemsQueryVariables = Exact<{
  feedId: Scalars['UUID']['input'];
  limit?: InputMaybe<Scalars['Int']['input']>;
  unreadOnly?: InputMaybe<Scalars['Boolean']['input']>;
}>;


export type GetFeedItemsQuery = { __typename?: 'Query', feedItems: Array<{ __typename?: 'FeedItem', id: any, feedId: any, title?: string | null, url?: string | null, content?: string | null, author?: string | null, publishedAt?: any | null, readAt?: any | null }> };

export type MarkItemReadMutationVariables = Exact<{
  itemId: Scalars['UUID']['input'];
}>;


export type MarkItemReadMutation = { __typename?: 'Mutation', markItemRead?: { __typename?: 'FeedItem', id: any, readAt?: any | null } | null };

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

export const GetBookmarksDocument = gql`
    query GetBookmarks($query: String, $tags: [String]) {
  bookmarks(query: $query, tags: $tags) {
    id
    url
    title
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
    mutation CreateBookmark($url: String!, $title: String, $tags: [String]) {
  addBookmark(url: $url, title: $title, tags: $tags) {
    id
    url
    title
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
export const GetFeedsDocument = gql`
    query GetFeeds {
  feeds {
    feed {
      id
      url
      title
      siteUrl
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
export const GetFeedItemsDocument = gql`
    query GetFeedItems($feedId: UUID!, $limit: Int, $unreadOnly: Boolean) {
  feedItems(feedId: $feedId, limit: $limit, unreadOnly: $unreadOnly) {
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
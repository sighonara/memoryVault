import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CUSTOM_ELEMENTS_SCHEMA, DestroyRef, signal } from '@angular/core';
import { of } from 'rxjs';
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { BookmarksComponent } from './bookmarks';
import { BookmarksStore } from './bookmarks.store';

describe('BookmarksComponent', () => {
  let component: BookmarksComponent;
  let fixture: ComponentFixture<BookmarksComponent>;

  const mockStore = {
    bookmarks: signal([] as any[]),
    folders: signal([] as any[]),
    selectedFolderId: signal(null as string | null),
    filteredBookmarks: signal([] as any[]),
    loading: signal(false),
    searchQuery: signal(''),
    selectedTags: signal([] as string[]),
    ingestPreview: signal(null),
    ingestLoading: signal(false),
    loadBookmarks: vi.fn(),
    loadFolders: vi.fn(),
    selectFolder: vi.fn(),
    setSearchQuery: vi.fn(),
    toggleTag: vi.fn(),
    addBookmark: vi.fn(),
    deleteBookmark: vi.fn(),
    createFolder: vi.fn(),
    renameFolder: vi.fn(),
    moveFolder: vi.fn(),
    deleteFolder: vi.fn(),
    moveBookmark: vi.fn(),
    fetchIngestPreview: vi.fn(),
    commitIngest: vi.fn(),
    exportBookmarks: vi.fn(),
  };

  const mockDialog = {
    open: vi.fn(),
  };

  const mockSnackBar = {
    open: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [BookmarksComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
      ],
    })
      .overrideComponent(BookmarksComponent, {
        set: {
          imports: [
            MatDialogModule,
            MatButtonModule,
            MatIconModule,
            MatChipsModule,
            MatProgressSpinnerModule,
            MatToolbarModule,
            MatSnackBarModule,
          ],
          schemas: [CUSTOM_ELEMENTS_SCHEMA],
          providers: [
            { provide: BookmarksStore, useValue: mockStore },
            { provide: MatDialog, useValue: mockDialog },
            { provide: MatSnackBar, useValue: mockSnackBar },
          ],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(BookmarksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call loadBookmarks and loadFolders on init', () => {
    expect(mockStore.loadBookmarks).toHaveBeenCalled();
    expect(mockStore.loadFolders).toHaveBeenCalled();
  });

  it('should delegate search to store', () => {
    const event = { target: { value: 'test query' } };
    component.onSearch(event);
    expect(mockStore.setSearchQuery).toHaveBeenCalledWith('test query');
  });

  it('should delegate toggleTag to store', () => {
    component.toggleTag('kotlin');
    expect(mockStore.toggleTag).toHaveBeenCalledWith('kotlin');
  });

  it('should return empty tags when no bookmarks exist', () => {
    expect(component.getAllTags()).toEqual([]);
  });

  it('should collect and sort tags from bookmarks', () => {
    mockStore.bookmarks.set([
      { id: '1', url: 'https://a.com', tags: [{ name: 'z-tag' }, { name: 'a-tag' }] },
      { id: '2', url: 'https://b.com', tags: [{ name: 'a-tag' }, { name: 'm-tag' }] },
    ]);
    expect(component.getAllTags()).toEqual(['a-tag', 'm-tag', 'z-tag']);
  });

  it('should open add dialog and add bookmark on close', () => {
    const dialogRef = { afterClosed: () => of({ url: 'https://new.com', title: 'New' }) };
    mockDialog.open.mockReturnValue(dialogRef);

    component.openAddDialog();

    expect(mockDialog.open).toHaveBeenCalled();
    expect(mockStore.addBookmark).toHaveBeenCalledWith({ url: 'https://new.com', title: 'New' });
  });

  it('should not add bookmark when dialog is cancelled', () => {
    const dialogRef = { afterClosed: () => of(undefined) };
    mockDialog.open.mockReturnValue(dialogRef);

    component.openAddDialog();

    expect(mockStore.addBookmark).not.toHaveBeenCalled();
  });

  it('should render tree and list panels when not loading', () => {
    mockStore.loading.set(false);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.bookmark-manager')).toBeTruthy();
    expect(el.querySelector('app-bookmark-tree')).toBeTruthy();
    expect(el.querySelector('app-bookmark-list')).toBeTruthy();
  });

  it('should render ingest panel', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-ingest-panel')).toBeTruthy();
  });

  it('should handle folder context actions', () => {
    vi.spyOn(window, 'prompt').mockReturnValue('TestFolder');
    component.onFolderAction({ action: 'new', folderId: null });
    expect(mockStore.createFolder).toHaveBeenCalledWith({ name: 'TestFolder', parentId: undefined });
  });
});

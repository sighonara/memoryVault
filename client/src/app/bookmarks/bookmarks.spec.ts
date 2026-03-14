import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { DestroyRef, signal } from '@angular/core';
import { of } from 'rxjs';
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

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [BookmarksComponent],
      providers: [provideNoopAnimations()],
    })
      .overrideComponent(BookmarksComponent, {
        set: {
          providers: [
            { provide: BookmarksStore, useValue: mockStore },
            { provide: MatDialog, useValue: mockDialog },
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

  it('should call loadBookmarks on init', () => {
    expect(mockStore.loadBookmarks).toHaveBeenCalled();
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

  it('should show empty state when no bookmarks and not loading', () => {
    mockStore.bookmarks.set([]);
    mockStore.loading.set(false);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state')).toBeTruthy();
  });

  it('should render bookmark rows', () => {
    mockStore.bookmarks.set([
      { id: '1', url: 'https://example.com', title: 'Example', tags: [] },
    ]);
    mockStore.loading.set(false);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.bookmark-row')).toBeTruthy();
    expect(el.querySelector('.bookmark-title')?.textContent).toContain('Example');
  });
});

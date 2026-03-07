import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BookmarksStore } from './bookmarks.store';
import { BookmarkDialogComponent } from './bookmark-dialog';

@Component({
  selector: 'app-bookmarks',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
  ],
  providers: [BookmarksStore],
  template: `
    <div style="padding: 20px;">
      <h1>Bookmarks</h1>
      <p>Bookmarks implementation in progress.</p>
    </div>
  `
})
export class BookmarksComponent implements OnInit {
  readonly store = inject(BookmarksStore);
  private dialog = inject(MatDialog);

  ngOnInit() {
    this.store.loadBookmarks();
  }

  onSearch(event: any) {
    this.store.setSearchQuery((event.target as HTMLInputElement).value);
  }

  toggleTag(tag: string) {
    this.store.toggleTag(tag);
  }

  openAddDialog() {
    const dialogRef = this.dialog.open(BookmarkDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.addBookmark(result);
      }
    });
  }

  deleteBookmark(id: string) {
    if (confirm('Are you sure you want to delete this bookmark?')) {
      this.store.deleteBookmark(id);
    }
  }

  getAllTags(): string[] {
    const tagsSet = new Set<string>();
    this.store.bookmarks().forEach(b => b.tags.forEach(t => tagsSet.add(t.name)));
    return Array.from(tagsSet).sort();
  }
}

import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatBadgeModule } from '@angular/material/badge';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ReaderStore } from '../reader.store';

@Component({
  selector: 'app-category-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatListModule,
    MatExpansionModule,
    MatBadgeModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatToolbarModule,
    MatProgressSpinnerModule,
    DragDropModule,
  ],
  templateUrl: './category-sidebar.html',
  styleUrl: './category-sidebar.css',
})
export class CategorySidebarComponent {
  readonly store = inject(ReaderStore);
  private readonly dialog = inject(MatDialog);

  onCategoryDrop(event: CdkDragDrop<any[]>): void {
    const categories = [...this.store.categories()];
    moveItemInArray(categories, event.previousIndex, event.currentIndex);
    const categoryIds = categories.map((c: any) => c.category.id);
    this.store.reorderCategories(categoryIds);
  }

  async openAddFeedDialog(categoryId?: string): Promise<void> {
    const { AddFeedDialogComponent } = await import('../feed-management');
    const dialogRef = this.dialog.open(AddFeedDialogComponent, {
      width: '400px',
      data: { categories: this.store.categories(), defaultCategoryId: categoryId },
    });
    dialogRef.afterClosed().subscribe((result: any) => {
      if (result) this.store.addFeed(result.url, result.categoryId);
    });
  }

  async openAddCategoryDialog(): Promise<void> {
    const { AddCategoryDialogComponent } = await import('../feed-management');
    const dialogRef = this.dialog.open(AddCategoryDialogComponent, { width: '300px' });
    dialogRef.afterClosed().subscribe((name: string) => {
      if (name) this.store.addCategory(name);
    });
  }

  async openRenameCategoryDialog(categoryId: string, currentName: string): Promise<void> {
    const { RenameCategoryDialogComponent } = await import('../feed-management');
    const dialogRef = this.dialog.open(RenameCategoryDialogComponent, {
      width: '300px',
      data: { currentName },
    });
    dialogRef.afterClosed().subscribe((name: string) => {
      if (name) this.store.renameCategory(categoryId, name);
    });
  }

  async openDeleteCategoryDialog(categoryId: string, categoryName: string): Promise<void> {
    const { DeleteConfirmDialogComponent } = await import('../feed-management');
    const dialogRef = this.dialog.open(DeleteConfirmDialogComponent, {
      width: '300px',
      data: { entityName: `category "${categoryName}"` },
    });
    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) this.store.deleteCategory(categoryId);
    });
  }

  async openMoveFeedDialog(feedId: string): Promise<void> {
    const { MoveFeedDialogComponent } = await import('../feed-management');
    const dialogRef = this.dialog.open(MoveFeedDialogComponent, {
      width: '300px',
      data: { categories: this.store.categories() },
    });
    dialogRef.afterClosed().subscribe((categoryId: string) => {
      if (categoryId) this.store.moveFeedToCategory(feedId, categoryId);
    });
  }

  async openDeleteFeedDialog(feedId: string, feedTitle: string): Promise<void> {
    const { DeleteConfirmDialogComponent } = await import('../feed-management');
    const dialogRef = this.dialog.open(DeleteConfirmDialogComponent, {
      width: '300px',
      data: { entityName: `feed "${feedTitle}"` },
    });
    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) this.store.deleteFeed(feedId);
    });
  }
}

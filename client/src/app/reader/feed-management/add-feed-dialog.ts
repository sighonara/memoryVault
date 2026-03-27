import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-add-feed-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Add Feed</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Feed URL</mat-label>
        <input matInput [(ngModel)]="url" placeholder="https://example.com/rss" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Category</mat-label>
        <mat-select [(ngModel)]="categoryId">
          @for (cat of data.categories; track cat.category.id) {
            <mat-option [value]="cat.category.id">{{ cat.category.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!url" (click)="submit()">Add</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; }`],
})
export class AddFeedDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<AddFeedDialogComponent>);
  readonly data: any = inject(MAT_DIALOG_DATA);
  url = '';
  categoryId: string = this.data.defaultCategoryId || this.data.categories?.[0]?.category?.id || '';

  submit(): void {
    this.dialogRef.close({ url: this.url, categoryId: this.categoryId });
  }
}

import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

export interface BookmarkDialogData {
  folders: Array<{ id: string; name: string }>;
  currentFolderId: string | null;
}

@Component({
  selector: 'app-bookmark-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title>Add Bookmark</h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>URL</mat-label>
          <input matInput formControlName="url" placeholder="https://example.com" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Title (Optional)</mat-label>
          <input matInput formControlName="title" placeholder="My Cool Link" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Tags (Comma separated)</mat-label>
          <input matInput formControlName="tags" placeholder="tech, news, code" />
        </mat-form-field>
        @if (data?.folders?.length) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Folder</mat-label>
            <mat-select formControlName="folderId">
              <mat-option [value]="null">No folder</mat-option>
              @for (folder of data!.folders; track folder.id) {
                <mat-option [value]="folder.id">{{ folder.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="submit()">Add</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; margin-bottom: 8px; }
  `]
})
export class BookmarkDialogComponent {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<BookmarkDialogComponent>);
  data = inject<BookmarkDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  form = this.fb.group({
    url: ['', [Validators.required, Validators.pattern('https?://.+')]],
    title: [''],
    tags: [''],
    folderId: [this.data?.currentFolderId ?? null],
  });

  submit() {
    if (this.form.valid) {
      const val = this.form.value;
      const tags = val.tags ? val.tags.split(',').map(t => t.trim()).filter(t => !!t) : [];
      this.dialogRef.close({
        url: val.url,
        title: val.title,
        tags,
        folderId: val.folderId || undefined,
      });
    }
  }
}

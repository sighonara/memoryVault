import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-bookmark-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatChipsModule,
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

  form = this.fb.group({
    url: ['', [Validators.required, Validators.pattern('https?://.+')]],
    title: [''],
    tags: [''],
  });

  submit() {
    if (this.form.valid) {
      const val = this.form.value;
      const tags = val.tags ? val.tags.split(',').map(t => t.trim()).filter(t => !!t) : [];
      this.dialogRef.close({ url: val.url, title: val.title, tags });
    }
  }
}

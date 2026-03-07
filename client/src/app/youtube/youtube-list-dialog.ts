import { Component, inject } from '@angular/core';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-youtube-list-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Add YouTube List</h2>
    <mat-dialog-content>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Playlist URL</mat-label>
          <input matInput formControlName="url" placeholder="https://www.youtube.com/playlist?list=..." />
          <mat-hint>Can be a playlist or a channel URL</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" [disabled]="form.invalid" (click)="submit()">Add</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; margin-bottom: 8px; margin-top: 8px; }
  `]
})
export class YoutubeListDialogComponent {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<YoutubeListDialogComponent>);

  form = this.fb.group({
    url: ['', [Validators.required, Validators.pattern('https?://.+')]],
  });

  submit() {
    if (this.form.valid) {
      this.dialogRef.close({ url: this.form.value.url });
    }
  }
}

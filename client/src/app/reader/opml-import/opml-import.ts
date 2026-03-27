import { Component, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReaderStore } from '../reader.store';

@Component({
  selector: 'app-opml-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './opml-import.html',
  styleUrl: './opml-import.css',
})
export class OpmlImportComponent {
  private readonly dialogRef = inject(MatDialogRef<OpmlImportComponent>);
  private readonly store = inject(ReaderStore);

  readonly fileName = signal('');
  readonly importing = signal(false);
  readonly result = signal<any>(null);
  private fileContent = '';

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.fileName.set(file.name);
    const reader = new FileReader();
    reader.onload = () => {
      this.fileContent = reader.result as string;
    };
    reader.readAsText(file);
  }

  importFile(): void {
    if (!this.fileContent) return;
    this.importing.set(true);
    this.store.importFeeds(this.fileContent, (result: any) => {
      this.importing.set(false);
      this.result.set(result);
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}

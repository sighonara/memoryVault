import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../auth/auth.service';
import { BookmarksStore } from '../bookmarks.store';

export function detectBrowser(userAgent: string): 'chrome' | 'firefox' | 'safari' {
  if (userAgent.includes('Firefox')) return 'firefox';
  if (userAgent.includes('Chrome') && !userAgent.includes('Edg')) return 'chrome';
  if (userAgent.includes('Safari') && !userAgent.includes('Chrome')) return 'safari';
  return 'chrome';
}

export function detectOS(userAgent: string): 'macos' | 'windows' | 'linux' {
  if (userAgent.includes('Macintosh') || userAgent.includes('Mac OS')) return 'macos';
  if (userAgent.includes('Windows')) return 'windows';
  return 'linux';
}

const CHROME_PATHS: Record<string, string> = {
  macos: '~/Library/Application\\ Support/Google/Chrome/Default/Bookmarks',
  windows: '%LOCALAPPDATA%\\Google\\Chrome\\User\\ Data\\Default\\Bookmarks',
  linux: '~/.config/google-chrome/Default/Bookmarks',
};

const FIREFOX_PATHS: Record<string, string> = {
  macos: '~/Library/Application\\ Support/Firefox/Profiles/*.default-release/places.sqlite',
  windows: '%APPDATA%\\Mozilla\\Firefox\\Profiles\\*.default-release\\places.sqlite',
  linux: '~/.mozilla/firefox/*.default-release/places.sqlite',
};

function responseParserLines(apiUrl: string): string[] {
  return [
    `  -d @- | python3 -c "`,
    `import json, sys`,
    `r = json.load(sys.stdin)`,
    `s = r.get('summary', {})`,
    `print()`,
    `print('Preview created: %d new, %d unchanged, %d moved, %d title changed, %d previously deleted' % (`,
    `    s.get('newCount', 0), s.get('unchangedCount', 0), s.get('movedCount', 0),`,
    `    s.get('titleChangedCount', 0), s.get('previouslyDeletedCount', 0)))`,
    `print('Review and commit at: ${apiUrl}/bookmarks?ingest=' + r.get('previewId', ''))`,
    `"`,
  ];
}

export function generateIngestCommand(
  browser: string,
  os: string,
  token: string,
  apiUrl: string,
): string {
  if (browser === 'safari') {
    return [
      `plutil -convert json -o /tmp/safari-bookmarks.json ~/Library/Safari/Bookmarks.plist`,
      '',
      `# Extract and send to MemoryVault`,
      `cat /tmp/safari-bookmarks.json | python3 -c "`,
      `import json, sys`,
      `data = json.load(sys.stdin)`,
      `def walk(node, folder=''):`,
      `    items = []`,
      `    for child in node.get('Children', []):`,
      `        t = child.get('WebBookmarkType', '')`,
      `        if t == 'WebBookmarkTypeLeaf':`,
      `            items.append({'url': child['URLString'], 'title': child.get('URIDictionary',{}).get('title',''), 'browserFolder': folder or None})`,
      `        elif t == 'WebBookmarkTypeList':`,
      `            items += walk(child, child.get('Title', folder))`,
      `    return items`,
      `bookmarks = walk(data)`,
      `print(json.dumps({'bookmarks': bookmarks}))`,
      `" | curl -s -X POST ${apiUrl}/api/bookmarks/ingest \\`,
      `  -H "Authorization: Bearer ${token}" \\`,
      `  -H "Content-Type: application/json" \\`,
      ...responseParserLines(apiUrl),
    ].join('\n');
  }

  if (browser === 'firefox') {
    const dbPath = FIREFOX_PATHS[os] || FIREFOX_PATHS['linux'];
    return [
      'if ! command -v sqlite3 &>/dev/null; then',
      '  echo "sqlite3 not found. Use Firefox: Bookmarks > Manage Bookmarks > Import/Export > Export to HTML"',
      '  echo "Then use the Safari/HTML import method instead."',
      '  exit 1',
      'fi',
      '',
      `DB=$(ls ${dbPath} 2>/dev/null | head -1)`,
      `cp "$DB" /tmp/places-copy.sqlite`,
      '',
      `sqlite3 /tmp/places-copy.sqlite "`,
      `  SELECT json_group_array(json_object(`,
      `    'url', p.url,`,
      `    'title', COALESCE(b.title, p.title, ''),`,
      `    'browserFolder', (SELECT pp.title FROM moz_bookmarks pb JOIN moz_bookmarks pp ON pb.parent = pp.id WHERE pb.fk = b.fk AND pp.type = 2 LIMIT 1)`,
      `  ))`,
      `  FROM moz_bookmarks b`,
      `  JOIN moz_places p ON b.fk = p.id`,
      `  WHERE b.type = 1 AND p.url NOT LIKE 'place:%';`,
      `" | python3 -c "`,
      `import json, sys`,
      `rows = json.loads(sys.stdin.read())`,
      `print(json.dumps({'bookmarks': rows}))`,
      `" | curl -s -X POST ${apiUrl}/api/bookmarks/ingest \\`,
      `  -H "Authorization: Bearer ${token}" \\`,
      `  -H "Content-Type: application/json" \\`,
      ...responseParserLines(apiUrl),
    ].join('\n');
  }

  // Chrome / Chromium
  const bookmarksPath = CHROME_PATHS[os] || CHROME_PATHS['linux'];
  return [
    `cat ${bookmarksPath} | python3 -c "`,
    `import json, sys`,
    `data = json.load(sys.stdin)`,
    `def walk(node, folder=''):`,
    `    items = []`,
    `    for child in node.get('children', []):`,
    `        if child['type'] == 'url':`,
    `            items.append({'url': child['url'], 'title': child.get('name',''), 'browserFolder': folder or None})`,
    `        elif child['type'] == 'folder':`,
    `            items += walk(child, child.get('name', folder))`,
    `    return items`,
    `roots = data['roots']`,
    `bookmarks = walk(roots['bookmark_bar']) + walk(roots.get('other', {})) + walk(roots.get('synced', {}))`,
    `print(json.dumps({'bookmarks': bookmarks}))`,
    `" | curl -s -X POST ${apiUrl}/api/bookmarks/ingest \\`,
    `  -H "Authorization: Bearer ${token}" \\`,
    `  -H "Content-Type: application/json" \\`,
    ...responseParserLines(apiUrl),
  ].join('\n');
}

@Component({
  selector: 'app-ingest-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIconModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatExpansionModule,
    MatTooltipModule,
  ],
  template: `
    <mat-accordion>
      <mat-expansion-panel>
        <mat-expansion-panel-header>
          <mat-panel-title>
            <mat-icon>swap_horiz</mat-icon>
            Import / Export
          </mat-panel-title>
        </mat-expansion-panel-header>

        <div class="ingest-columns">
          <div class="section import-section">
            <div class="section-header">
              <mat-icon>cloud_upload</mat-icon>
              <h3>Import from Browser</h3>
            </div>
            <p class="hint">Run a terminal command to send your browser bookmarks to MemoryVault.</p>

            <div class="controls">
              <mat-form-field appearance="outline" class="browser-select">
                <mat-label>Browser</mat-label>
                <mat-select [value]="browser()" (selectionChange)="browser.set($event.value)">
                  <mat-option value="chrome">Chrome</mat-option>
                  <mat-option value="firefox">Firefox</mat-option>
                  <mat-option value="safari">Safari</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline" class="os-select">
                <mat-label>OS</mat-label>
                <mat-select [value]="os()" (selectionChange)="os.set($event.value)">
                  <mat-option value="macos">macOS</mat-option>
                  <mat-option value="windows">Windows</mat-option>
                  <mat-option value="linux">Linux</mat-option>
                </mat-select>
              </mat-form-field>

              <button mat-stroked-button class="copy-command-btn" (click)="copyCommand()" matTooltip="Copy import command to clipboard">
                <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>
                {{ copied() ? 'Copied!' : 'Copy Command' }}
              </button>
            </div>

            <mat-expansion-panel class="code-panel">
              <mat-expansion-panel-header>
                <mat-panel-title>View command</mat-panel-title>
              </mat-expansion-panel-header>
              <div class="code-block">
                <pre>{{ command() }}</pre>
              </div>
            </mat-expansion-panel>

            <div class="review-section">
              <mat-form-field appearance="outline" class="preview-id-field">
                <mat-label>Preview ID</mat-label>
                <input matInput (input)="previewId.set($any($event.target).value)"
                       placeholder="Paste from CLI output" />
              </mat-form-field>
              <button mat-stroked-button [disabled]="!previewId()" (click)="reviewImport()"
                      matTooltip="Open the review dialog for this import">
                <mat-icon>rate_review</mat-icon> Review
              </button>
            </div>
          </div>

          <div class="column-divider"></div>

          <div class="section export-section">
            <div class="section-header">
              <mat-icon>cloud_download</mat-icon>
              <h3>Export Bookmarks</h3>
            </div>
            <p class="hint">Download as a Netscape HTML file compatible with all browsers.</p>

            <button mat-stroked-button (click)="store.exportBookmarks()" class="export-btn">
              <mat-icon>download</mat-icon> Download HTML
            </button>

            <mat-expansion-panel class="help-panel">
              <mat-expansion-panel-header>
                <mat-panel-title>How to import into your browser</mat-panel-title>
              </mat-expansion-panel-header>
              <ul class="import-instructions">
                <li><strong>Chrome:</strong> Settings &gt; Bookmarks &gt; Import bookmarks and settings &gt; HTML file</li>
                <li><strong>Firefox:</strong> Bookmarks &gt; Manage Bookmarks &gt; Import/Export &gt; Import from HTML</li>
                <li><strong>Safari:</strong> File &gt; Import From &gt; Bookmarks HTML File</li>
              </ul>
            </mat-expansion-panel>
          </div>
        </div>
      </mat-expansion-panel>
    </mat-accordion>
  `,
  styles: [`
    mat-expansion-panel-header mat-icon { margin-right: 8px; }

    .ingest-columns {
      display: flex; gap: 0; padding: 8px 0;
    }
    .section { flex: 1; min-width: 0; }
    .section-header {
      display: flex; align-items: center; gap: 8px; margin-bottom: 4px;
    }
    .section-header mat-icon { color: #5f6368; font-size: 20px; width: 20px; height: 20px; }
    .section-header h3 { font-size: 0.875rem; font-weight: 500; margin: 0; }
    .hint { font-size: 0.75rem; color: #5f6368; margin: 0 0 12px; }

    .controls { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 8px; }
    .browser-select, .os-select { width: 130px; }
    .copy-command-btn { white-space: nowrap; }

    .review-section {
      display: flex; gap: 12px; align-items: center; margin-top: 12px;
      padding-top: 12px; border-top: 1px solid #e8eaed;
    }
    .preview-id-field { flex: 1; }

    .code-panel { margin-top: 4px; }
    .code-block {
      background: #1e1e1e; border-radius: 4px;
      padding: 12px 16px; overflow-x: auto;
    }
    .code-block pre {
      margin: 0; font-size: 0.7rem; color: #d4d4d4; line-height: 1.5;
      font-family: 'SF Mono', Monaco, Consolas, monospace;
      white-space: pre-wrap; word-break: break-all;
    }

    .column-divider {
      width: 1px; background: #e8eaed; margin: 0 24px; flex-shrink: 0;
    }

    .export-btn { margin-bottom: 12px; }
    .help-panel { margin-top: 4px; }
    .import-instructions { font-size: 0.8125rem; padding-left: 20px; margin: 0; }
    .import-instructions li { margin-bottom: 6px; }

    @media (max-width: 768px) {
      .ingest-columns { flex-direction: column; }
      .column-divider { width: auto; height: 1px; margin: 16px 0; }
    }
  `]
})
export class IngestPanelComponent {
  readonly store = inject(BookmarksStore);
  private authService = inject(AuthService);
  private router = inject(Router);

  browser = signal(detectBrowser(navigator.userAgent));
  os = signal(detectOS(navigator.userAgent));
  copied = signal(false);
  previewId = signal('');

  command = computed(() =>
    generateIngestCommand(
      this.browser(),
      this.os(),
      this.authService.getToken() ?? '<your-token>',
      window.location.origin,
    )
  );

  async copyCommand() {
    await navigator.clipboard.writeText(this.command());
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 2000);
  }

  reviewImport() {
    const id = this.previewId().trim();
    if (id) {
      this.router.navigate(['/bookmarks'], { queryParams: { ingest: id } });
    }
  }
}

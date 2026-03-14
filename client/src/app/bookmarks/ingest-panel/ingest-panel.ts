import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
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

export function generateIngestCommand(
  browser: string,
  os: string,
  token: string,
  apiUrl: string,
): string {
  if (browser === 'safari') {
    return [
      '# Safari bookmarks (macOS only)',
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
      `  -d @-`,
    ].join('\n');
  }

  if (browser === 'firefox') {
    const dbPath = FIREFOX_PATHS[os] || FIREFOX_PATHS['linux'];
    return [
      '# Firefox bookmarks',
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
      `  -d @-`,
    ].join('\n');
  }

  // Chrome / Chromium
  const bookmarksPath = CHROME_PATHS[os] || CHROME_PATHS['linux'];
  return [
    '# Chrome/Chromium bookmarks',
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
    `  -d @-`,
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

        <div class="ingest-content">
          <div class="section">
            <h3>Import from Browser</h3>
            <p class="hint">Run this command in your terminal to send bookmarks to MemoryVault:</p>

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
            </div>

            <div class="code-block">
              <button mat-icon-button class="copy-btn" (click)="copyCommand()" matTooltip="Copy to clipboard">
                <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>
              </button>
              <pre>{{ command() }}</pre>
            </div>
          </div>

          <div class="divider"></div>

          <div class="section">
            <h3>Export Bookmarks</h3>
            <p class="hint">Download your bookmarks as a Netscape HTML file for import into any browser.</p>
            <button mat-stroked-button (click)="store.exportBookmarks()">
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
    .ingest-content { padding: 8px 0; }
    .section h3 { font-size: 0.875rem; font-weight: 500; margin: 0 0 4px; }
    .hint { font-size: 0.75rem; color: #5f6368; margin: 0 0 12px; }
    .controls { display: flex; gap: 12px; margin-bottom: 12px; }
    .browser-select, .os-select { width: 140px; }
    .code-block {
      position: relative; background: #1e1e1e; border-radius: 6px;
      padding: 12px 16px; overflow-x: auto;
    }
    .code-block pre {
      margin: 0; font-size: 0.75rem; color: #d4d4d4;
      font-family: 'SF Mono', Monaco, Consolas, monospace;
      white-space: pre-wrap; word-break: break-all;
    }
    .copy-btn {
      position: absolute; top: 4px; right: 4px; color: #999;
    }
    .divider { border-top: 1px solid #e8eaed; margin: 16px 0; }
    .help-panel { margin-top: 12px; }
    .import-instructions { font-size: 0.8125rem; padding-left: 20px; }
    .import-instructions li { margin-bottom: 6px; }
  `]
})
export class IngestPanelComponent {
  readonly store = inject(BookmarksStore);
  private authService = inject(AuthService);

  browser = signal(detectBrowser(navigator.userAgent));
  os = signal(detectOS(navigator.userAgent));
  copied = signal(false);

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
}

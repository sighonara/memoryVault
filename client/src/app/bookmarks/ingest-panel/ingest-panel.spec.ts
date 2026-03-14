import { describe, it, expect } from 'vitest';
import { generateIngestCommand, detectBrowser, detectOS } from './ingest-panel';

describe('IngestPanel command generation', () => {
  it('generates Chrome macOS command with token and URL', () => {
    const command = generateIngestCommand('chrome', 'macos', 'eyJhbGciOiJIUzI1NiJ9.test', 'http://localhost:8080');
    expect(command).toContain('curl');
    expect(command).toContain('eyJhbGciOiJIUzI1NiJ9.test');
    expect(command).toContain('http://localhost:8080');
    expect(command).toContain('Google/Chrome/Default/Bookmarks');
  });

  it('generates Chrome Windows command', () => {
    const command = generateIngestCommand('chrome', 'windows', 'token', 'http://localhost:8080');
    expect(command).toContain('LOCALAPPDATA');
    expect(command).toContain('Google\\Chrome');
  });

  it('generates Chrome Linux command', () => {
    const command = generateIngestCommand('chrome', 'linux', 'token', 'http://localhost:8080');
    expect(command).toContain('.config/google-chrome');
  });

  it('generates Firefox macOS command with sqlite3', () => {
    const command = generateIngestCommand('firefox', 'macos', 'token', 'http://localhost:8080');
    expect(command).toContain('sqlite3');
    expect(command).toContain('places.sqlite');
  });

  it('Firefox command includes sqlite3 availability check', () => {
    const command = generateIngestCommand('firefox', 'macos', 'token', 'http://localhost:8080');
    expect(command).toContain('command -v sqlite3');
  });

  it('generates Safari command with plutil', () => {
    const command = generateIngestCommand('safari', 'macos', 'token', 'http://localhost:8080');
    expect(command).toContain('plutil');
    expect(command).toContain('Bookmarks.plist');
  });

  it('all commands include response parser with review URL', () => {
    for (const browser of ['chrome', 'firefox', 'safari'] as const) {
      const command = generateIngestCommand(browser, 'macos', 'token', 'http://localhost:4200');
      expect(command).toContain('Preview created:');
      expect(command).toContain('Review and commit at: http://localhost:4200/bookmarks?ingest=');
    }
  });

  it('detects Chrome from user agent', () => {
    expect(detectBrowser('Mozilla/5.0 ... Chrome/120.0.0.0 Safari/537.36')).toBe('chrome');
  });

  it('detects Firefox from user agent', () => {
    expect(detectBrowser('Mozilla/5.0 ... Gecko/20100101 Firefox/120.0')).toBe('firefox');
  });

  it('detects Safari from user agent', () => {
    expect(detectBrowser('Mozilla/5.0 (Macintosh; ...) AppleWebKit/605.1.15 ... Version/17.0 Safari/605.1.15')).toBe('safari');
  });

  it('detects macOS from user agent', () => {
    expect(detectOS('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)')).toBe('macos');
  });

  it('detects Windows from user agent', () => {
    expect(detectOS('Mozilla/5.0 (Windows NT 10.0; Win64; x64)')).toBe('windows');
  });

  it('detects Linux from user agent', () => {
    expect(detectOS('Mozilla/5.0 (X11; Linux x86_64)')).toBe('linux');
  });
});

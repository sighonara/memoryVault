import { describe, it, expect } from 'vitest';
import { formatTimestamp } from './log-viewer';

describe('LogViewer formatTimestamp', () => {
  it('formats ISO timestamp as 24-hour with date', () => {
    // Use a fixed UTC timestamp: 2026-03-14T15:30:45Z
    const result = formatTimestamp('2026-03-14T15:30:45Z');
    // Result depends on local timezone, but format should be YYYY-MM-DD HH:mm:ss
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
  });

  it('returns dash for null/undefined', () => {
    expect(formatTimestamp(null)).toBe('\u2014');
    expect(formatTimestamp(undefined)).toBe('\u2014');
  });

  it('uses 24-hour format (no AM/PM)', () => {
    const result = formatTimestamp('2026-03-14T15:30:45Z');
    expect(result).not.toContain('AM');
    expect(result).not.toContain('PM');
  });
});

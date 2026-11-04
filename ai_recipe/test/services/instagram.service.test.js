const { getInstagramShortcodeFromUrl } = require('../../src/services/instagram.service');

describe('getInstagramShortcodeFromUrl', () => {
  it('accepts Reel links with Instagram share-tracking query parameters', () => {
    expect(getInstagramShortcodeFromUrl(
      'https://www.instagram.com/reel/DfSJrAyq3LE/?rpxt=MmpzZjIzeTM5Zm4z'
    )).toBe('DfSJrAyq3LE');
  });

  it('accepts post and shortened Instagram links', () => {
    expect(getInstagramShortcodeFromUrl('https://instagram.com/p/ABC123/')).toBe('ABC123');
    expect(getInstagramShortcodeFromUrl('https://instagr.am/reels/XYZ789')).toBe('XYZ789');
  });
});

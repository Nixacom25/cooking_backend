const { parseKcal } = require('../../src/utils/nutrition');

describe('parseKcal', () => {
  test('reads numbers and strings with units', () => {
    expect(parseKcal(450)).toBe(450);
    expect(parseKcal('450')).toBe(450);
    expect(parseKcal('450 kcal')).toBe(450);
    expect(parseKcal('~1,200 calories')).toBe(1200);
  });

  test('a range gives its midpoint', () => {
    expect(parseKcal('400-500')).toBe(450);
  });

  test('missing or zero is null, never 0', () => {
    expect(parseKcal(null)).toBeNull();
    expect(parseKcal(undefined)).toBeNull();
    expect(parseKcal('')).toBeNull();
    expect(parseKcal('unknown')).toBeNull();
    expect(parseKcal(0)).toBeNull();
    expect(parseKcal('0')).toBeNull();
  });
});

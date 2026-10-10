/**
 * Reads a calorie value the model may return as 450, "450", "450 kcal",
 * "~450 calories" or "400-500". Returns a positive integer, or null when
 * there is no usable number - never 0, so the backend knows to estimate it.
 */
function parseKcal(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'number') return Number.isFinite(value) && value > 0 ? Math.round(value) : null;
  const numbers = String(value).replace(/,/g, '').match(/\d+(\.\d+)?/g);
  if (!numbers) return null;
  // A range ("400-500") -> its midpoint.
  const picked = numbers.slice(0, 2).map(Number);
  const kcal = Math.round(picked.reduce((a, b) => a + b, 0) / picked.length);
  return kcal > 0 && kcal < 10000 ? kcal : null;
}

module.exports = { parseKcal };

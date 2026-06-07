const { detectIngredientsHandler } = require('../../src/controllers/ingredientController');
const ingredientService = require('../../src/services/ingredientService');
const { AppError } = require('../../src/middleware/errorHandler');

jest.mock('../../src/services/ingredientService', () => ({
  detectIngredients: jest.fn(),
  detectIngredientsFromList: jest.fn(),
  detectIngredientsFromUrl: jest.fn(),
}));

jest.mock('../../src/utils/logger', () => ({
  info: jest.fn(),
  warn: jest.fn(),
  error: jest.fn(),
}));

describe('Ingredient Controller', () => {
  let req, res, next;

  beforeEach(() => {
    jest.clearAllMocks();
    req = {
      body: {},
    };
    res = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };
    next = jest.fn();
  });

  describe('detectIngredientsHandler', () => {
    it('should process pre-detected ingredients array', async () => {
      req.body.ingredients = ['apple'];
      ingredientService.detectIngredientsFromList.mockResolvedValueOnce({
        allowed_ingredients: [{ name: 'apple' }],
        restricted_ingredients: [],
      });

      await detectIngredientsHandler(req, res, next);

      expect(ingredientService.detectIngredientsFromList).toHaveBeenCalledWith(['apple'], expect.any(Object));
      expect(res.status).toHaveBeenCalledWith(200);
      expect(res.json).toHaveBeenCalledWith(expect.objectContaining({
        success: true,
        allowed_ingredients: [{ name: 'apple' }]
      }));
    });

    it('should process image_url', async () => {
      req.body.image_url = 'https://example.com/fridge.jpg';
      ingredientService.detectIngredientsFromUrl.mockResolvedValueOnce({
        allowed_ingredients: [{ name: 'tomato' }],
      });

      await detectIngredientsHandler(req, res, next);

      expect(ingredientService.detectIngredientsFromUrl).toHaveBeenCalledWith('https://example.com/fridge.jpg', expect.any(Object));
      expect(res.status).toHaveBeenCalledWith(200);
      expect(res.json).toHaveBeenCalledWith(expect.objectContaining({
        success: true,
        allowed_ingredients: [{ name: 'tomato' }]
      }));
    });

    it('should pass error to next if no valid input provided', async () => {
      await detectIngredientsHandler(req, res, next);
      expect(next).toHaveBeenCalledWith(expect.any(AppError));
      expect(next.mock.calls[0][0].message).toBe('No image file, base64 data, image_url, or ingredients array provided.');
    });
  });
});

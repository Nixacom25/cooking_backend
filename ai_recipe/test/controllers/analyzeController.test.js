const { analyzeHandler } = require('../../src/controllers/analyzeController');
const ingredientService = require('../../src/services/ingredientService');
const recipeService = require('../../src/services/recipeService');
const { AppError } = require('../../src/middleware/errorHandler');

jest.mock('../../src/services/ingredientService', () => ({
  detectIngredients: jest.fn(),
}));

jest.mock('../../src/services/recipeService', () => ({
  generateRecipes: jest.fn(),
}));

jest.mock('../../src/utils/logger', () => ({
  info: jest.fn(),
  error: jest.fn(),
}));

describe('Analyze Controller', () => {
  let req, res, next;

  beforeEach(() => {
    jest.clearAllMocks();
    req = {
      validatedBody: {
        manual_ingredients: [],
        ingredients: [],
        user_preferences: { allergies: [] }
      },
      file: null,
      body: {}
    };
    res = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };
    next = jest.fn();
  });

  it('should process text ingredients directly', async () => {
    req.validatedBody.ingredients = ['apple'];
    recipeService.generateRecipes.mockResolvedValueOnce([{ name: 'Apple Pie' }]);

    await analyzeHandler(req, res, next);

    expect(ingredientService.detectIngredients).not.toHaveBeenCalled();
    expect(recipeService.generateRecipes).toHaveBeenCalledWith(['apple'], expect.any(Object));
    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({
      success: true,
      allowed_ingredients: [{ name: 'apple', confidence: 1.0 }],
      recipes: [{ name: 'Apple Pie' }]
    }));
  });

  it('should process image upload', async () => {
    req.file = { buffer: Buffer.from('test'), originalname: 'test.jpg' };
    
    ingredientService.detectIngredients.mockResolvedValueOnce({
      allowed_ingredients: [{ name: 'tomato', confidence: 0.9 }],
      restricted_ingredients: [],
      image_url: 'http://image'
    });
    
    recipeService.generateRecipes.mockResolvedValueOnce([{ name: 'Tomato Soup' }]);

    await analyzeHandler(req, res, next);

    expect(ingredientService.detectIngredients).toHaveBeenCalled();
    expect(recipeService.generateRecipes).toHaveBeenCalledWith(['tomato'], expect.any(Object));
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({
      success: true,
      recipes: [{ name: 'Tomato Soup' }]
    }));
  });

  it('should pass error to next if no valid input provided', async () => {
    await analyzeHandler(req, res, next);
    expect(next).toHaveBeenCalledWith(expect.any(AppError));
    expect(next.mock.calls[0][0].message).toBe('No image provided.');
  });
});

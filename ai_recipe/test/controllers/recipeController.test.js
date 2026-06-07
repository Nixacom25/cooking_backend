const { generateRecipesHandler, getTrendingDishesHandler } = require('../../src/controllers/recipeController');
const recipeService = require('../../src/services/recipeService');
const aiService = require('../../src/services/aiService');

jest.mock('../../src/services/recipeService', () => ({
  generateRecipes: jest.fn(),
}));

jest.mock('../../src/services/aiService', () => ({
  generateTrendingDishes: jest.fn(),
}));

jest.mock('../../src/utils/logger', () => ({
  info: jest.fn(),
}));

describe('Recipe Controller', () => {
  let req, res, next;

  beforeEach(() => {
    jest.clearAllMocks();
    req = {
      validatedBody: {
        ingredients: ['apple', 'cinnamon'],
        user_preferences: { allergies: [], preferences: [] },
      },
    };
    res = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };
    next = jest.fn();
  });

  describe('generateRecipesHandler', () => {
    it('should successfully return generated recipes', async () => {
      const mockRecipes = [{ name: 'Apple Pie', image: null }];
      recipeService.generateRecipes.mockResolvedValueOnce(mockRecipes);

      await generateRecipesHandler(req, res, next);

      expect(recipeService.generateRecipes).toHaveBeenCalledWith(
        ['apple', 'cinnamon'],
        { allergies: [], preferences: [] }
      );
      expect(res.status).toHaveBeenCalledWith(200);
      expect(res.json).toHaveBeenCalledWith({
        success: true,
        recipes: mockRecipes,
        allowed_ingredients: [],
        restricted_ingredients: [],
        image_url: null,
      });
    });
  });

  describe('getTrendingDishesHandler', () => {
    it('should successfully return trending dishes', async () => {
      const mockTrending = ['Pasta', 'Salad'];
      aiService.generateTrendingDishes.mockResolvedValueOnce(mockTrending);

      await getTrendingDishesHandler(req, res, next);

      expect(aiService.generateTrendingDishes).toHaveBeenCalled();
      expect(res.status).toHaveBeenCalledWith(200);
      expect(res.json).toHaveBeenCalledWith({
        success: true,
        trending: mockTrending,
      });
    });
  });
});

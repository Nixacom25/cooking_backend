const recipeService = require('../../src/services/recipeService');
const aiService = require('../../src/services/aiService');

jest.mock('../../src/services/aiService', () => ({
  generateRecipesFromIngredients: jest.fn(),
  generateRecipeImage: jest.fn(),
}));

jest.mock('../../src/utils/logger', () => ({
  info: jest.fn(),
  warn: jest.fn(),
}));

describe('Recipe Service', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('generateRecipes', () => {
    it('should coordinate AI calls and return recipes with null images', async () => {
      const mockRecipes = [
        { title: 'Recipe 1', ingredients: [] },
        { title: 'Recipe 2', ingredients: [] },
      ];

      aiService.generateRecipesFromIngredients.mockResolvedValueOnce(mockRecipes);
      aiService.generateRecipeImage.mockResolvedValue('https://image.url');

      const ingredients = ['tomato', 'onion'];
      const preferences = { allergies: [] };

      const result = await recipeService.generateRecipes(ingredients, preferences);

      expect(aiService.generateRecipesFromIngredients).toHaveBeenCalledWith(ingredients, preferences);
      
      // Ensure the returned recipes have their images forced to null initially
      expect(result).toHaveLength(2);
      expect(result[0].image).toBeNull();
      expect(result[1].image).toBeNull();
      
      // Background process check (give it a micro-tick to run)
      await new Promise(resolve => setTimeout(resolve, 0));
      expect(aiService.generateRecipeImage).toHaveBeenCalledTimes(2);
    });
  });
});

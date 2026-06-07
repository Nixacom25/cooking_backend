const aiService = require('../../src/services/aiService');
const openaiClient = require('../../src/config/openai');

jest.mock('../../src/config/openai', () => ({
  chat: {
    completions: {
      create: jest.fn(),
    },
  },
  images: {
    generate: jest.fn(),
  },
}));

jest.mock('../../src/services/storageService', () => ({
  uploadImage: jest.fn().mockResolvedValue('https://cloudinary.com/image.jpg'),
}));

jest.mock('../../src/utils/logger', () => ({
  info: jest.fn(),
  error: jest.fn(),
  warn: jest.fn(),
  event: jest.fn(),
}));

describe('AI Service', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('validateIngredientSchema', () => {
    it('should validate and sanitize correct ingredient payload', () => {
      const parsed = {
        allowed_ingredients: [{ name: '  Tomato  ', confidence: 0.9, quantity: '2' }],
        restricted_ingredients: [{ ingredient: 'Peanut', reason: 'Allergy' }],
      };

      const result = aiService.validateIngredientSchema(parsed);
      expect(result.allowed_ingredients).toHaveLength(1);
      expect(result.allowed_ingredients[0].name).toBe('tomato');
      expect(result.restricted_ingredients).toHaveLength(1);
      expect(result.restricted_ingredients[0].name).toBe('peanut');
    });

    it('should throw error on missing array', () => {
      expect(() => aiService.validateIngredientSchema({})).toThrow('Invalid ingredient schema');
    });
  });

  describe('validateRecipesSchema', () => {
    it('should throw if recipes array is missing', () => {
      expect(() => aiService.validateRecipesSchema({})).toThrow('AI response missing "recipes" array');
    });

    it('should validate and transform recipes successfully', () => {
      const parsed = {
        recipes: [
          {
            name: 'Pizza',
            ingredients: [{ name: 'Dough', quantity: '1', price: 2.5 }],
            equipment: ['Oven'],
            steps: ['Bake'],
            cookTime: 15,
            prepTime: 10,
            kcal: 500,
            servings: 2,
            cuisine: 'Italian',
            category: 'Main'
          }
        ]
      };

      const result = aiService.validateRecipesSchema(parsed);
      expect(result).toHaveLength(1);
      expect(result[0].name).toBe('Pizza');
      expect(result[0].cookTime).toBe(15);
      expect(result[0].equipment).toContain('Oven');
    });
  });

  describe('generateTrendingDishes', () => {
    it('should parse trending dishes successfully', async () => {
      openaiClient.chat.completions.create.mockResolvedValueOnce({
        choices: [{ message: { content: '{"trending": ["Dish 1", "Dish 2"]}' } }]
      });

      const result = await aiService.generateTrendingDishes();
      expect(result).toEqual(['Dish 1', 'Dish 2']);
    });

    it('should return fallback if OpenAI fails', async () => {
      openaiClient.chat.completions.create.mockRejectedValueOnce(new Error('Network error'));

      const result = await aiService.generateTrendingDishes();
      expect(result).toContain('Pizza'); // Part of fallback
    });
  });

  describe('generateRecipesFromIngredients', () => {
    it('should return parsed recipes when successful', async () => {
      const mockResponse = {
        recipes: [
          {
            name: 'Test',
            ingredients: [{ name: 'A', quantity: '1' }],
            steps: ['B'],
            equipment: ['C']
          }
        ]
      };
      
      openaiClient.chat.completions.create.mockResolvedValueOnce({
        choices: [{ message: { content: JSON.stringify(mockResponse) } }]
      });

      const result = await aiService.generateRecipesFromIngredients(['A']);
      expect(result).toHaveLength(1);
      expect(result[0].name).toBe('Test');
    });
  });
});

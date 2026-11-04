const {
  tiktokService,
} = require('../../src/services/tiktok.service');
const {
  instagramService,
} = require('../../src/services/instagram.service');
const {
  openaiService,
} = require('../../src/services/openai.service');
const {
  youtubeService,
} = require('../../src/services/youtube.service');
const {
  webService,
} = require('../../src/services/web.service');
const { extractRecipe } = require('../../src/controllers/extractController');

jest.mock('../../src/services/tiktok.service', () => ({
  tiktokService: jest.fn(),
}));
jest.mock('../../src/services/instagram.service', () => ({
  instagramService: jest.fn(),
}));
jest.mock('../../src/services/openai.service', () => ({
  openaiService: {
    extractRecipeFromCaption: jest.fn(),
  },
}));
jest.mock('../../src/services/youtube.service', () => ({
  youtubeService: jest.fn(),
}));
jest.mock('../../src/services/web.service', () => ({
  webService: jest.fn(),
}));

describe('extractRecipe', () => {
  let req;
  let res;
  let next;

  beforeEach(() => {
    jest.clearAllMocks();
    req = { body: { url: 'https://www.instagram.com/reel/DfSJrAyq3LE/' } };
    res = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };
    next = jest.fn();
    openaiService.extractRecipeFromCaption.mockResolvedValue({
      status: 'success',
      chef_persona: 'Italian chef',
      recipe: {
        title: 'Bolognese Potato Bake',
        ingredients: [{ name: 'potatoes', quantity: '2 lb', icon: '🥔' }],
        instructions: ['Bake until tender.'],
        time_and_servings: { cook_time: '45', prep_time: '15', servings: '6' },
        metadata: { cuisine: 'Italian', meal_type: 'Dinner' },
      },
    });
  });

  it('uses the captured caption instead of scraping a social page again', async () => {
    req.body.caption = 'Bolognese Potato Bake. Ingredients: 2 lb Yukon Gold potatoes.';
    req.body.thumbnail = 'https://cdn.example.com/potato-bake.jpg';

    await extractRecipe(req, res, next);

    expect(instagramService).not.toHaveBeenCalled();
    expect(openaiService.extractRecipeFromCaption).toHaveBeenCalledWith(req.body.caption);
    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({
      success: true,
      data: expect.objectContaining({
        source: 'instagram',
        image: req.body.thumbnail,
        recipe: expect.objectContaining({
          recipe: expect.objectContaining({
            name: 'Bolognese Potato Bake',
            ingredients: expect.arrayContaining([
              expect.objectContaining({ name: 'potatoes' }),
            ]),
          }),
        }),
      }),
    }));
    expect(next).not.toHaveBeenCalled();
  });

  it.each([
    ['Instagram Reel', req => req.body.url],
    ['TikTok', req => { req.body.url = 'https://www.tiktok.com/@chef/video/123'; return req.body.url; }],
    ['YouTube', req => { req.body.url = 'https://youtu.be/abcdefghijk'; return req.body.url; }],
  ])('routes %s links to their supported extractor', async (_label, setUrl) => {
    const url = setUrl(req);
    const service = url.includes('tiktok')
      ? tiktokService
      : url.includes('youtu')
        ? youtubeService
        : instagramService;
    service.mockResolvedValue({
      platform: 'social',
      description: 'A complete recipe caption.',
      thumbnail: null,
    });

    await extractRecipe(req, res, next);

    expect(service).toHaveBeenCalledWith(url);
    expect(openaiService.extractRecipeFromCaption).toHaveBeenCalledWith('A complete recipe caption.');
    expect(res.status).toHaveBeenCalledWith(200);
  });

  it('rejects oversized captured captions', async () => {
    req.body.caption = 'x'.repeat(20001);

    await extractRecipe(req, res, next);

    expect(res.status).toHaveBeenCalledWith(400);
    expect(instagramService).not.toHaveBeenCalled();
    expect(openaiService.extractRecipeFromCaption).not.toHaveBeenCalled();
  });
});

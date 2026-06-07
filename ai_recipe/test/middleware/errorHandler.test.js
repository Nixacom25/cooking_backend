const { AppError, errorHandler, asyncHandler } = require('../../src/middleware/errorHandler');
const logger = require('../../src/utils/logger');

jest.mock('../../src/utils/logger', () => ({
  event: jest.fn(),
}));

describe('Error Handler Middleware', () => {
  let req, res, next;

  beforeEach(() => {
    jest.clearAllMocks();
    req = { path: '/test', method: 'GET', ip: '127.0.0.1' };
    res = {
      status: jest.fn().mockReturnThis(),
      json: jest.fn(),
    };
    next = jest.fn();
  });

  describe('AppError', () => {
    it('should correctly initialize', () => {
      const err = new AppError(400, 'BAD_REQUEST', 'Invalid input');
      expect(err.statusCode).toBe(400);
      expect(err.code).toBe('BAD_REQUEST');
      expect(err.message).toBe('Invalid input');
      expect(err.isOperational).toBe(true);
    });
  });

  describe('errorHandler', () => {
    it('should handle AppError correctly and log as warn', () => {
      const err = new AppError(404, 'NOT_FOUND', 'Resource missing');

      errorHandler(err, req, res, next);

      expect(logger.event).toHaveBeenCalledWith('warn', 'request.error', '[NOT_FOUND] Resource missing', expect.any(Object));
      expect(res.status).toHaveBeenCalledWith(404);
      expect(res.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'NOT_FOUND',
          message: 'Resource missing',
        },
      });
    });

    it('should handle generic Error correctly and log as error', () => {
      const err = new Error('Database exploded');

      errorHandler(err, req, res, next);

      expect(logger.event).toHaveBeenCalledWith('error', 'request.error.unexpected', 'Unexpected error', expect.any(Object));
      expect(res.status).toHaveBeenCalledWith(500);
      expect(res.json).toHaveBeenCalledWith({
        success: false,
        error: {
          code: 'INTERNAL_SERVER_ERROR',
          message: 'An unexpected error occurred. Please try again later.',
        },
      });
    });
  });

  describe('asyncHandler', () => {
    it('should catch rejected promises and pass to next', async () => {
      const err = new Error('Test Error');
      const asyncRoute = async () => { throw err; };
      const wrapped = asyncHandler(asyncRoute);

      await wrapped(req, res, next);

      expect(next).toHaveBeenCalledWith(err);
    });

    it('should resolve successfully without calling next with error', async () => {
      const asyncRoute = async () => 'Success';
      const wrapped = asyncHandler(asyncRoute);

      await wrapped(req, res, next);

      expect(next).not.toHaveBeenCalled();
    });
  });
});

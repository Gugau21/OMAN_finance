import { isMarketStatusDto, type MarketStatusDto } from '../../../lib/validation/MarketStatusDto';

export type GetMarketStatusResponse = MarketStatusDto;

export function parseGetMarketStatus(value: unknown): GetMarketStatusResponse {
  if (!isMarketStatusDto(value)) {
    throw new Error('Market status response has an invalid shape');
  }

  return {
    timestamp: value.timestamp,
    isOpen: value.isOpen,
    nextOpen: value.nextOpen,
    nextClose: value.nextClose,
  };
}

import type { MarketStatusDto } from '../api/marketStatusDto';
import type { MarketStatus } from '../model/market';

export function toMarketStatus(dto: MarketStatusDto): MarketStatus {
  return {
    timestamp: new Date(dto.timestamp),
    isOpen: dto.isOpen,
    nextOpen: new Date(dto.nextOpen),
    nextClose: new Date(dto.nextClose),
  };
}

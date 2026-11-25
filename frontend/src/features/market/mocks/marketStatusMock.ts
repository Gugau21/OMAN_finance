import type { MockScenario } from '../../../data/dataSource';
import type { MarketStatusDto } from '../api/marketStatusDto';

const mockMarketStatus: MarketStatusDto = {
  timestamp: '2026-10-29T13:08:00-04:00',
  isOpen: true,
  nextOpen: '2026-10-30T09:30:00-04:00',
  nextClose: '2026-10-29T16:00:00-04:00',
};

export async function getMockMarketStatus(
  scenario: MockScenario = getConfiguredScenario(),
  signal?: AbortSignal,
): Promise<MarketStatusDto | null> {
  if (scenario === 'empty') {
    return null;
  }

  if (scenario === 'error') {
    throw new Error('Market status mock request failed');
  }

  if (scenario === 'loading') {
    return waitForever(signal);
  }

  return mockMarketStatus;
}

function getConfiguredScenario(): MockScenario {
  const configuredScenario = import.meta.env.VITE_MARKET_STATUS_SCENARIO;

  return configuredScenario === 'empty' ||
    configuredScenario === 'error' ||
    configuredScenario === 'loading'
    ? configuredScenario
    : 'success';
}

function waitForever(signal?: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Market status mock request aborted', 'AbortError'));
      return;
    }

    signal?.addEventListener(
      'abort',
      () => reject(new DOMException('Market status mock request aborted', 'AbortError')),
      { once: true },
    );
  });
}

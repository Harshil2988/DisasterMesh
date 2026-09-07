import type { OperationalDataset } from '../types';
import type { DataProvider, DataLoadResult } from './provider';

/**
 * Live data from the rescuer backend.
 *
 * Reads whatever this deployment's API has actually received from rescuer
 * gateways. When nothing has been uploaded yet the result is an empty
 * dataset, and the interface says "waiting for gateway connection" — it never
 * falls back to demonstration data, because a commander must never see
 * fabricated incidents while believing the console is live.
 */
export class ApiDataProvider implements DataProvider {
  readonly mode = 'LIVE' as const;
  readonly description: string;

  constructor(private readonly baseUrl = '/api') {
    this.description = `Live rescuer uplink — ${baseUrl}`;
  }

  async load(): Promise<DataLoadResult> {
    try {
      const response = await fetch(`${this.baseUrl}/dataset`, { cache: 'no-store' });
      if (!response.ok) {
        return {
          dataset: null,
          error: `Command Center connection unavailable — endpoint returned ${response.status}.`,
        };
      }
      const dataset = (await response.json()) as OperationalDataset;
      return { dataset, error: null };
    } catch {
      return {
        dataset: null,
        error: 'Command Center connection unavailable — no response from the rescue data endpoint.',
      };
    }
  }
}

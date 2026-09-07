import type { DataProvider, DataLoadResult } from './provider';
import { buildDemoDataset } from './seed';

/**
 * Demonstration data.
 *
 * Deterministic by construction: the scenario is authored, not randomised, so
 * a walkthrough runs the same way every time. The only thing that moves
 * between loads is the clock, which keeps waiting times honest.
 */
export class DemoDataProvider implements DataProvider {
  readonly mode = 'DEMO' as const;
  readonly description = 'Seeded demonstration scenario — OP RIVERSIDE';

  async load(): Promise<DataLoadResult> {
    return { dataset: buildDemoDataset(Date.now()), error: null };
  }
}

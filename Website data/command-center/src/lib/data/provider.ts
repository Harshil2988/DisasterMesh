import type { DataMode, OperationalDataset } from '../types';

/**
 * The seam between the interface and its data.
 *
 * Nothing in `src/components` or `src/app` may construct an emergency, a team
 * or a hospital. Every screen consumes whatever a provider hands it, which is
 * what makes swapping demonstration data for a live rescuer backend a
 * one-line change rather than a rewrite.
 */
export interface DataProvider {
  readonly mode: DataMode;
  /** One line for the UI, e.g. "Seeded demonstration scenario". */
  readonly description: string;
  load(): Promise<DataLoadResult>;
}

export interface DataLoadResult {
  dataset: OperationalDataset | null;
  /** Set when the source could not be reached. The UI shows this verbatim. */
  error: string | null;
}

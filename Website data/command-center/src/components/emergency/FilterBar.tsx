'use client';

import { ListFilter, Search, X } from 'lucide-react';
import { useOps, type SortKey } from '@/state/ops-store';
import { CATEGORY_META, CATEGORY_ORDER, EMERGENCY_STATUS_META } from '@/lib/constants';
import type { EmergencyStatus } from '@/lib/types';
import { CATEGORY_ICON, cx } from '@/components/ui/primitives';

const SORTS: { id: SortKey; label: string }[] = [
  { id: 'PRIORITY', label: 'Highest priority' },
  { id: 'OLDEST', label: 'Longest waiting' },
  { id: 'NEAREST', label: 'Nearest' },
  { id: 'NEWEST', label: 'Most recent' },
  { id: 'PEOPLE', label: 'Most people' },
];

const STATUS_ORDER: EmergencyStatus[] = [
  'UNASSIGNED',
  'ACKNOWLEDGED',
  'ASSIGNED',
  'IN_PROGRESS',
  'RESCUED',
  'RESOLVED',
  'INVALID',
];

/**
 * Queue filters.
 *
 * Chips rather than dropdowns: the current filter has to be readable without
 * opening anything, because a queue that is quietly filtered is a queue that
 * quietly hides an emergency.
 */
export function FilterBar(): React.JSX.Element {
  const { filters, toggleCategory, toggleStatus, setFilters, resetFilters, derived } = useOps();

  const categoriesFiltered = filters.categories.size < CATEGORY_ORDER.length;
  const statusesFiltered = filters.statuses.size < STATUS_ORDER.length;
  const active = categoriesFiltered || statusesFiltered || filters.search.length > 0;

  return (
    <div className="space-y-2 border-b border-line px-3 py-2.5">
      {/* search */}
      <div className="relative">
        <Search
          size={12}
          className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-text-3"
          aria-hidden
        />
        <label htmlFor="queue-search" className="sr-only">
          Filter the queue
        </label>
        <input
          id="queue-search"
          value={filters.search}
          onChange={(e) => setFilters({ search: e.target.value })}
          placeholder="Filter by id, sector, node, description…"
          className="h-7.5 w-full rounded-sm border border-line bg-surface-2 pr-7 pl-7.5 text-[12px] text-text placeholder:text-text-3 focus:border-line-2"
        />
        {filters.search && (
          <button
            type="button"
            onClick={() => setFilters({ search: '' })}
            aria-label="Clear queue filter"
            className="absolute top-1/2 right-2 -translate-y-1/2 text-text-3 hover:text-text"
          >
            <X size={12} />
          </button>
        )}
      </div>

      {/* categories */}
      <div className="flex flex-wrap gap-1">
        {CATEGORY_ORDER.map((category) => {
          const meta = CATEGORY_META[category];
          const Icon = CATEGORY_ICON[category];
          const on = filters.categories.has(category);
          const count = derived.emergencies.filter((e) => e.category === category).length;

          return (
            <button
              key={category}
              type="button"
              onClick={() => toggleCategory(category)}
              aria-pressed={on}
              className={cx(
                'flex items-center gap-1 rounded-xs border px-1.5 py-1 text-[10.5px] font-medium transition-colors',
                on ? 'text-text' : 'border-line bg-transparent text-text-3 hover:text-text-2',
              )}
              style={
                on
                  ? { borderColor: `rgb(${meta.rgb} / 0.45)`, background: `rgb(${meta.rgb} / 0.12)`, color: meta.text }
                  : undefined
              }
            >
              <Icon size={10} aria-hidden />
              {meta.short}
              <span className="num opacity-60">{count}</span>
            </button>
          );
        })}
      </div>

      {/* statuses */}
      <div className="flex flex-wrap gap-1">
        {STATUS_ORDER.map((status) => {
          const meta = EMERGENCY_STATUS_META[status];
          const on = filters.statuses.has(status);
          return (
            <button
              key={status}
              type="button"
              onClick={() => toggleStatus(status)}
              aria-pressed={on}
              className={cx(
                'flex items-center gap-1 rounded-xs border px-1.5 py-0.5 text-[10px] transition-colors',
                on
                  ? 'border-line-2 bg-surface-3 text-text-2'
                  : 'border-line text-text-3 opacity-55 hover:opacity-85',
              )}
            >
              <span className="size-1.5 rounded-full" style={{ background: meta.dot }} aria-hidden />
              {meta.label}
            </button>
          );
        })}
      </div>

      {/* sort + reset */}
      <div className="flex items-center gap-2">
        <ListFilter size={11} className="shrink-0 text-text-3" aria-hidden />
        <label htmlFor="queue-sort" className="sr-only">
          Sort the queue
        </label>
        <select
          id="queue-sort"
          value={filters.sort}
          onChange={(e) => setFilters({ sort: e.target.value as SortKey })}
          className="h-6.5 flex-1 rounded-sm border border-line bg-surface-2 px-1.5 text-[11px] text-text-2 focus:border-line-2"
        >
          {SORTS.map((sort) => (
            <option key={sort.id} value={sort.id}>
              {sort.label}
            </option>
          ))}
        </select>
        {active && (
          <button
            type="button"
            onClick={resetFilters}
            className="shrink-0 text-[10.5px] text-rescuer-text hover:underline"
          >
            Reset
          </button>
        )}
      </div>
    </div>
  );
}

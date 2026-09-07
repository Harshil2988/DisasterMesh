'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Hospital, Search, Siren, Users, X } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import { CATEGORY_META } from '@/lib/constants';
import { formatDistance } from '@/lib/geo';
import { cx, CategoryBadge } from '@/components/ui/primitives';

interface Hit {
  id: string;
  kind: 'emergency' | 'team' | 'hospital' | 'sector';
  title: string;
  subtitle: string;
  href: string;
  category?: keyof typeof CATEGORY_META;
}

const MAX_HITS = 8;

/**
 * Command-centre search.
 *
 * One box across every record type, because under pressure nobody remembers
 * which screen owns which entity. Debounced, keyboard-first, and it always
 * shows what kind of thing each hit is — an id like DM-1047 and a team id
 * look alike at a glance otherwise.
 */
export function GlobalSearch(): React.JSX.Element {
  const router = useRouter();
  const { derived, dataset, selectEmergency, selectTeam, selectHospital } = useOps();
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [open, setOpen] = useState(false);
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim().toLowerCase());
      // A new result set invalidates the highlighted row, so the cursor is
      // reset here rather than in a second effect that would re-render again.
      setCursor(0);
    }, 120);
    return () => window.clearTimeout(timer);
  }, [query]);

  // Ctrl/Cmd-K from anywhere.
  useEffect(() => {
    const onKey = (event: KeyboardEvent): void => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        inputRef.current?.focus();
        setOpen(true);
      }
      if (event.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    const onClick = (event: MouseEvent): void => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const hits = useMemo<Hit[]>(() => {
    if (debounced.length < 1 || !dataset) return [];
    const out: Hit[] = [];

    for (const e of derived.emergencies) {
      const haystack = `${e.id} ${e.category} ${e.status} ${e.priority} ${e.description} ${
        e.sector ?? ''
      } ${e.mesh.senderNodeId}`.toLowerCase();
      if (!haystack.includes(debounced)) continue;
      out.push({
        id: e.id,
        kind: 'emergency',
        title: e.id,
        subtitle: `${e.sector ?? 'No sector'} · ${formatDistance(e.distanceFromCommand)} · ${e.description}`,
        href: '/emergencies',
        category: e.category,
      });
    }

    for (const t of dataset.teams) {
      if (!`${t.id} ${t.name} ${t.callsign} ${t.status}`.toLowerCase().includes(debounced)) continue;
      out.push({
        id: t.id,
        kind: 'team',
        title: t.name,
        subtitle: `${t.members.length} members · ${t.status.replace(/_/g, ' ').toLowerCase()}`,
        href: '/teams',
      });
    }

    for (const h of dataset.hospitals) {
      if (!`${h.id} ${h.name} ${h.status}`.toLowerCase().includes(debounced)) continue;
      out.push({
        id: h.id,
        kind: 'hospital',
        title: h.name,
        subtitle: `${h.status.replace(/_/g, ' ').toLowerCase()}${
          h.emergencyBeds !== undefined ? ` · ${h.emergencyBeds} emergency beds` : ' · capacity unknown'
        }`,
        href: '/hospitals',
      });
    }

    // Rank: exact id match first, then emergencies, then everything else.
    return out
      .sort((a, b) => {
        const aExact = a.id.toLowerCase() === debounced ? 0 : 1;
        const bExact = b.id.toLowerCase() === debounced ? 0 : 1;
        return aExact - bExact;
      })
      .slice(0, MAX_HITS);
  }, [debounced, derived.emergencies, dataset]);

  const go = (hit: Hit): void => {
    if (hit.kind === 'emergency') selectEmergency(hit.id);
    if (hit.kind === 'team') selectTeam(hit.id);
    if (hit.kind === 'hospital') selectHospital(hit.id);
    router.push(hit.href);
    setOpen(false);
    setQuery('');
    inputRef.current?.blur();
  };

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setCursor((c) => Math.min(hits.length - 1, c + 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setCursor((c) => Math.max(0, c - 1));
    } else if (event.key === 'Enter' && hits[cursor]) {
      event.preventDefault();
      go(hits[cursor]);
    }
  };

  const iconFor = (kind: Hit['kind']) =>
    kind === 'team' ? Users : kind === 'hospital' ? Hospital : Siren;

  return (
    <div ref={containerRef} className="relative w-[168px] shrink-0 md:w-[220px] xl:w-[290px]">
      <label htmlFor="global-search" className="sr-only">
        Search incidents, teams, hospitals and sectors
      </label>
      <div className="relative">
        <Search
          size={13}
          className="pointer-events-none absolute top-1/2 left-2.5 -translate-y-1/2 text-text-3"
          aria-hidden
        />
        <input
          id="global-search"
          ref={inputRef}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          role="combobox"
          aria-expanded={open && hits.length > 0}
          aria-controls="global-search-results"
          aria-autocomplete="list"
          placeholder="Search DM-1001, Sector 4, Bravo…"
          className="h-8 w-full rounded-sm border border-line bg-surface-2 pr-14 pl-7.5 text-[12px] text-text placeholder:text-text-3 focus:border-line-2"
        />
        {query ? (
          <button
            type="button"
            onClick={() => {
              setQuery('');
              inputRef.current?.focus();
            }}
            aria-label="Clear search"
            className="absolute top-1/2 right-2 -translate-y-1/2 text-text-3 hover:text-text"
          >
            <X size={13} />
          </button>
        ) : (
          <kbd className="num pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 rounded-xs border border-line px-1 py-px text-[9.5px] text-text-3">
            ⌘K
          </kbd>
        )}
      </div>

      {open && debounced.length > 0 && (
        <div
          id="global-search-results"
          role="listbox"
          className="anim-in absolute top-9 left-0 z-50 w-[420px] max-w-[70vw] overflow-hidden rounded-md border border-line-2 bg-surface-2 shadow-[0_16px_48px_rgb(0_0_0/0.65)]"
        >
          {hits.length === 0 ? (
            <p className="px-3 py-4 text-center text-[11.5px] text-text-3">
              No records match “{query}”.
            </p>
          ) : (
            <ul className="max-h-[340px] overflow-y-auto py-1">
              {hits.map((hit, index) => {
                const Icon = iconFor(hit.kind);
                return (
                  <li key={`${hit.kind}-${hit.id}`}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={index === cursor}
                      onMouseEnter={() => setCursor(index)}
                      onClick={() => go(hit)}
                      className={cx(
                        'flex w-full items-center gap-2.5 px-3 py-1.5 text-left',
                        index === cursor ? 'bg-surface-3' : 'hover:bg-surface-3',
                      )}
                    >
                      <Icon size={13} className="shrink-0 text-text-3" aria-hidden />
                      <span className="num shrink-0 text-[12px] font-medium text-text">
                        {hit.title}
                      </span>
                      {hit.category && <CategoryBadge category={hit.category} />}
                      <span className="truncate text-[11px] text-text-3">{hit.subtitle}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

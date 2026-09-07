'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useOps } from '@/state/ops-store';
import { cx } from '@/components/ui/primitives';
import { PRIMARY_NAV, SECONDARY_NAV, type NavItem } from './nav-items';
import { DemoControls } from './DemoControls';

function NavLink({ item, active, count }: { item: NavItem; active: boolean; count?: number }) {
  const Icon = item.icon;
  return (
    <Link
      href={item.href}
      aria-current={active ? 'page' : undefined}
      className={cx(
        'group relative flex h-8 items-center gap-2.5 rounded-sm pr-2 pl-3 text-[12.5px] transition-colors duration-150',
        active
          ? 'bg-surface-3 font-medium text-text'
          : 'text-text-3 hover:bg-surface-2 hover:text-text-2',
      )}
    >
      {/* The active marker is a shape, not only a colour. */}
      <span
        className={cx(
          'absolute top-1.5 bottom-1.5 left-0 w-0.5 rounded-full transition-colors',
          active ? 'bg-rescuer' : 'bg-transparent',
        )}
        aria-hidden
      />
      <Icon size={14.5} strokeWidth={active ? 2.2 : 1.9} className="shrink-0" aria-hidden />
      <span className="flex-1 truncate">{item.label}</span>
      {count !== undefined && count > 0 && (
        <span className="num rounded-xs bg-surface-4 px-1 py-px text-[10px] text-text-3 tabular-nums">
          {count}
        </span>
      )}
    </Link>
  );
}

export function Sidebar(): React.JSX.Element {
  const pathname = usePathname();
  const { derived, mode } = useOps();

  const countFor = (item: NavItem): number | undefined => {
    if (!item.countKey) return undefined;
    return derived.stats[item.countKey];
  };

  return (
    <nav
      aria-label="Command centre sections"
      // min-h-0 lets the rail shrink inside the flex row; without it a tall
      // rail pushes the whole shell past the viewport height.
      className="scroll-y flex w-[196px] min-h-0 shrink-0 flex-col gap-1 border-r border-line bg-surface-1 px-2 py-3"
    >
      <p className="eyebrow px-3 pb-1">Operations</p>
      {PRIMARY_NAV.map((item) => (
        <NavLink
          key={item.href}
          item={item}
          active={pathname === item.href}
          count={countFor(item)}
        />
      ))}

      <p className="eyebrow px-3 pt-4 pb-1">Data pipeline</p>
      {SECONDARY_NAV.map((item) => (
        <NavLink key={item.href} item={item} active={pathname === item.href} />
      ))}

      <div className="mt-auto space-y-2 px-1 pt-4">
        <DemoControls />
        <p className="px-1.5 text-[10px] leading-tight text-text-3">
          {mode === 'DEMO' ? 'Demonstration dataset' : 'Live rescuer uplink'}
        </p>
      </div>
    </nav>
  );
}

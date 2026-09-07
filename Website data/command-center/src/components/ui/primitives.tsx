'use client';

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import {
  HeartPulse,
  Info,
  Package,
  ShieldCheck,
  Siren,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';
import type {
  Category,
  EmergencyStatus,
  FieldSource,
  HospitalStatus,
  MissionStatus,
  PriorityLevel,
  TeamStatus,
} from '@/lib/types';
import {
  CATEGORY_META,
  EMERGENCY_STATUS_META,
  HOSPITAL_STATUS_META,
  MISSION_STATUS_META,
  PRIORITY_META,
  SOURCE_META,
  TEAM_STATUS_META,
} from '@/lib/constants';

export const cx = (...parts: Array<string | false | null | undefined>): string =>
  parts.filter(Boolean).join(' ');

// ---------------------------------------------------------------------------
// Category identity — colour AND icon AND text, never colour alone.
// ---------------------------------------------------------------------------

export const CATEGORY_ICON: Record<Category, LucideIcon> = {
  CRITICAL: Siren,
  MEDICAL: HeartPulse,
  WARNING: TriangleAlert,
  SUPPLY: Package,
  SAFE: ShieldCheck,
};

interface CategoryBadgeProps {
  category: Category;
  size?: 'sm' | 'md';
  showLabel?: boolean;
}

export function CategoryBadge({
  category,
  size = 'sm',
  showLabel = true,
}: CategoryBadgeProps): React.JSX.Element {
  const meta = CATEGORY_META[category];
  const Icon = CATEGORY_ICON[category];
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-xs border font-semibold uppercase tracking-wider whitespace-nowrap',
        size === 'sm' ? 'px-1.5 py-0.5 text-[10px]' : 'px-2 py-1 text-[11px]',
      )}
      style={{
        color: meta.text,
        borderColor: `rgb(${meta.rgb} / 0.35)`,
        background: `rgb(${meta.rgb} / 0.1)`,
      }}
    >
      <Icon size={size === 'sm' ? 11 : 13} strokeWidth={2.4} aria-hidden />
      {showLabel && meta.short}
    </span>
  );
}

export function PriorityBadge({
  priority,
  score,
}: {
  priority: PriorityLevel;
  score?: number;
}): React.JSX.Element {
  const meta = PRIORITY_META[priority];
  return (
    <span
      className="inline-flex items-center gap-1 rounded-xs border px-1.5 py-0.5 text-[10px] font-bold tracking-wider"
      style={{ color: meta.text, borderColor: `${meta.mark}59`, background: `${meta.mark}1a` }}
      title={`${priority} — ${meta.label}${score !== undefined ? ` · score ${score}/100` : ''}`}
    >
      {priority}
      {score !== undefined && <span className="num opacity-70">{score}</span>}
    </span>
  );
}

type AnyStatus = EmergencyStatus | TeamStatus | MissionStatus | HospitalStatus;

const STATUS_LOOKUP: Record<string, { label: string; text: string; dot: string }> = {
  ...EMERGENCY_STATUS_META,
  ...TEAM_STATUS_META,
  ...MISSION_STATUS_META,
  ...HOSPITAL_STATUS_META,
};

export function StatusBadge({
  status,
  kind = 'emergency',
}: {
  status: AnyStatus;
  kind?: 'emergency' | 'team' | 'mission' | 'hospital';
}): React.JSX.Element {
  const table =
    kind === 'team'
      ? TEAM_STATUS_META
      : kind === 'mission'
        ? MISSION_STATUS_META
        : kind === 'hospital'
          ? HOSPITAL_STATUS_META
          : EMERGENCY_STATUS_META;

  const meta =
    (table as Record<string, { label: string; text: string; dot: string }>)[status] ??
    STATUS_LOOKUP[status] ?? { label: String(status), text: '#94a3b8', dot: '#64748b' };

  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-[11px] font-medium text-text-2">
      <span
        className="size-1.5 shrink-0 rounded-full"
        style={{ background: meta.dot }}
        aria-hidden
      />
      <span style={{ color: meta.text }}>{meta.label}</span>
    </span>
  );
}

/**
 * Where a value came from. Shown next to any figure a commander might
 * otherwise assume was measured — the difference between "reported" and
 * "computed here" changes how much weight it deserves.
 */
export function SourceTag({ source }: { source: FieldSource }): React.JSX.Element {
  const meta = SOURCE_META[source];
  return (
    <span
      className="inline-flex items-center rounded-xs border border-line px-1 py-px text-[9.5px] font-semibold tracking-wider text-text-3 uppercase"
      title={meta.description}
    >
      {meta.label}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

type ButtonVariant = 'primary' | 'default' | 'ghost' | 'danger' | 'success';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: 'sm' | 'md';
  icon?: LucideIcon;
  block?: boolean;
}

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'bg-rescuer/15 text-rescuer-text border-rescuer/40 hover:bg-rescuer/25 hover:border-rescuer/60',
  default:
    'bg-surface-3 text-text-2 border-line-2 hover:bg-surface-4 hover:text-text hover:border-[#3d4a68]',
  ghost: 'bg-transparent text-text-3 border-transparent hover:bg-surface-3 hover:text-text',
  danger:
    'bg-critical/15 text-critical-text border-critical/40 hover:bg-critical/25 hover:border-critical/60',
  success: 'bg-safe/15 text-safe-text border-safe/40 hover:bg-safe/25 hover:border-safe/60',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'default', size = 'md', icon: Icon, block, className, children, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type="button"
      className={cx(
        'inline-flex items-center justify-center gap-1.5 rounded-sm border font-medium',
        'transition-colors duration-150',
        'disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-inherit',
        // 28px is below the 44px touch guidance, which applies to touch input.
        // This is a pointer-driven desktop console; rows stay 32px+ and every
        // control keeps 8px of separation.
        size === 'sm' ? 'h-7 px-2 text-[11px]' : 'h-8 px-2.5 text-xs',
        block && 'w-full',
        VARIANTS[variant],
        className,
      )}
      {...rest}
    >
      {Icon && <Icon size={size === 'sm' ? 12 : 14} strokeWidth={2.2} aria-hidden />}
      {children}
    </button>
  );
});

// ---------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------

interface PanelProps {
  title?: string;
  subtitle?: string;
  icon?: LucideIcon;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  /** Removes body padding for tables and maps that manage their own inset. */
  flush?: boolean;
}

export function Panel({
  title,
  subtitle,
  icon: Icon,
  actions,
  children,
  className,
  bodyClassName,
  flush,
}: PanelProps): React.JSX.Element {
  return (
    <section className={cx('flex min-h-0 flex-col rounded-md border border-line bg-surface-2', className)}>
      {(title || actions) && (
        <header className="flex h-11 shrink-0 items-center justify-between gap-3 border-b border-line px-3.5">
          <div className="flex min-w-0 items-center gap-2">
            {Icon && <Icon size={14} className="shrink-0 text-text-3" aria-hidden />}
            <div className="min-w-0">
              {title && (
                <h2 className="truncate text-[12.5px] font-semibold tracking-wide text-text">
                  {title}
                </h2>
              )}
              {subtitle && <p className="truncate text-[11px] text-text-3">{subtitle}</p>}
            </div>
          </div>
          {actions && <div className="flex shrink-0 items-center gap-1.5">{actions}</div>}
        </header>
      )}
      <div className={cx('min-h-0 flex-1', !flush && 'p-3.5', bodyClassName)}>{children}</div>
    </section>
  );
}

// ---------------------------------------------------------------------------
// States — every surface that can be empty, loading or broken has one.
// ---------------------------------------------------------------------------

export function EmptyState({
  icon: Icon,
  title,
  detail,
  action,
}: {
  icon: LucideIcon;
  title: string;
  detail?: string;
  action?: ReactNode;
}): React.JSX.Element {
  return (
    <div className="flex h-full min-h-[140px] flex-col items-center justify-center gap-2 px-6 py-8 text-center">
      <Icon size={22} className="text-line-2" strokeWidth={1.6} aria-hidden />
      <p className="text-[13px] font-medium text-text-2">{title}</p>
      {detail && <p className="max-w-sm text-[11.5px] leading-relaxed text-text-3">{detail}</p>}
      {action && <div className="mt-1">{action}</div>}
    </div>
  );
}

export function ErrorState({
  title,
  detail,
  action,
}: {
  title: string;
  detail?: string;
  action?: ReactNode;
}): React.JSX.Element {
  return (
    <div
      role="alert"
      className="flex h-full min-h-[140px] flex-col items-center justify-center gap-2 px-6 py-8 text-center"
    >
      <TriangleAlert size={22} className="text-critical-text" strokeWidth={1.8} aria-hidden />
      <p className="text-[13px] font-semibold text-critical-text">{title}</p>
      {detail && <p className="max-w-sm text-[11.5px] leading-relaxed text-text-3">{detail}</p>}
      {action && <div className="mt-1">{action}</div>}
    </div>
  );
}

export function LoadingState({ label = 'Loading' }: { label?: string }): React.JSX.Element {
  return (
    <div className="flex h-full min-h-[140px] flex-col items-center justify-center gap-3" role="status">
      <div className="relative h-0.5 w-28 overflow-hidden rounded-full bg-surface-4 anim-sweep" />
      <p className="text-[11.5px] tracking-wide text-text-3">{label}</p>
    </div>
  );
}

/** Renders a value, or a stated reason it is missing. Never a zero or a dash. */
export function ValueOrUnavailable({
  value,
  reason,
  className,
}: {
  value: ReactNode | null | undefined;
  reason: string;
  className?: string;
}): React.JSX.Element {
  if (value === null || value === undefined || value === '') {
    return (
      <span className={cx('text-[11.5px] text-text-3 italic', className)} title={reason}>
        {reason}
      </span>
    );
  }
  return <span className={className}>{value}</span>;
}

// ---------------------------------------------------------------------------

export function Field({
  label,
  children,
  mono,
  hint,
}: {
  label: string;
  children: ReactNode;
  mono?: boolean;
  hint?: string;
}): React.JSX.Element {
  return (
    <div className="min-w-0">
      <div className="eyebrow mb-0.5 flex items-center gap-1">
        {label}
        {hint && (
          // A real icon rather than the ⓘ character: the glyph rendered at
          // 1.7:1 against the panel and is unavailable to a screen reader
          // unless it carries its own label.
          <span className="inline-flex cursor-help text-text-3" title={hint}>
            <Info size={10} aria-hidden />
            <span className="sr-only">{hint}</span>
          </span>
        )}
      </div>
      <div className={cx('truncate text-[12.5px] text-text-2', mono && 'num')}>{children}</div>
    </div>
  );
}

export function Divider({ label }: { label?: string }): React.JSX.Element {
  if (!label) return <hr className="border-line" />;
  return (
    <div className="flex items-center gap-2">
      <hr className="flex-1 border-line" />
      <span className="eyebrow">{label}</span>
      <hr className="flex-1 border-line" />
    </div>
  );
}

'use client';

import { useRouter } from 'next/navigation';
import { ArrowRight, CircleAlert, Info, Siren, TriangleAlert } from 'lucide-react';
import { useOps } from '@/state/ops-store';
import type { OperationalRecommendation } from '@/lib/types';
import { Button, cx } from '@/components/ui/primitives';

const TONE = {
  CRITICAL: { icon: Siren, colour: 'var(--color-critical-text)', rgb: '239 68 68', label: 'Critical' },
  WARNING: { icon: TriangleAlert, colour: 'var(--color-warning-text)', rgb: '245 158 11', label: 'Attention' },
  SUCCESS: { icon: CircleAlert, colour: 'var(--color-safe-text)', rgb: '34 197 94', label: 'Note' },
  INFO: { icon: Info, colour: 'var(--color-rescuer-text)', rgb: '6 182 212', label: 'Note' },
} as const;

/**
 * One recommendation, with its evidence.
 *
 * The `because` list is not decoration and is never collapsed behind a
 * disclosure: it is the difference between a decision-support tool and an
 * instruction a commander is expected to obey without understanding.
 */
export function RecommendationCard({
  recommendation,
  onAssign,
}: {
  recommendation: OperationalRecommendation;
  onAssign?: (emergencyId: string, teamId?: string) => void;
}): React.JSX.Element {
  const router = useRouter();
  const { selectEmergency, selectTeam, selectHospital, selectCluster, fit } = useOps();
  const tone = TONE[recommendation.severity];
  const Icon = tone.icon;

  const act = (action: OperationalRecommendation['actions'][number]): void => {
    switch (action.kind) {
      case 'VIEW_EMERGENCY':
        if (action.emergencyId) selectEmergency(action.emergencyId);
        router.push('/emergencies');
        break;
      case 'ASSIGN_TEAM':
        if (action.emergencyId) {
          selectEmergency(action.emergencyId);
          onAssign?.(action.emergencyId, action.teamId);
          if (!onAssign) router.push('/emergencies');
        }
        break;
      case 'VIEW_AREA':
        if (action.clusterId) {
          selectCluster(action.clusterId);
          fit('CLUSTER');
        }
        router.push('/map');
        break;
      case 'VIEW_TEAM':
        if (action.teamId) selectTeam(action.teamId);
        router.push('/teams');
        break;
      case 'VIEW_HOSPITAL':
        if (action.hospitalId) selectHospital(action.hospitalId);
        router.push('/hospitals');
        break;
      case 'VIEW_MAP':
        router.push('/map');
        break;
    }
  };

  return (
    <article
      className="rounded-md border bg-surface-2 p-3"
      style={{ borderColor: `rgb(${tone.rgb} / 0.3)` }}
    >
      <div className="flex items-start gap-2.5">
        <span
          className="mt-0.5 grid size-6 shrink-0 place-items-center rounded-sm"
          style={{ background: `rgb(${tone.rgb} / 0.15)` }}
        >
          <Icon size={13} style={{ color: tone.colour }} aria-hidden />
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span
              className="rounded-xs px-1 py-px text-[9.5px] font-bold tracking-wider uppercase"
              style={{ color: tone.colour, background: `rgb(${tone.rgb} / 0.14)` }}
            >
              {tone.label}
            </span>
          </div>

          <h3 className="mt-1 text-[13px] leading-snug font-semibold text-text">
            {recommendation.title}
          </h3>

          <ul className="mt-1.5 space-y-0.5">
            {recommendation.because.map((line, index) => (
              <li key={index} className="flex gap-1.5 text-[11px] leading-snug text-text-3">
                <span className="mt-[6px] size-1 shrink-0 rounded-full bg-line-2" aria-hidden />
                <span>{line}</span>
              </li>
            ))}
          </ul>

          <p
            className="mt-2 rounded-sm border-l-2 py-1 pl-2 text-[11.5px] leading-snug text-text-2"
            style={{ borderColor: tone.colour, background: 'var(--color-surface-3)' }}
          >
            {recommendation.suggestion}
          </p>

          {recommendation.actions.length > 0 && (
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {recommendation.actions.map((action, index) => (
                <Button
                  key={`${action.kind}-${index}`}
                  size="sm"
                  variant={index === 0 ? 'primary' : 'default'}
                  icon={index === 0 ? ArrowRight : undefined}
                  onClick={() => act(action)}
                >
                  {action.label}
                </Button>
              ))}
            </div>
          )}
        </div>
      </div>
    </article>
  );
}

/** Footnote for a list of recommendations. */
export function DecisionSupportNote({
  recommendations,
}: {
  recommendations: OperationalRecommendation[];
}): React.JSX.Element {
  // A rule can fire more than once — one card per cluster, per hospital, per
  // hazard. Counting cards as rules would report "12 of 8", so count the
  // distinct rule kinds instead.
  const rulesMatched = new Set(recommendations.map((r) => r.kind)).size;

  return (
    <p className={cx('text-[10px] leading-relaxed text-text-3')}>
      {recommendations.length === 0
        ? 'No rule currently matches the operating picture.'
        : `${rulesMatched} of ${TOTAL_RULES} rules matched, producing ${recommendations.length} ${
            recommendations.length === 1 ? 'recommendation' : 'recommendations'
          }.`}{' '}
      Deterministic rules over the current data — no machine learning is involved, and every
      recommendation states the evidence behind it.
    </p>
  );
}

/** Rules implemented in `generateRecommendations`. */
const TOTAL_RULES = 8;

import {
  Activity,
  Clock,
  FileText,
  Hospital,
  LayoutDashboard,
  Map,
  Network,
  RefreshCw,
  Route,
  Siren,
  Users,
  type LucideIcon,
} from 'lucide-react';

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Shown as a live count badge when the key resolves to a non-zero number. */
  countKey?: 'critical' | 'pending' | 'availableTeams';
}

/**
 * Two groups, in operational order: what a commander acts on, then what they
 * consult. The split is the whole point — a flat list of eleven items would
 * give a hospital directory the same weight as the SOS queue.
 */
export const PRIMARY_NAV: NavItem[] = [
  { href: '/', label: 'Overview', icon: LayoutDashboard },
  { href: '/map', label: 'Live Map', icon: Map },
  { href: '/emergencies', label: 'Emergencies', icon: Siren, countKey: 'pending' },
  { href: '/operations', label: 'Rescue Operations', icon: Route },
  { href: '/teams', label: 'Teams', icon: Users, countKey: 'availableTeams' },
  { href: '/hospitals', label: 'Hospitals', icon: Hospital },
  { href: '/reports', label: 'Reports', icon: Activity },
  { href: '/timeline', label: 'Timeline', icon: Clock },
];

export const SECONDARY_NAV: NavItem[] = [
  { href: '/field-reports', label: 'Field Reports', icon: FileText },
  { href: '/sync', label: 'Synchronisation', icon: RefreshCw },
  { href: '/network', label: 'Mesh Network', icon: Network },
];

'use client';

import type { ReactNode } from 'react';
import { useOps } from '@/state/ops-store';
import { ErrorState, LoadingState, Button } from '@/components/ui/primitives';
import { CommandHeader } from './CommandHeader';
import { Sidebar } from './Sidebar';
import { AlertStack } from './AlertStack';

export function AppShell({ children }: { children: ReactNode }): React.JSX.Element {
  const { status, error, mode, setMode, reload } = useOps();

  return (
    <div className="flex h-dvh min-h-0 flex-col bg-void">
      <CommandHeader />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 overflow-hidden">
          {status === 'loading' ? (
            <LoadingState label="Assembling operating picture" />
          ) : status === 'error' ? (
            <div className="grid h-full place-items-center p-6">
              <ErrorState
                title="Command Center connection unavailable"
                detail={
                  error ??
                  'The rescue data endpoint did not respond. No cached data is shown, because a stale operating picture is more dangerous than none.'
                }
                action={
                  <div className="flex gap-2">
                    <Button onClick={reload}>Retry connection</Button>
                    {mode === 'LIVE' && (
                      <Button variant="primary" onClick={() => setMode('DEMO')}>
                        Switch to demo data
                      </Button>
                    )}
                  </div>
                }
              />
            </div>
          ) : (
            children
          )}
        </main>
      </div>
      <AlertStack />
    </div>
  );
}

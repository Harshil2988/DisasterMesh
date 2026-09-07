'use client';

import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
import { Button, cx } from './primitives';

interface DialogProps {
  open: boolean;
  title: string;
  description?: string;
  children?: ReactNode;
  confirmLabel?: string;
  confirmVariant?: 'primary' | 'danger' | 'success';
  onConfirm?: () => void;
  onClose: () => void;
  wide?: boolean;
}

/**
 * Modal dialog with a focus trap.
 *
 * Used for anything a commander cannot casually undo — releasing a team,
 * invalidating a report — and for team assignment, where the choice deserves
 * the whole screen's attention for the two seconds it takes.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  children,
  confirmLabel,
  confirmVariant = 'primary',
  onConfirm,
  onClose,
  wide,
}: DialogProps): React.JSX.Element | null {
  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;
    previouslyFocused.current = document.activeElement as HTMLElement;

    const focusables = (): HTMLElement[] =>
      Array.from(
        panelRef.current?.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
        ) ?? [],
      ).filter((el) => !el.hasAttribute('disabled'));

    focusables()[0]?.focus();

    const onKey = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      const items = focusables();
      if (items.length === 0) return;
      const first = items[0];
      const last = items[items.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      previouslyFocused.current?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[1000] grid place-items-center p-6">
      <div
        className="absolute inset-0 bg-void/75 backdrop-blur-[2px]"
        onClick={onClose}
        aria-hidden
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        className={cx(
          'anim-in relative w-full overflow-hidden rounded-lg border border-line-2 bg-surface-2 shadow-[0_24px_64px_rgb(0_0_0/0.7)]',
          wide ? 'max-w-[560px]' : 'max-w-[420px]',
        )}
      >
        <header className="flex items-start justify-between gap-4 border-b border-line px-4 py-3">
          <div>
            <h2 id="dialog-title" className="text-[13.5px] font-semibold text-text">
              {title}
            </h2>
            {description && <p className="mt-0.5 text-[11.5px] text-text-3">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="shrink-0 text-text-3 transition-colors hover:text-text"
          >
            <X size={15} />
          </button>
        </header>

        {children && <div className="max-h-[52vh] overflow-y-auto p-4">{children}</div>}

        {onConfirm && (
          <footer className="flex justify-end gap-2 border-t border-line bg-surface-1 px-4 py-2.5">
            <Button onClick={onClose}>Cancel</Button>
            <Button variant={confirmVariant} onClick={onConfirm}>
              {confirmLabel ?? 'Confirm'}
            </Button>
          </footer>
        )}
      </div>
    </div>
  );
}

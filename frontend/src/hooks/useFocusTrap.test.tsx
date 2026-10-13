// Tests for useFocusTrap: Tab wraps inside the open dialog and never reaches the page behind it.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useRef } from 'react';
import { describe, expect, it } from 'vitest';
import { useFocusTrap } from './useFocusTrap';

function Page({ open }: { open: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  useFocusTrap(ref, open);
  return (
    <>
      <button type="button">Behind</button>
      <div ref={ref} role="dialog">
        <button type="button">First</button>
        <button type="button">Last</button>
      </div>
    </>
  );
}

describe('useFocusTrap', () => {
  it('wraps Tab and Shift+Tab inside the dialog while it is open', async () => {
    const user = userEvent.setup();
    render(<Page open />);
    screen.getByText('First').focus();
    await user.tab();
    expect(screen.getByText('Last')).toHaveFocus();
    await user.tab();
    expect(screen.getByText('First')).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByText('Last')).toHaveFocus();
  });

  it('leaves Tab alone when the dialog is closed', async () => {
    const user = userEvent.setup();
    render(<Page open={false} />);
    screen.getByText('Last').focus();
    await user.tab();
    expect(screen.getByText('Last')).not.toHaveFocus();
  });
});

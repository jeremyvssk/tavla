// The page's own confirm dialog: the action button says yes; Cancel, Escape and the scrim say no.
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { useConfirm } from './parts';

function Asker() {
  const [ask, dialog] = useConfirm();
  const [answer, setAnswer] = useState('none');
  return (
    <>
      <button type="button" onClick={async () => setAnswer(String(await ask({ title: 'Archive it?', action: 'Archive', danger: true })))}>Go</button>
      <p>answer: {answer}</p>
      {dialog}
    </>
  );
}

describe('useConfirm', () => {
  it('answers yes from the action button, and starts on Cancel', async () => {
    render(<Asker />);
    await userEvent.click(screen.getByRole('button', { name: 'Go' }));

    expect(screen.getByRole('alertdialog', { name: 'Archive it?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));

    expect(await screen.findByText('answer: true')).toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('answers no on Escape', async () => {
    render(<Asker />);
    await userEvent.click(screen.getByRole('button', { name: 'Go' }));
    await userEvent.keyboard('{Escape}');

    expect(await screen.findByText('answer: false')).toBeInTheDocument();
  });
});

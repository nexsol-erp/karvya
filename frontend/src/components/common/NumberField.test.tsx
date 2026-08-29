import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';

import { NumberField } from './NumberField';
import { renderWithProviders } from '../../test/render';

/** Drives the field the way a form does, so the value it reports is the value it keeps. */
function Harness({ decimal, negative }: { decimal?: boolean; negative?: boolean }) {
  const [value, setValue] = useState('');
  return (
    <>
      <NumberField
        label="Amount"
        value={value}
        onChange={setValue}
        decimal={decimal}
        negative={negative}
      />
      <output data-testid="held">{value}</output>
    </>
  );
}

const held = () => screen.getByTestId('held').textContent;

describe('NumberField', () => {
  it('keeps digits', async () => {
    renderWithProviders(<Harness />);
    await userEvent.type(screen.getByLabelText('Amount'), '1250');
    expect(held()).toBe('1250');
  });

  it('ignores letters and symbols instead of storing them', async () => {
    renderWithProviders(<Harness />);
    await userEvent.type(screen.getByLabelText('Amount'), '12abc!34');
    expect(held()).toBe('1234');
  });

  /**
   * The reason this filters on change rather than blocking keystrokes: a paste
   * arrives as one event, and key handlers routinely let it through.
   */
  it('rejects a pasted value that is not a number', async () => {
    renderWithProviders(<Harness />);
    const field = screen.getByLabelText('Amount');

    await userEvent.click(field);
    await userEvent.paste('not a number');
    expect(held()).toBe('');

    await userEvent.paste('4200');
    expect(held()).toBe('4200');
  });

  it('refuses a decimal point unless the field is a decimal one', async () => {
    renderWithProviders(<Harness />);
    await userEvent.type(screen.getByLabelText('Amount'), '12.5');
    expect(held()).toBe('125');
  });

  it('accepts one when it is', async () => {
    renderWithProviders(<Harness decimal />);
    await userEvent.type(screen.getByLabelText('Amount'), '1250.75');
    expect(held()).toBe('1250.75');
  });

  /** "12." has to survive long enough to become "12.5". */
  it('allows a trailing point on the way to a decimal', async () => {
    renderWithProviders(<Harness decimal />);
    const field = screen.getByLabelText('Amount');
    await userEvent.type(field, '12.');
    expect(held()).toBe('12.');
    await userEvent.type(field, '5');
    expect(held()).toBe('12.5');
  });

  it('refuses a second decimal point', async () => {
    renderWithProviders(<Harness decimal />);
    await userEvent.type(screen.getByLabelText('Amount'), '1.2.3');
    expect(held()).toBe('1.23');
  });

  it('refuses a minus unless negatives are allowed', async () => {
    renderWithProviders(<Harness />);
    await userEvent.type(screen.getByLabelText('Amount'), '-5');
    expect(held()).toBe('5');
  });

  /** Emptying it has to be possible, or a value can only be edited, never replaced. */
  it('can be cleared', async () => {
    renderWithProviders(<Harness />);
    const field = screen.getByLabelText('Amount');
    await userEvent.type(field, '99');
    await userEvent.clear(field);
    expect(held()).toBe('');
  });

  it('passes the error state through so a form can mark it', () => {
    const noop = vi.fn();
    renderWithProviders(
      <NumberField label="Amount" value="" onChange={noop} error helperText="Needs a number." />,
    );
    expect(screen.getByText('Needs a number.')).toBeInTheDocument();
    expect(screen.getByLabelText('Amount')).toHaveAttribute('aria-invalid', 'true');
  });
});

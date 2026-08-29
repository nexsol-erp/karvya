import TextField, { type TextFieldProps } from '@mui/material/TextField';

type Props = Omit<TextFieldProps, 'onChange' | 'value' | 'type'> & {
  value: string;
  onChange: (value: string) => void;
  /** Allow a decimal point. Off for counts, on for money. */
  decimal?: boolean;
  /** Allow a leading minus. Off almost everywhere: stock and prices cannot be negative. */
  negative?: boolean;
};

/**
 * A text field that only ever holds a number.
 *
 * <p>Filtered on change rather than by blocking keystrokes. Intercepting keys
 * looks equivalent and is not: it breaks pasting, breaks the numeric keypads
 * and autocomplete on phones, and breaks input methods that compose a
 * character over several events. Rejecting a whole value that is not a number
 * handles all of those the same way, because it only ever asks whether the
 * result is valid.
 *
 * <p>Not {@code type="number"}, either. That accepts "1e5" and "1-2", hides
 * what was typed when it cannot parse it - reporting an empty value while the
 * user is looking at text - and puts spinners on a field nobody wants to step
 * through one at a time.
 *
 * <p>An intermediate value is allowed through: "12." has to survive long
 * enough to become "12.5", and an empty field has to be typeable so a value
 * can be replaced rather than only edited.
 */
export function NumberField({ value, onChange, decimal = false, negative = false, ...rest }: Props) {
  const pattern = new RegExp(
    `^${negative ? '-?' : ''}\\d*${decimal ? '(\\.\\d*)?' : ''}$`,
  );

  return (
    <TextField
      {...rest}
      value={value}
      // the keypad on a phone, without the desktop spinners of type="number"
      inputMode={decimal ? 'decimal' : 'numeric'}
      onChange={(event) => {
        const next = event.target.value;
        // The pattern already admits the in-progress forms - empty, a lone "-"
        // when negatives are allowed, "12." when decimals are - so there is
        // nothing to special-case. Letting those through unconditionally was a
        // bug: typing "-5" into a field that forbids negatives left the minus
        // behind and swallowed the digit.
        if (pattern.test(next)) {
          onChange(next);
        }
      }}
    />
  );
}

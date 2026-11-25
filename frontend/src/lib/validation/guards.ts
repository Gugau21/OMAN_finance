const rfc3339DateTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;

const US_SYMBOL_REGEX = /^[A-Z]{1,5}([.-][A-Z])?$/;

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function isRfc3339DateTime(value: unknown): value is string {
  return (
    typeof value === 'string' && rfc3339DateTime.test(value) && !Number.isNaN(Date.parse(value))
  );
}

export function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

export function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0;
}

export function isSymbol(value: unknown): value is string {
  return typeof value === 'string' && US_SYMBOL_REGEX.test(value);
}

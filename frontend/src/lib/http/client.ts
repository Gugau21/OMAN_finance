import { HttpError } from './errors';

export type HttpRequestOptions = {
  signal?: AbortSignal;
};

export type HttpJsonOptions = HttpRequestOptions & {
  resourceName: string;
};

export async function getJson(
  path: string,
  options: HttpJsonOptions,
): Promise<unknown> {
  const response = options.signal
    ? await fetch(path, { signal: options.signal })
    : await fetch(path);

  if (!response.ok) {
    throw new HttpError(
      response.status,
      response.statusText,
      `Failed to fetch ${options.resourceName}: ${response.status} ${response.statusText}`,
    );
  }

  return (await response.json()) as unknown;
}

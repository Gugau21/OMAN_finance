export class HttpError extends Error {
  readonly status: number;
  readonly statusText: string;

  constructor(status: number, statusText: string, message: string) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.statusText = statusText;
  }
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="error-banner">{message}</div>;
}

export function errorMessage(err: unknown): string {
  if (err instanceof Error) return err.message;
  return "Ein unerwarteter Fehler ist aufgetreten.";
}

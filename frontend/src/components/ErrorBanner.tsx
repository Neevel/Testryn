export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="error-banner" role="alert">
      <span aria-hidden="true">⚠</span>
      <span>{message}</span>
    </div>
  );
}

export function errorMessage(err: unknown): string {
  if (err instanceof Error && "status" in err && (err as { status?: number }).status === 401) {
    return "Authentication is required. Enter an API token on the Settings page to continue.";
  }
  if (err instanceof Error && err.message) return err.message;
  return "Something went wrong. Please try again.";
}

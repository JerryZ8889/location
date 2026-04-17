import "server-only";

const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL;
const SUPABASE_SECRET_KEY = process.env.SUPABASE_SECRET_KEY;
const SUPABASE_SCHEMA = process.env.SUPABASE_SCHEMA ?? "location";

function requireEnv(name: string, value: string | undefined) {
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return value;
}

function buildUrl(path: string, query?: URLSearchParams) {
  const baseUrl = requireEnv("NEXT_PUBLIC_SUPABASE_URL", SUPABASE_URL);
  const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
  const url = new URL(`/rest/v1/${normalizedPath}`, baseUrl);

  if (query) {
    url.search = query.toString();
  }

  return url;
}

interface SupabaseRequestOptions {
  body?: unknown;
  method?: "GET" | "POST" | "PATCH";
  prefer?: string;
  query?: URLSearchParams;
  schema?: string;
}

export class SupabaseRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly details: unknown
  ) {
    super(message);
    this.name = "SupabaseRequestError";
  }
}

export async function supabaseRequest<T>(
  path: string,
  {
    body,
    method = "GET",
    prefer,
    query,
    schema = SUPABASE_SCHEMA
  }: SupabaseRequestOptions = {}
) {
  const secretKey = requireEnv("SUPABASE_SECRET_KEY", SUPABASE_SECRET_KEY);
  const headers = new Headers({
    apikey: secretKey
  });

  if (method === "GET") {
    headers.set("Accept-Profile", schema);
  } else {
    headers.set("Accept-Profile", schema);
    headers.set("Content-Profile", schema);
    headers.set("Content-Type", "application/json");
  }

  if (prefer) {
    headers.set("Prefer", prefer);
  }

  const response = await fetch(buildUrl(path, query), {
    body: body == null ? undefined : JSON.stringify(body),
    headers,
    method,
    cache: "no-store"
  });

  if (!response.ok) {
    let details: unknown = null;

    try {
      details = await response.json();
    } catch {
      details = await response.text();
    }

    throw new SupabaseRequestError(
      `Supabase request failed for ${method} ${path}`,
      response.status,
      details
    );
  }

  if (response.status === 204) {
    return null as T;
  }

  const text = await response.text();

  if (text.trim().length === 0) {
    return null as T;
  }

  return JSON.parse(text) as T;
}

export function buildSelectQuery(select: string, extra?: Record<string, string>) {
  const query = new URLSearchParams({
    select
  });

  if (extra) {
    for (const [key, value] of Object.entries(extra)) {
      query.set(key, value);
    }
  }

  return query;
}

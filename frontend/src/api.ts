export type Me = { name: string | null; email: string | null }

// Dev builds point sign-in links at Spring Boot directly (see .env.development); production is same-origin.
export const apiOrigin: string = import.meta.env.VITE_API_ORIGIN ?? ''

export async function fetchMe(): Promise<Me | null> {
  const res = await fetch('/api/me', { credentials: 'include' })
  if (res.status === 401) return null
  if (!res.ok) throw new Error(`GET /api/me failed with ${res.status}`)
  return res.json()
}

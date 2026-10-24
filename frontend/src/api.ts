export type Me = {
  id: number
  name: string
  email: string
  provider: string
  hasProfile: boolean
  isReferrer: boolean
}

export type Profile = {
  pitch: string | null
  experienceLevel: string
  skills: string[]
  rolesWanted: string[]
  githubUrl: string | null
  linkedinUrl: string | null
  phoneNumber: string | null
  hideFromMyCompany: boolean
  exists: boolean
}

export type Company = {
  id: number
  name: string
  domain: string
  sponsor: boolean
  wishlisted: boolean
}
export type Companies = {
  wishlistCount: number
  wishlistMax: number
  referralRequestsLeft: number
  referralRequestsMax: number
  referralBonusCredits: number
  companies: Company[]
}

export type SeekerResponse = {
  responseId: number
  status: string
  referrerName: string
  referrerTitle: string | null
  referrerEmail: string | null
  referrerPhone: string | null
  acceptedAt: string | null
  submittedAt: string | null
  confirmedAt: string | null
}

export type SeekerReferralRequest = {
  id: number
  companyName: string
  roleTitle: string
  jobUrl: string | null
  seekerNote: string | null
  status: string
  createdAt: string
  expiresAt: string
  pendingCount: number
  declinedCount: number
  responses: SeekerResponse[]
}

export type ReferralRequestInboxItem = {
  responseId: number
  requestId: number
  companyName: string
  roleTitle: string
  jobUrl: string | null
  seekerName: string
  experienceLevel: string | null
  skills: string[]
  rolesWanted: string[]
  referralRolePreferences: string[]
  pitch: string | null
  githubUrl: string | null
  linkedinUrl: string | null
  seekerNote: string | null
  canSharePhone: boolean
  seekerEmail: string | null
  seekerPhone: string | null
  status: string
  createdAt: string
  acceptedAt: string | null
  submittedAt: string | null
  remindersSent: number
}

export type Conversation = {
  id: number
  status: 'pending' | 'accepted' | 'declined' | 'completed'
  initiatedBy: 'seeker' | 'referrer'
  myRole: 'seeker' | 'referrer'
  messageCount: number
  otherId: number
  otherName: string
  otherTitle: string | null
  companyName: string | null
  note: string | null
  lastMessage: string | null
}
export type Message = { id: number; senderId: number; mine: boolean; body: string; sentAt: string }
export type Thread = { conversation: Conversation; messageCap: number; messages: Message[] }

export type Referral = {
  id: number
  companyName: string
  referrerName: string
  roleTitle: string | null
  status: 'submitted' | 'under_review' | 'interview' | 'closed'
  updatedAt: string
}

export type Badge = { name: string; token: string }

export type ReferrerSettings = {
  verified: boolean
  workEmail: string | null
  companyName: string | null
  openToMeet: boolean
  slotCapacity: number
  referralLimit: number
  acceptsReferralRequests: boolean
  referralRolePreferences: string[]
}

// Dev builds point sign-in links at Spring Boot directly (see .env.development); production is same-origin.
export const apiOrigin: string = import.meta.env.VITE_API_ORIGIN ?? ''

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function csrfToken(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = init.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (init.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken()
  const res = await fetch(path, {
    method,
    headers,
    credentials: 'include',
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })
  if (!res.ok) {
    let message = 'Something went wrong. Please try again.'
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch {
      /* keep the generic message */
    }
    throw new ApiError(res.status, message)
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>('/api/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

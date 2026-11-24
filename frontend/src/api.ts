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
  portfolioUrl: string | null
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
  canContinueWithOtherReferrers: boolean
  otherReferrerCount: number
  creditReturned: boolean
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
  portfolioUrl: string | null
  seekerNote: string | null
  canSharePhone: boolean
  seekerEmail: string | null
  seekerPhone: string | null
  resumeAvailable: boolean
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

export type ResumeInfo = {
  filename: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export type ReferrerSettings = {
  verified: boolean
  workEmail: string | null
  companyName: string | null
  openToMeet: boolean
  slotCapacity: number
  referralLimit: number
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

export async function uploadResume(file: File): Promise<void> {
  const form = new FormData()
  form.set('file', file)
  const res = await fetch('/api/me/resume', {
    method: 'POST',
    credentials: 'include',
    headers: { 'X-XSRF-TOKEN': csrfToken() },
    body: form,
  })
  if (!res.ok) {
    let message = 'Could not upload the resume.'
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch {
    }
    throw new ApiError(res.status, message)
  }
}

export async function deleteResume(): Promise<void> {
  const res = await fetch('/api/me/resume', {
    method: 'DELETE',
    credentials: 'include',
    headers: { 'X-XSRF-TOKEN': csrfToken() },
  })
  if (!res.ok) {
    let message = 'Could not remove the resume.'
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch {
    }
    throw new ApiError(res.status, message)
  }
}

export async function downloadPdf(path: string, fallbackFilename: string): Promise<void> {
  const res = await fetch(path, { credentials: 'include' })
  if (!res.ok) {
    let message = 'Could not download the PDF.'
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch {
    }
    throw new ApiError(res.status, message)
  }
  const blob = await res.blob()
  const objectUrl = URL.createObjectURL(blob)
  const disposition = res.headers.get('Content-Disposition')
  const filenameMatch = disposition?.match(/filename="?([^";]+)"?/i)
  const filename = filenameMatch?.[1] ?? fallbackFilename
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
}

export async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>('/api/me')
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null
    throw e
  }
}

export type AppNotification = {
  id: number
  type: string
  payload: Record<string, unknown>
  read: boolean
  createdAt: string
}

export async function fetchNotifications(): Promise<AppNotification[]> {
  return api<AppNotification[]>('/api/notifications')
}

export async function fetchUnreadCount(): Promise<number> {
  const res = await api<{ count: number }>('/api/notifications/unread-count')
  return res.count
}

export async function markNotificationRead(id: number): Promise<void> {
  await api<void>(`/api/notifications/${id}/read`, { method: 'POST' })
}

export async function markAllNotificationsRead(): Promise<void> {
  await api<void>('/api/notifications/read-all', { method: 'POST' })
}

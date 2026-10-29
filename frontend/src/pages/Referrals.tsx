import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, Inbox, Send } from 'lucide-react'
import { useState } from 'react'
import { api, type ReferralRequestInboxItem, type SeekerReferralRequest } from '../api'
import { Avatar, Button, Card, Chip, EmptyState, ErrorNote, PageTitle, Spinner } from '../ui'

type RequestAction =
  | { kind: 'accept'; requestId: number; shareReferrerEmail: boolean; shareReferrerPhone: boolean }
  | { kind: 'decline' | 'cancel' | 'submitted'; requestId: number }
  | { kind: 'confirm'; requestId: number; responseId: number }

function label(status: string) {
  const values: Record<string, { text: string; tone: number }> = {
    open: { text: 'Waiting for responses', tone: 2 },
    in_progress: { text: 'Referrers working', tone: 3 },
    no_acceptances: { text: 'No acceptances yet', tone: 1 },
    pending: { text: 'Awaiting your response', tone: 2 },
    accepted: { text: 'Accepted', tone: 3 },
    declined: { text: 'Declined', tone: 1 },
    expired: { text: 'Expired', tone: 1 },
    submitted: { text: 'Submitted', tone: 0 },
    confirmed: { text: 'Confirmed', tone: 3 },
    closed: { text: 'Closed', tone: 1 },
  }
  return values[status] ?? { text: status, tone: 0 }
}

function SeekerRequestCard({ request, act, pending }: {
  request: SeekerReferralRequest
  act: (action: RequestAction) => void
  pending: boolean
}) {
  const requestStatus = request.status === 'closed' && request.responses.length > 0
    ? { text: 'Invitations closed', tone: 3 }
    : label(request.status)
  return (
    <Card className="space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-bold">{request.companyName}</p>
          <p className="text-sm text-muted">{request.roleTitle}</p>
          {request.jobUrl && <a className="mt-1 inline-block text-sm text-gblue underline" href={request.jobUrl} target="_blank" rel="noreferrer">Job posting</a>}
        </div>
        <Chip tone={requestStatus.tone}>{requestStatus.text}</Chip>
      </div>
      {request.seekerNote && <p className="whitespace-pre-wrap text-sm">{request.seekerNote}</p>}
      {(request.status === 'open' || request.status === 'no_acceptances') && (
        <p className="text-xs text-muted">{request.pendingCount} awaiting · {request.declinedCount} declined. Unanswered requests expire {new Date(request.expiresAt).toLocaleDateString()} and return one request to your allowance.</p>
      )}
      {request.responses.length > 0 && (
        <div className="space-y-2 border-t border-line pt-3">
          <p className="text-xs font-bold uppercase tracking-wide text-muted">Employees who accepted</p>
          {request.responses.map((response) => {
            const responseStatus = label(response.status)
            return (
              <div key={response.responseId} className="rounded-2xl bg-page p-3">
                <div className="flex items-center gap-2">
                  <Avatar name={response.referrerName} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold">{response.referrerName}</p>
                    {response.referrerTitle && <p className="text-xs text-muted">{response.referrerTitle}</p>}
                  </div>
                  <Chip tone={responseStatus.tone}>{responseStatus.text}</Chip>
                </div>
                {(response.referrerEmail || response.referrerPhone) && (
                  <div className="mt-2 space-y-1 text-sm">
                    {response.referrerEmail && <p>Email: <a className="text-gblue underline" href={`mailto:${response.referrerEmail}`}>{response.referrerEmail}</a></p>}
                    {response.referrerPhone && <p>Phone: <a className="text-gblue underline" href={`tel:${response.referrerPhone}`}>{response.referrerPhone}</a></p>}
                  </div>
                )}
                {response.submittedAt && response.status === 'submitted' && (
                  <Button className="mt-3 w-full" disabled={pending} onClick={() => act({ kind: 'confirm', requestId: request.id, responseId: response.responseId })}>
                    Confirm that they submitted your referral
                  </Button>
                )}
              </div>
            )
          })}
        </div>
      )}
      {(request.status === 'open' || request.status === 'no_acceptances') && request.responses.length === 0 && (
        <Button variant="outline" disabled={pending} onClick={() => act({ kind: 'cancel', requestId: request.id })}>Cancel request</Button>
      )}
    </Card>
  )
}

function ReferrerInboxCard({ item, act, pending }: {
  item: ReferralRequestInboxItem
  act: (action: RequestAction) => void
  pending: boolean
}) {
  const [shareEmail, setShareEmail] = useState(false)
  const [sharePhone, setSharePhone] = useState(false)
  const [packetPreviewOpen, setPacketPreviewOpen] = useState(false)
  const [packetCopyMessage, setPacketCopyMessage] = useState('')
  const status = label(item.status)
  const referralPacket = [
    'Referral details',
    `Company: ${item.companyName}`,
    `Role: ${item.roleTitle}`,
    item.jobUrl ? `Job posting: ${item.jobUrl}` : undefined,
    '',
    'Candidate profile',
    `Name: ${item.seekerName}`,
    item.experienceLevel ? `Experience: ${item.experienceLevel}` : undefined,
    item.pitch ? `Pitch: ${item.pitch}` : undefined,
    item.skills.length > 0 ? `Skills: ${item.skills.join(', ')}` : undefined,
    item.rolesWanted.length > 0 ? `Interested in: ${item.rolesWanted.join(', ')}` : undefined,
    item.githubUrl ? `GitHub: ${item.githubUrl}` : undefined,
    item.linkedinUrl ? `LinkedIn: ${item.linkedinUrl}` : undefined,
    item.seekerEmail ? `Email: ${item.seekerEmail}` : undefined,
    item.seekerPhone ? `Phone: ${item.seekerPhone}` : undefined,
    item.seekerNote ? `Note: ${item.seekerNote}` : undefined,
  ].filter((line): line is string => line !== undefined).join('\n')

  const copyReferralPacket = async () => {
    try {
      await navigator.clipboard.writeText(referralPacket)
      setPacketCopyMessage('Referral packet copied.')
    } catch {
      setPacketCopyMessage('Could not access the clipboard. Select and copy the preview text.')
    }
  }

  return (
    <Card className="space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-bold">{item.companyName}</p>
          <p className="text-sm text-muted">{item.roleTitle}</p>
        </div>
        <Chip tone={status.tone}>{status.text}</Chip>
      </div>
      {item.jobUrl && <a className="text-sm text-gblue underline" href={item.jobUrl} target="_blank" rel="noreferrer">Job posting</a>}
      <div className="flex items-center gap-3 border-t border-line pt-3">
        <Avatar name={item.seekerName} size="sm" />
        <div>
          <p className="font-semibold">{item.seekerName}</p>
          {item.experienceLevel && <p className="text-xs text-muted">{item.experienceLevel} experience</p>}
        </div>
      </div>
      {item.pitch && <p className="text-sm">{item.pitch}</p>}
      {item.rolesWanted.length > 0 && <p className="text-sm text-muted">Interested in: {item.rolesWanted.join(', ')}</p>}
      {item.referralRolePreferences.length > 0 && <p className="text-sm text-muted">Your referral areas: {item.referralRolePreferences.join(', ')}</p>}
      {(item.githubUrl || item.linkedinUrl) && (
        <div className="flex flex-wrap gap-3 text-sm">
          {item.githubUrl && <a className="text-gblue underline" href={item.githubUrl} target="_blank" rel="noreferrer">GitHub profile</a>}
          {item.linkedinUrl && <a className="text-gblue underline" href={item.linkedinUrl} target="_blank" rel="noreferrer">LinkedIn profile</a>}
        </div>
      )}
      {item.skills.length > 0 && <div className="flex flex-wrap gap-1.5">{item.skills.map((skill) => <Chip key={skill}>{skill}</Chip>)}</div>}
      {item.status === 'pending' && (
        <div className="space-y-3 border-t border-line pt-3">
          <p className="text-xs text-muted">Your contact details stay private unless you choose to share them. The seeker’s contact is shown only after you accept and only if they opted in.</p>
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={shareEmail} onChange={(event) => setShareEmail(event.target.checked)} className="mt-1 accent-gblue" />
            <span>Share my verified work email with this seeker if I accept.</span>
          </label>
          {item.canSharePhone && (
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" checked={sharePhone} onChange={(event) => setSharePhone(event.target.checked)} className="mt-1 accent-gblue" />
              <span>Share my profile phone number with this seeker if I accept.</span>
            </label>
          )}
          <div className="grid grid-cols-2 gap-2">
            <Button variant="outline" disabled={pending} onClick={() => act({ kind: 'decline', requestId: item.requestId })}>Decline</Button>
            <Button disabled={pending} onClick={() => act({ kind: 'accept', requestId: item.requestId, shareReferrerEmail: shareEmail, shareReferrerPhone: sharePhone })}>Accept</Button>
          </div>
        </div>
      )}
      {item.status === 'accepted' && (
        <div className="space-y-3 border-t border-line pt-3">
          {(item.seekerEmail || item.seekerPhone) && (
            <div className="space-y-1 text-sm">
              {item.seekerEmail && <p>Email: <a className="text-gblue underline" href={`mailto:${item.seekerEmail}`}>{item.seekerEmail}</a></p>}
              {item.seekerPhone && <p>Phone: <a className="text-gblue underline" href={`tel:${item.seekerPhone}`}>{item.seekerPhone}</a></p>}
            </div>
          )}
          <Button
            variant="outline"
            className="w-full"
            onClick={() => {
              setPacketPreviewOpen((open) => !open)
              setPacketCopyMessage('')
            }}
          >
            {packetPreviewOpen ? 'Hide referral packet' : 'Preview referral packet'}
          </Button>
          {packetPreviewOpen && (
            <div className="space-y-2 border-l-2 border-gblue pl-3">
              <p className="text-xs text-muted">Review this summary before pasting it into your employer’s referral system.</p>
              <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-words font-sans text-xs">{referralPacket}</pre>
              {packetCopyMessage && <p role="status" aria-live="polite" className="text-xs text-muted">{packetCopyMessage}</p>}
              <Button className="w-full" onClick={() => void copyReferralPacket()}>
                <span className="inline-flex items-center gap-2"><Copy size={16} />Copy referral packet</span>
              </Button>
            </div>
          )}
          <Button disabled={pending} className="w-full" onClick={() => act({ kind: 'submitted', requestId: item.requestId })}>Mark referral submitted</Button>
        </div>
      )}
      {item.status === 'submitted' && <p className="text-sm text-muted">Waiting for the seeker to confirm your submission. Reminders sent: {item.remindersSent} of 3.</p>}
      {item.seekerNote && <p className="rounded-2xl bg-page p-3 text-sm">{item.seekerNote}</p>}
    </Card>
  )
}

export default function ReferralsPage() {
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState<'sent' | 'inbox'>('sent')
  const seekerRequests = useQuery({
    queryKey: ['seeker-referral-requests'],
    queryFn: () => api<SeekerReferralRequest[]>('/api/referral-requests'),
    refetchInterval: 10000,
  })
  const inbox = useQuery({
    queryKey: ['referral-request-inbox'],
    queryFn: () => api<ReferralRequestInboxItem[]>('/api/referral-requests/inbox'),
    refetchInterval: 10000,
  })
  const action = useMutation({
    mutationFn: async (input: RequestAction) => {
      if (input.kind === 'accept') {
        return api(`/api/referral-requests/${input.requestId}/accept`, {
          method: 'POST',
          body: { shareReferrerEmail: input.shareReferrerEmail, shareReferrerPhone: input.shareReferrerPhone },
        })
      }
      if (input.kind === 'decline' || input.kind === 'cancel' || input.kind === 'submitted') {
        return api(`/api/referral-requests/${input.requestId}/${input.kind}`, { method: 'POST' })
      }
      if (input.kind === 'confirm') {
        return api(`/api/referral-requests/${input.requestId}/responses/${input.responseId}/confirm`, { method: 'POST' })
      }
      throw new Error('Unknown referral request action.')
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['seeker-referral-requests'] })
      await qc.invalidateQueries({ queryKey: ['referral-request-inbox'] })
      await qc.invalidateQueries({ queryKey: ['companies'] })
    },
  })
  if (seekerRequests.isPending || inbox.isPending) return <Spinner />
  if (seekerRequests.isError || inbox.isError || !seekerRequests.data || !inbox.data) {
    return <ErrorNote message="Could not load referral requests." />
  }
  const pendingInboxCount = inbox.data.filter((item) => item.status === 'pending').length
  return (
    <div>
      <PageTitle title="Referrals" subtitle="Track requests you sent and requests you can help with." />
      <ErrorNote message={action.error?.message ?? null} />
      <div role="tablist" aria-label="Referral request views" className="mb-4 grid grid-cols-2 gap-1 rounded-2xl border border-line bg-page p-1">
        <button
          id="sent-requests-tab"
          type="button"
          role="tab"
          aria-selected={activeTab === 'sent'}
          aria-controls="sent-requests-panel"
          onClick={() => setActiveTab('sent')}
          className={`flex min-h-11 items-center justify-center gap-2 rounded-xl px-2 text-sm font-semibold transition ${activeTab === 'sent' ? 'bg-white text-gblue shadow-sm' : 'text-muted hover:bg-white/60'}`}
        >
          <Send size={16} />
          <span>Requests I sent</span>
          <span className="text-xs tabular-nums">{seekerRequests.data.length}</span>
        </button>
        <button
          id="received-requests-tab"
          type="button"
          role="tab"
          aria-selected={activeTab === 'inbox'}
          aria-controls="received-requests-panel"
          onClick={() => setActiveTab('inbox')}
          className={`flex min-h-11 items-center justify-center gap-2 rounded-xl px-2 text-sm font-semibold transition ${activeTab === 'inbox' ? 'bg-white text-ggreen shadow-sm' : 'text-muted hover:bg-white/60'}`}
        >
          <Inbox size={16} />
          <span>Requests for me</span>
          <span className="text-xs tabular-nums">{pendingInboxCount}</span>
        </button>
      </div>
      <section id="sent-requests-panel" role="tabpanel" aria-labelledby="sent-requests-tab" hidden={activeTab !== 'sent'} className="space-y-3">
        {seekerRequests.data.length === 0 ? (
          <EmptyState title="No referral requests yet" text="Choose a company and request referral help to get started." />
        ) : seekerRequests.data.map((request) => <SeekerRequestCard key={request.id} request={request} act={action.mutate} pending={action.isPending} />)}
      </section>
      <section id="received-requests-panel" role="tabpanel" aria-labelledby="received-requests-tab" hidden={activeTab !== 'inbox'} className="space-y-3">
        {inbox.data.length === 0 ? (
          <EmptyState title="No requests to review" text="When someone asks for referral help at your company, it will appear here." />
        ) : inbox.data.map((item) => <ReferrerInboxCard key={item.responseId} item={item} act={action.mutate} pending={action.isPending} />)}
      </section>
    </div>
  )
}

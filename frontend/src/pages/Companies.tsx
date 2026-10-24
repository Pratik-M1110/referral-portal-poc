import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Heart } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Companies, type Company, type Profile } from '../api'
import { Avatar, Button, Card, Chip, EmptyState, ErrorNote, PageTitle, Spinner } from '../ui'

function ReferralRequestSheet({ company, requestsLeft, onClose }: {
  company: Company
  requestsLeft: number
  onClose: () => void
}) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { data: profile } = useQuery({ queryKey: ['profile'], queryFn: () => api<Profile>('/api/me/profile') })
  const [roleTitle, setRoleTitle] = useState('')
  const [jobUrl, setJobUrl] = useState('')
  const [seekerNote, setSeekerNote] = useState('')
  const [shareProfile, setShareProfile] = useState(true)
  const [shareEmail, setShareEmail] = useState(false)
  const [sharePhone, setSharePhone] = useState(false)
  const create = useMutation({
    mutationFn: () => api('/api/referral-requests', {
      method: 'POST',
      body: { companyId: company.id, roleTitle, jobUrl, seekerNote, shareProfile, shareSeekerEmail: shareEmail, shareSeekerPhone: sharePhone },
    }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['companies'] })
      await qc.invalidateQueries({ queryKey: ['seeker-referral-requests'] })
      onClose()
      navigate('/referrals')
    },
  })

  return (
    <div className="fixed inset-0 z-20 flex items-end justify-center bg-ink/45 px-3 pb-3 sm:items-center" onClick={onClose}>
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="referral-request-title"
        className="rise max-h-[92vh] w-full max-w-[500px] overflow-y-auto rounded-3xl border border-line bg-white p-5 shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="referral-request-title" className="text-xl font-extrabold">Request a referral</h2>
            <p className="mt-1 text-sm text-muted">{company.name}</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close request form" className="rounded-full px-3 py-1 text-muted hover:bg-line/60">Close</button>
        </div>
        <p className="mt-3 text-sm text-muted">Your request goes to employees who opted in at this company. Accepting means someone is willing to help; it does not guarantee an opening, referral, or interview.</p>
        <form
          className="mt-4 space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate()
          }}
        >
          <div>
            <label className="text-sm font-semibold" htmlFor="role-title">Role you’re interested in</label>
            <input
              id="role-title"
              required
              maxLength={160}
              value={roleTitle}
              onChange={(event) => setRoleTitle(event.target.value)}
              placeholder="e.g. Backend Engineer"
              className="mt-1 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm outline-none focus:border-gblue"
            />
          </div>
          <div>
            <label className="text-sm font-semibold" htmlFor="job-url">Job link <span className="font-normal text-muted">(optional)</span></label>
            <input
              id="job-url"
              type="url"
              maxLength={500}
              value={jobUrl}
              onChange={(event) => setJobUrl(event.target.value)}
              placeholder="https://company.com/jobs/..."
              className="mt-1 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm outline-none focus:border-gblue"
            />
          </div>
          <div>
            <label className="text-sm font-semibold" htmlFor="request-context">A note for the referrers <span className="font-normal text-muted">(optional)</span></label>
            <textarea
              id="request-context"
              maxLength={1000}
              rows={3}
              value={seekerNote}
              onChange={(event) => setSeekerNote(event.target.value)}
              placeholder="What kind of help are you looking for?"
              className="mt-1 w-full resize-y rounded-xl border border-line bg-white px-3 py-2.5 text-sm outline-none focus:border-gblue"
            />
            <p className="text-right text-xs text-muted">{seekerNote.length}/1000</p>
          </div>
          <fieldset className="space-y-3 rounded-2xl bg-page p-3">
            <legend className="px-1 text-sm font-bold">Information sharing</legend>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" checked={shareProfile} onChange={(event) => setShareProfile(event.target.checked)} className="mt-1 accent-gblue" />
              <span>Share my profile summary with invited referrers (experience, skills, role interests, and pitch).</span>
            </label>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" checked={shareEmail} onChange={(event) => setShareEmail(event.target.checked)} className="mt-1 accent-gblue" />
              <span>Share my sign-in email only with employees who accept.</span>
            </label>
            {profile?.phoneNumber && (
              <label className="flex items-start gap-2 text-sm">
                <input type="checkbox" checked={sharePhone} onChange={(event) => setSharePhone(event.target.checked)} className="mt-1 accent-gblue" />
                <span>Share my phone number only with employees who accept.</span>
              </label>
            )}
          </fieldset>
          <p className="text-xs text-muted">Request allowance remaining: {requestsLeft}. One request covers this company and role, regardless of how many employees accept.</p>
          <ErrorNote message={create.error?.message ?? null} />
          <div className="flex gap-3">
            <Button variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={create.isPending || requestsLeft < 1 || !shareProfile} className="flex-1">
              {create.isPending ? 'Sending…' : 'Send request'}
            </Button>
          </div>
        </form>
      </section>
    </div>
  )
}

export default function CompaniesPage() {
  const qc = useQueryClient()
  const { data, isPending, isError } = useQuery({ queryKey: ['companies'], queryFn: () => api<Companies>('/api/companies') })
  const [target, setTarget] = useState<Company | null>(null)
  const toggle = useMutation({
    mutationFn: (company: Company) => api(`/api/wishlist/${company.id}`, { method: company.wishlisted ? 'DELETE' : 'PUT' }),
    onSettled: () => qc.invalidateQueries({ queryKey: ['companies'] }),
  })

  if (isPending) return <Spinner />
  if (isError || !data) return <ErrorNote message="Could not load companies." />

  return (
    <div>
      <PageTitle title="Companies" subtitle="Ask for help from employees who have opted in to referring candidates." />
      <div className="mb-4 flex flex-wrap gap-2">
        <Chip tone={0}>Wishlist {data.wishlistCount} of {data.wishlistMax}</Chip>
        <Chip tone={2}>{data.referralRequestsLeft} referral requests left</Chip>
        <Chip tone={1}>{data.referralRequestsMax} base per 30 days</Chip>
        {data.referralBonusCredits > 0 && <Chip tone={3}>{data.referralBonusCredits} bonus {data.referralBonusCredits === 1 ? 'credit' : 'credits'}</Chip>}
      </div>
      <ErrorNote message={toggle.error?.message ?? null} />
      {data.companies.length === 0 ? (
        <EmptyState title="No companies are accepting requests yet" text="Check back when employees at more companies opt in to help with referrals." />
      ) : (
        <div className="space-y-3">
          {data.companies.map((company) => (
            <Card key={company.id} className="flex items-center gap-3">
              <Avatar name={company.name} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-bold">{company.name}</p>
                <p className="truncate text-xs text-muted">{company.domain}</p>
                {company.sponsor && <span className="mt-1 inline-block rounded-full bg-gyellow/25 px-2 py-0.5 text-xs font-semibold text-yellow-700">Sponsor</span>}
              </div>
              <button
                type="button"
                onClick={() => toggle.mutate(company)}
                aria-label={company.wishlisted ? 'Remove from wishlist' : 'Add to wishlist'}
                className={`rounded-full p-2 transition ${company.wishlisted ? 'bg-gred/10 text-gred' : 'bg-line/60 text-muted hover:text-gred'}`}
              >
                <Heart size={18} fill={company.wishlisted ? 'currentColor' : 'none'} />
              </button>
              <Button onClick={() => setTarget(company)} disabled={data.referralRequestsLeft === 0} className="flex shrink-0 items-center gap-2 px-3">
                <span>Request</span><ArrowRight size={16} />
              </Button>
            </Card>
          ))}
        </div>
      )}
      {target && <ReferralRequestSheet company={target} requestsLeft={data.referralRequestsLeft} onClose={() => setTarget(null)} />}
    </div>
  )
}
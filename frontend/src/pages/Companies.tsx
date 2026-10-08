import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Heart, MapPin, Users } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Companies, type Company, type Person } from '../api'
import { Avatar, Button, Card, Chip, ErrorNote, PageTitle, Spinner } from '../ui'

const levelLabel: Record<string, string> = { student: 'Student', fresher: 'Fresher', '1-3': '1-3 yrs', '3-6': '3-6 yrs', '6+': '6+ yrs' }

function RequestSheet({ company, person, requestsLeft, onClose }: { company: Company; person: Person; requestsLeft: number; onClose: () => void }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [note, setNote] = useState('')
  const send = useMutation({
    mutationFn: () => api<{ id: number }>('/api/conversations', { method: 'POST', body: { referrerId: person.userId, note } }),
    onSuccess: async (res) => {
      await qc.invalidateQueries({ queryKey: ['companies'] })
      await qc.invalidateQueries({ queryKey: ['conversations'] })
      navigate(`/chats/${res.id}`)
    },
  })
  return (
    <div className="fixed inset-0 z-20 flex items-end justify-center bg-ink/40 px-3 pb-3 sm:items-center" onClick={onClose}>
      <div className="rise w-full max-w-[430px] rounded-3xl bg-white p-5 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-extrabold">Request to meet {person.name.split(' ')[0]}</h2>
        <p className="text-sm text-muted">{person.jobTitle} at {company.name}</p>
        <label htmlFor="note" className="mt-4 block text-sm font-bold">Why do you want to meet?</label>
        <textarea
          id="note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={200}
          rows={4}
          autoFocus
          className="mt-2 w-full resize-none rounded-2xl border border-line px-4 py-3 text-sm outline-none focus:border-gblue"
          placeholder="Say hello and what you would like to talk about"
        />
        <p className="text-right text-xs text-muted">{note.length}/200</p>
        <p className="mb-3 text-xs text-muted">Requests left: {requestsLeft}. You can chat once they accept.</p>
        <ErrorNote message={send.error?.message ?? null} />
        <div className="mt-3 grid grid-cols-2 gap-3">
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button onClick={() => send.mutate()} disabled={send.isPending || note.trim().length === 0}>
            {send.isPending ? 'Sending…' : 'Send request'}
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function CompaniesPage() {
  const qc = useQueryClient()
  const { data, isPending, isError } = useQuery({ queryKey: ['companies'], queryFn: () => api<Companies>('/api/companies') })
  const [target, setTarget] = useState<{ company: Company; person: Person } | null>(null)
  const toggle = useMutation({
    mutationFn: (c: Company) => api(`/api/wishlist/${c.id}`, { method: c.wishlisted ? 'DELETE' : 'PUT' }),
    onSettled: () => qc.invalidateQueries({ queryKey: ['companies'] }),
  })

  if (isPending) return <Spinner />
  if (isError || !data) return <ErrorNote message="Could not load companies." />

  return (
    <div>
      <PageTitle title="Companies attending" />
      <div className="mb-4 flex gap-2">
        <Chip tone={0}>Wishlist {data.wishlistCount} of {data.wishlistMax}</Chip>
        <Chip tone={2}>Requests left {data.requestsLeft} of {data.requestsMax}</Chip>
      </div>
      <ErrorNote message={toggle.error?.message ?? null} />
      <div className="space-y-4">
        {data.companies.map((c, i) => (
          <Card key={c.id} className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <Avatar name={c.name} />
                <div>
                  <p className="font-bold leading-tight">{c.name}</p>
                  <p className="flex items-center gap-1 text-xs text-muted">
                    {c.boothLabel ? <><MapPin size={12} /> {c.boothLabel}</> : 'Visiting the venue'}
                    {c.sponsor && <span className="ml-1 rounded-full bg-gyellow/25 px-2 py-0.5 font-semibold text-yellow-700">Sponsor</span>}
                  </p>
                </div>
              </div>
              <button
                onClick={() => toggle.mutate(c)}
                aria-label={c.wishlisted ? 'Remove from wishlist' : 'Add to wishlist'}
                className={`rounded-full p-2 transition ${c.wishlisted ? 'bg-gred/10 text-gred' : 'bg-line/60 text-muted hover:text-gred'}`}
              >
                <Heart size={18} fill={c.wishlisted ? 'currentColor' : 'none'} />
              </button>
            </div>

            <div className="flex items-center gap-2 text-xs text-muted">
              <Users size={14} /> {c.roles.length} open role{c.roles.length === 1 ? '' : 's'} · {c.people.length} {c.people.length === 1 ? 'person' : 'people'} open to meet
            </div>

            <div className="space-y-2">
              {c.roles.map((r, j) => (
                <div key={r.id} className="rounded-2xl bg-page px-3 py-2">
                  <p className="text-sm font-semibold">{r.title} {r.level && <span className="font-normal text-muted">· {levelLabel[r.level] ?? r.level}</span>}</p>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {r.skills.map((s, k) => <Chip key={s} tone={i + j + k}>{s}</Chip>)}
                  </div>
                </div>
              ))}
            </div>

            {c.people.map((p) => (
              <div key={p.userId} className="flex items-center justify-between gap-3 border-t border-line pt-3">
                <div className="flex items-center gap-3">
                  <Avatar name={p.name} size="sm" />
                  <div>
                    <p className="text-sm font-semibold leading-tight">{p.name}</p>
                    <p className="text-xs text-muted">{p.jobTitle}</p>
                  </div>
                </div>
                {p.fullyBooked ? (
                  <Chip tone={1}>Fully booked</Chip>
                ) : (
                  <Button onClick={() => setTarget({ company: c, person: p })} disabled={data.requestsLeft === 0}>Request meet</Button>
                )}
              </div>
            ))}
          </Card>
        ))}
      </div>
      {target && <RequestSheet {...target} requestsLeft={data.requestsLeft} onClose={() => setTarget(null)} />}
    </div>
  )
}

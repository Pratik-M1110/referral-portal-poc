import { useQuery } from '@tanstack/react-query'
import { api, type Referral } from '../api'
import { Avatar, Card, Chip, EmptyState, ErrorNote, PageTitle, Spinner, statusLabel } from '../ui'

export default function ReferralsPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['referrals'],
    queryFn: () => api<Referral[]>('/api/referrals?as=candidate'),
    refetchInterval: 10000,
  })
  if (isPending) return <Spinner />
  if (isError || !data) return <ErrorNote message="Could not load your referrals." />
  return (
    <div>
      <PageTitle title="My referrals" subtitle="Companies that have referred you. Updates as they respond." />
      {data.length === 0 && <EmptyState title="No referrals yet" text="When someone refers you at the event, it will show up here." />}
      <div className="space-y-3">
        {data.map((r) => {
          const s = statusLabel(r.status)
          return (
            <Card key={r.id} className="flex items-center gap-3">
              <Avatar name={r.companyName} />
              <div className="min-w-0 flex-1">
                <p className="font-bold leading-tight">{r.companyName}</p>
                <p className="truncate text-sm text-muted">Referred by {r.referrerName}{r.roleTitle ? ` for ${r.roleTitle}` : ''}</p>
              </div>
              <Chip tone={s.tone}>{s.label}</Chip>
            </Card>
          )
        })}
      </div>
    </div>
  )
}

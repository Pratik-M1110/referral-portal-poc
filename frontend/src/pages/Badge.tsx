import { useQuery } from '@tanstack/react-query'
import { QRCodeSVG } from 'qrcode.react'
import { api, type Badge, type Me } from '../api'
import { Avatar, Card, Chip, ErrorNote, PageTitle, Spinner } from '../ui'

export default function BadgePage({ me }: { me: Me }) {
  const { data, isPending, isError } = useQuery({ queryKey: ['badge'], queryFn: () => api<Badge>('/api/me/badge') })
  if (isPending) return <Spinner />
  if (isError || !data) return <ErrorNote message="Could not load your badge." />
  // The QR holds a link with a random token only, never your user id.
  const link = `${window.location.origin}/b/${data.token}`
  return (
    <div>
      <PageTitle title="Your badge" subtitle="Show this at the event. Referrers scan it to open your profile." />
      <Card className="flex flex-col items-center gap-4 py-8 text-center">
        <div className="rounded-3xl border-4 border-gblue/20 bg-white p-4">
          <QRCodeSVG value={link} size={208} fgColor="#202124" />
        </div>
        <div className="flex items-center gap-3">
          <Avatar name={data.name} />
          <p className="text-xl font-extrabold">{data.name}</p>
        </div>
        <div className="flex gap-2">
          <Chip tone={0}>Seeker</Chip>
          {me.isReferrer && <Chip tone={3}>Referrer</Chip>}
        </div>
        <p className="max-w-[260px] text-xs text-muted">This code holds only a random token, so it never reveals your account details.</p>
      </Card>
    </div>
  )
}

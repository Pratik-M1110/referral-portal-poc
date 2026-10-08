import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { api, type Conversation } from '../api'
import { Avatar, Button, Card, Chip, EmptyState, ErrorNote, PageTitle, Spinner, statusLabel } from '../ui'

export default function ChatsPage() {
  const qc = useQueryClient()
  const { data, isPending, isError } = useQuery({
    queryKey: ['conversations'],
    queryFn: () => api<Conversation[]>('/api/conversations'),
    refetchInterval: 5000,
  })
  const decide = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'accept' | 'decline' }) =>
      api(`/api/conversations/${id}/${action}`, { method: 'POST' }),
    onSettled: () => qc.invalidateQueries({ queryKey: ['conversations'] }),
  })

  if (isPending) return <Spinner />
  if (isError || !data) return <ErrorNote message="Could not load your chats." />

  return (
    <div>
      <PageTitle title="Chats and requests" subtitle="Each chat holds 20 messages in total." />
      <ErrorNote message={decide.error?.message ?? null} />
      {data.length === 0 && <EmptyState title="No chats yet" text="Request a meet from the Companies tab to start a conversation." />}
      <div className="space-y-3">
        {data.map((c) => {
          const s = statusLabel(c.status)
          const waitingOnMe = c.status === 'pending' && c.initiatedBy !== c.myRole
          return (
            <Card key={c.id}>
              <div className="flex items-center gap-3">
                <Avatar name={c.otherName} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold leading-tight">{c.otherName}{c.companyName && <span className="font-normal text-muted">, {c.companyName}</span>}</p>
                  <p className="truncate text-sm text-muted">
                    {c.lastMessage ?? (waitingOnMe ? c.note : c.status === 'pending' ? 'Waiting for a reply to your request.' : c.note)}
                  </p>
                </div>
                <Chip tone={s.tone}>{s.label}</Chip>
              </div>
              {waitingOnMe && (
                <div className="mt-3 grid grid-cols-2 gap-3">
                  <Button variant="outline" onClick={() => decide.mutate({ id: c.id, action: 'decline' })} disabled={decide.isPending}>Decline</Button>
                  <Button onClick={() => decide.mutate({ id: c.id, action: 'accept' })} disabled={decide.isPending}>Accept</Button>
                </div>
              )}
              {c.status === 'accepted' && (
                <Link to={`/chats/${c.id}`} className="mt-3 flex items-center justify-center gap-1 rounded-full border border-gblue/40 py-2.5 text-sm font-semibold text-gblue hover:bg-gblue/5">
                  Open chat <ChevronRight size={16} />
                </Link>
              )}
            </Card>
          )
        })}
      </div>
    </div>
  )
}

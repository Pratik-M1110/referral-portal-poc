import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Thread } from '../api'
import { Avatar, ErrorNote, Spinner } from '../ui'

export default function ChatPage() {
  const { id } = useParams()
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const [bannerDismissed, setBannerDismissed] = useState(() => localStorage.getItem('contactBanner') === 'off')
  const bottom = useRef<HTMLDivElement>(null)
  const { data, isPending, isError } = useQuery({
    queryKey: ['thread', id],
    queryFn: () => api<Thread>(`/api/conversations/${id}/messages`),
    refetchInterval: 4000,
  })
  const send = useMutation({
    mutationFn: (body: string) => api(`/api/conversations/${id}/messages`, { method: 'POST', body: { body } }),
    onSuccess: async () => {
      setText('')
      await qc.invalidateQueries({ queryKey: ['thread', id] })
      await qc.invalidateQueries({ queryKey: ['conversations'] })
    },
  })

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
  }, [data?.messages.length])

  if (isPending) return <Spinner />
  if (isError || !data) return <ErrorNote message="This chat is not available." />

  const { conversation: c, messageCap, messages } = data
  const full = c.messageCount >= messageCap
  const canWrite = c.status === 'accepted' && !full

  return (
    <div className="mx-auto flex h-screen max-w-[430px] flex-col">
      <header className="flex items-center gap-3 px-4 pb-3 pt-4">
        <Link to="/chats" aria-label="Back to chats" className="rounded-full p-2 hover:bg-line/60"><ArrowLeft size={20} /></Link>
        <Avatar name={c.otherName} size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate font-bold leading-tight">{c.otherName}</p>
          <p className="truncate text-xs text-muted">{c.otherTitle ?? ''}{c.companyName ? ` · ${c.companyName}` : ''}</p>
        </div>
        <span className="rounded-full bg-gblue/10 px-3 py-1 text-xs font-semibold text-gblue">{c.messageCount} of {messageCap} used</span>
      </header>

      <div className="flex-1 space-y-2 overflow-y-auto px-4 pb-2">
        {!bannerDismissed && (
          <div className="rise flex items-start justify-between gap-3 rounded-2xl bg-gyellow/20 px-4 py-3 text-sm text-yellow-900">
            <span>Share contact details only with people you are comfortable with.</span>
            <button
              onClick={() => {
                localStorage.setItem('contactBanner', 'off')
                setBannerDismissed(true)
              }}
              className="shrink-0 font-semibold"
            >
              Got it
            </button>
          </div>
        )}
        {messages.map((m) => (
          <div key={m.id} className={`flex ${m.mine ? 'justify-end' : 'justify-start'}`}>
            <p className={`max-w-[80%] whitespace-pre-wrap break-words rounded-3xl px-4 py-2.5 text-sm ${m.mine ? 'rounded-br-md bg-gblue text-white' : 'rounded-bl-md border border-line bg-white'}`}>
              {m.body}
            </p>
          </div>
        ))}
        <div ref={bottom} />
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (text.trim() && canWrite) send.mutate(text.trim())
        }}
        className="border-t border-line bg-white/80 px-4 py-3 backdrop-blur"
      >
        <ErrorNote message={send.error?.message ?? null} />
        <div className="mt-1 flex gap-2">
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={500}
            disabled={!canWrite}
            placeholder={full ? 'This chat has used all its messages' : canWrite ? 'Write a message' : 'Chat opens once accepted'}
            className="min-w-0 flex-1 rounded-full border border-line bg-white px-4 py-3 text-sm outline-none focus:border-gblue disabled:bg-line/40"
          />
          <button type="submit" disabled={!canWrite || send.isPending || !text.trim()} aria-label="Send" className="rounded-full bg-gblue p-3 text-white disabled:opacity-40">
            <Send size={18} />
          </button>
        </div>
      </form>
    </div>
  )
}

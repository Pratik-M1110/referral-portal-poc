import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  type AppNotification,
} from './api'
import { Spinner } from './ui'

const unreadKey = ['notifications', 'unread-count']
const listKey = ['notifications', 'list']

function text(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

/** Turns a stored notification type + payload into one readable line. */
function describe(n: AppNotification): string {
  const company = text(n.payload.company) ?? 'a company'
  const role = text(n.payload.roleTitle)
  switch (n.type) {
    case 'referral_request':
      return role ? `New referral request for ${role} at ${company}` : `New referral request at ${company}`
    case 'referral_accepted':
      return `Your referral request at ${company} was accepted`
    case 'referral_submitted':
      return `Your referral at ${company} was marked submitted`
    case 'referral_confirmed':
      return `Your referral at ${company} was confirmed`
    case 'referral_reminder':
      return role ? `Reminder: referral at ${company} for ${role}` : `Reminder: referral at ${company}`
    case 'referral_closed':
      return `Your referral at ${company} was closed`
    case 'referral_request_expired':
      return role ? `Your request for ${role} at ${company} expired` : `Your request at ${company} expired`
    default:
      return n.type.replace(/_/g, ' ')
  }
}

function timeAgo(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const seconds = Math.round((Date.now() - then) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days}d ago`
  return new Date(iso).toLocaleDateString()
}

export default function NotificationBell() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const { data: unread = 0 } = useQuery({
    queryKey: unreadKey,
    queryFn: fetchUnreadCount,
    refetchInterval: 30000,
  })

  const { data: items, isPending } = useQuery({
    queryKey: listKey,
    queryFn: fetchNotifications,
    enabled: open,
  })

  useEffect(() => {
    if (!open) return
    const onPointer = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const refresh = () => {
    qc.invalidateQueries({ queryKey: unreadKey })
    qc.invalidateQueries({ queryKey: listKey })
  }

  const readOne = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh })
  const readAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh })

  const openItem = (n: AppNotification) => {
    if (!n.read) readOne.mutate(n.id)
    setOpen(false)
    navigate('/referrals')
  }

  const hasUnread = Boolean(items?.some((n) => !n.read))

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        className="relative rounded-full p-2 text-muted hover:bg-line/60"
      >
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 inline-flex min-w-[1.1rem] items-center justify-center rounded-full bg-gred px-1 text-[0.65rem] font-bold leading-4 text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 max-w-[90vw] overflow-hidden rounded-3xl border border-line bg-white shadow-[0_8px_30px_rgba(60,64,67,0.18)]">
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <span className="text-sm font-bold">Notifications</span>
            {hasUnread && (
              <button
                type="button"
                onClick={() => readAll.mutate()}
                disabled={readAll.isPending}
                className="text-xs font-semibold text-gblue hover:underline disabled:opacity-50"
              >
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {isPending ? (
              <Spinner />
            ) : !items || items.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-muted">You&apos;re all caught up.</p>
            ) : (
              <ul>
                {items.map((n) => (
                  <li key={n.id}>
                    <button
                      type="button"
                      onClick={() => openItem(n)}
                      className={`flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-line/40 ${n.read ? '' : 'bg-gblue/5'}`}
                    >
                      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.read ? 'bg-transparent' : 'bg-gblue'}`} />
                      <span className="min-w-0 flex-1">
                        <span className={`block text-sm ${n.read ? 'text-muted' : 'font-semibold text-ink'}`}>
                          {describe(n)}
                        </span>
                        <span className="mt-0.5 block text-xs text-muted">{timeAgo(n.createdAt)}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

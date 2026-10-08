import type { ReactNode } from 'react'

const dotColors = ['bg-gblue', 'bg-gred', 'bg-gyellow', 'bg-ggreen']
const chipTones = [
  'bg-gblue/10 text-gblue',
  'bg-gred/10 text-gred',
  'bg-gyellow/20 text-yellow-700',
  'bg-ggreen/10 text-ggreen',
]
const avatarTones = ['bg-gblue', 'bg-gred', 'bg-gyellow', 'bg-ggreen']

export function Logo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  const dot = size === 'lg' ? 'h-4 w-4' : 'h-2.5 w-2.5'
  return (
    <span className="inline-flex items-center gap-2 font-bold tracking-tight">
      <span className="flex items-center gap-1" aria-hidden>
        {dotColors.map((c) => (
          <span key={c} className={`${dot} rounded-full ${c}`} />
        ))}
      </span>
      <span className={size === 'lg' ? 'text-3xl' : 'text-lg'}>Referral Portal</span>
    </span>
  )
}

export function Chip({ children, tone = 0 }: { children: ReactNode; tone?: number }) {
  return (
    <span className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${chipTones[tone % chipTones.length]}`}>
      {children}
    </span>
  )
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' | 'lg' }) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
  const tone = avatarTones[(name.charCodeAt(0) || 0) % avatarTones.length]
  const dim = size === 'lg' ? 'h-14 w-14 text-lg' : size === 'sm' ? 'h-8 w-8 text-xs' : 'h-11 w-11 text-sm'
  return (
    <span className={`${dim} ${tone} inline-flex shrink-0 items-center justify-center rounded-full font-bold text-white`}>
      {initials || '?'}
    </span>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rise rounded-3xl border border-line bg-white p-4 shadow-[0_2px_14px_rgba(60,64,67,0.07)] ${className}`}>
      {children}
    </div>
  )
}

export function PageTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="mb-4">
      <h1 className="text-2xl font-extrabold tracking-tight">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
    </div>
  )
}

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) return null
  return <p role="alert" className="rounded-2xl bg-gred/10 px-4 py-3 text-sm font-medium text-gred">{message}</p>
}

export function Spinner() {
  return (
    <div className="flex justify-center py-10" role="status" aria-label="Loading">
      <span className="h-8 w-8 animate-spin rounded-full border-4 border-line border-t-gblue" />
    </div>
  )
}

export function EmptyState({ title, text }: { title: string; text: string }) {
  return (
    <Card className="text-center">
      <p className="font-bold">{title}</p>
      <p className="mt-1 text-sm text-muted">{text}</p>
    </Card>
  )
}

export function Button({
  children,
  onClick,
  type = 'button',
  variant = 'primary',
  disabled,
  className = '',
}: {
  children: ReactNode
  onClick?: () => void
  type?: 'button' | 'submit'
  variant?: 'primary' | 'outline' | 'ghost' | 'danger'
  disabled?: boolean
  className?: string
}) {
  const styles = {
    primary: 'bg-gblue text-white shadow-sm hover:brightness-95',
    outline: 'border border-gblue/40 bg-white text-gblue hover:bg-gblue/5',
    ghost: 'text-muted hover:bg-line/60',
    danger: 'border border-gred/30 bg-white text-gred hover:bg-gred/5',
  }[variant]
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`rounded-full px-5 py-2.5 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${styles} ${className}`}
    >
      {children}
    </button>
  )
}

export function statusLabel(status: string): { label: string; tone: number } {
  switch (status) {
    case 'accepted':
      return { label: 'Accepted', tone: 3 }
    case 'pending':
      return { label: 'Pending', tone: 2 }
    case 'declined':
      return { label: 'No reply', tone: 0 }
    case 'completed':
      return { label: 'Completed', tone: 0 }
    case 'interview':
      return { label: 'Interview', tone: 3 }
    case 'under_review':
      return { label: 'Under review', tone: 0 }
    case 'submitted':
      return { label: 'Submitted', tone: 2 }
    case 'closed':
      return { label: 'Closed', tone: 1 }
    default:
      return { label: status, tone: 0 }
  }
}

import { useQuery } from '@tanstack/react-query'
import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { apiOrigin, fetchMe } from './api'

const seekerTabs = [
  { to: '/companies', label: 'Companies' },
  { to: '/chats', label: 'Chats' },
  { to: '/badge', label: 'Badge' },
  { to: '/referrals', label: 'Referrals' },
]

function SignIn() {
  return (
    <main className="mx-auto flex min-h-screen max-w-[430px] flex-col justify-center gap-4 px-6">
      <h1 className="text-3xl font-bold">Referral Portal</h1>
      <p className="text-muted">DevFest · 13 December</p>
      <p className="text-muted">Meet companies, get referred, or refer others.</p>
      <a
        href={`${apiOrigin}/oauth2/authorization/google`}
        className="rounded-xl bg-seeker py-3 text-center font-semibold text-white"
      >
        Continue with Google
      </a>
      <a
        href={`${apiOrigin}/oauth2/authorization/github`}
        className="rounded-xl bg-ink py-3 text-center font-semibold text-white"
      >
        Continue with GitHub
      </a>
      <p className="text-sm text-muted">
        By continuing you agree to share your name and email so we can create your profile.
      </p>
    </main>
  )
}

function Shell() {
  return (
    <div className="mx-auto flex min-h-screen max-w-[430px] flex-col">
      <header className="flex items-center justify-between px-5 pt-5">
        <span className="font-bold">Referral Portal</span>
        <NavLink to="/profile" className="text-sm font-semibold text-seeker">
          Profile
        </NavLink>
      </header>
      <main className="flex-1 px-5 py-4">
        <Outlet />
      </main>
      <nav className="sticky bottom-0 grid grid-cols-4 gap-1 border-t border-slate-200 bg-page px-2 py-2">
        {seekerTabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) =>
              `rounded-xl py-2 text-center text-sm font-semibold ${
                isActive ? 'bg-white text-seeker' : 'text-muted'
              }`
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

function Placeholder({ title }: { title: string }) {
  return (
    <section>
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="mt-2 text-muted">Coming in a later slice.</p>
    </section>
  )
}

export default function App() {
  const { data: me, isPending, isError } = useQuery({ queryKey: ['me'], queryFn: fetchMe })

  if (isPending) return <p className="p-6 text-muted">Loading…</p>
  if (isError) return <p className="p-6 text-muted">Cannot reach the server. Is the backend running?</p>
  if (!me) return <SignIn />

  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Navigate to="/companies" replace />} />
        <Route path="companies" element={<Placeholder title="Companies" />} />
        <Route path="chats" element={<Placeholder title="Chats" />} />
        <Route path="badge" element={<Placeholder title="Your badge" />} />
        <Route path="referrals" element={<Placeholder title="My referrals" />} />
        <Route path="profile" element={<Placeholder title="Your profile" />} />
      </Route>
    </Routes>
  )
}

import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Award, Building2, LogOut, MessageCircle, QrCode } from 'lucide-react'
import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { api, fetchMe, type Me } from './api'
import BadgePage from './pages/Badge'
import ChatPage from './pages/Chat'
import ChatsPage from './pages/Chats'
import CompaniesPage from './pages/Companies'
import ProfilePage from './pages/Profile'
import ReferralsPage from './pages/Referrals'
import SignIn from './pages/SignIn'
import { Avatar, ErrorNote, Logo, Spinner } from './ui'

const tabs = [
  { to: '/companies', label: 'Companies', icon: Building2, color: 'text-gblue' },
  { to: '/chats', label: 'Chats', icon: MessageCircle, color: 'text-gred' },
  { to: '/badge', label: 'Badge', icon: QrCode, color: 'text-yellow-600' },
  { to: '/referrals', label: 'Referrals', icon: Award, color: 'text-ggreen' },
]

function Shell({ me }: { me: Me }) {
  const qc = useQueryClient()
  const signOut = async () => {
    try {
      await api('/logout', { method: 'POST' })
    } finally {
      qc.clear()
      window.location.assign('/')
    }
  }
  return (
    <div className="mx-auto flex min-h-screen max-w-[430px] flex-col">
      <header className="sticky top-0 z-10 flex items-center justify-between bg-page/80 px-5 py-4 backdrop-blur">
        <Logo />
        <div className="flex items-center gap-1">
          <NavLink to="/profile" aria-label="Profile" className="flex items-center gap-2 rounded-full py-1 pl-1 pr-3 hover:bg-line/60">
            <Avatar name={me.name} size="sm" />
            <span className="text-sm font-semibold text-gblue">Profile</span>
          </NavLink>
          <button onClick={signOut} aria-label="Sign out" className="rounded-full p-2 text-muted hover:bg-line/60">
            <LogOut size={18} />
          </button>
        </div>
      </header>
      <main className="flex-1 px-5 pb-6 pt-2">
        <Outlet />
      </main>
      <nav className="sticky bottom-0 z-10 grid grid-cols-4 gap-1 border-t border-line bg-white/90 px-2 pb-3 pt-2 backdrop-blur">
        {tabs.map(({ to, label, icon: Icon, color }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex flex-col items-center gap-1 rounded-2xl py-2 text-xs font-semibold transition ${isActive ? `bg-page ${color}` : 'text-muted'}`
            }
          >
            <Icon size={20} />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

export default function App() {
  const { data: me, isPending, isError } = useQuery({ queryKey: ['me'], queryFn: fetchMe })

  if (isPending) return <Spinner />
  if (isError) {
    return (
      <div className="mx-auto max-w-[430px] p-6">
        <ErrorNote message="Cannot reach the server. Please check that the backend is running and refresh." />
      </div>
    )
  }
  if (!me) return <SignIn loginError={new URLSearchParams(window.location.search).has('loginError')} />

  return (
    <Routes>
      <Route path="chats/:id" element={<ChatPage />} />
      <Route element={<Shell me={me} />}>
        <Route index element={<Navigate to={me.hasProfile ? '/companies' : '/profile'} replace />} />
        <Route path="profile" element={<ProfilePage me={me} />} />
        <Route path="companies" element={me.hasProfile ? <CompaniesPage /> : <Navigate to="/profile" replace />} />
        <Route path="chats" element={<ChatsPage />} />
        <Route path="badge" element={<BadgePage me={me} />} />
        <Route path="referrals" element={<ReferralsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

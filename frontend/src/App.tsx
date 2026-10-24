import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Award, Building2, LogOut, Moon, QrCode, Sun } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { api, fetchMe, type Me } from './api'
import BadgePage from './pages/Badge'
import CompaniesPage from './pages/Companies'
import ProfilePage from './pages/Profile'
import ReferralsPage from './pages/Referrals'
import SignIn from './pages/SignIn'
import { Avatar, ErrorNote, Logo, Spinner } from './ui'

const tabs = [
  { to: '/companies', label: 'Companies', icon: Building2, color: 'text-gblue' },
  { to: '/badge', label: 'Badge', icon: QrCode, color: 'text-yellow-600' },
  { to: '/referrals', label: 'Referrals', icon: Award, color: 'text-ggreen' },
]

function ThemeButton({ theme, onToggle }: { theme: 'light' | 'dark'; onToggle: () => void }) {
  const Icon = theme === 'dark' ? Sun : Moon
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      aria-pressed={theme === 'dark'}
      className="rounded-full p-2 text-muted hover:bg-line/60"
    >
      <Icon size={18} />
    </button>
  )
}

function Shell({ me, theme, toggleTheme }: { me: Me; theme: 'light' | 'dark'; toggleTheme: () => void }) {
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
          <ThemeButton theme={theme} onToggle={toggleTheme} />
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
      <nav className="sticky bottom-0 z-10 grid grid-cols-3 gap-1 border-t border-line bg-white/90 px-2 pb-3 pt-2 backdrop-blur">
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
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light',
  )
  const toggleTheme = () => {
    const nextTheme = theme === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = nextTheme
    try {
      localStorage.setItem('referral-theme', nextTheme)
    } catch {
    }
    setTheme(nextTheme)
  }

  if (isPending) return <><div className="fixed right-3 top-3 z-20"><ThemeButton theme={theme} onToggle={toggleTheme} /></div><Spinner /></>
  if (isError) {
    return (
      <div className="min-h-screen">
        <div className="fixed right-3 top-3 z-20"><ThemeButton theme={theme} onToggle={toggleTheme} /></div>
        <div className="mx-auto max-w-[430px] p-6 pt-16">
          <ErrorNote message="Cannot reach the server. Please check that the backend is running and refresh." />
        </div>
      </div>
    )
  }
  if (!me) {
    return (
      <div className="min-h-screen">
        <div className="fixed right-3 top-3 z-20"><ThemeButton theme={theme} onToggle={toggleTheme} /></div>
        <SignIn loginError={new URLSearchParams(window.location.search).has('loginError')} />
      </div>
    )
  }

  return (
    <Routes>
      <Route element={<Shell me={me} theme={theme} toggleTheme={toggleTheme} />}>
        <Route index element={<Navigate to={me.hasProfile ? '/companies' : '/profile'} replace />} />
        <Route path="profile" element={<ProfilePage me={me} />} />
        <Route path="companies" element={me.hasProfile ? <CompaniesPage /> : <Navigate to="/profile" replace />} />
        <Route path="badge" element={<BadgePage me={me} />} />
        <Route path="referrals" element={<ReferralsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

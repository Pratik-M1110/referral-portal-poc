import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api, apiOrigin } from '../api'
import { Button, ErrorNote, Logo } from '../ui'

export default function SignIn({ loginError }: { loginError: boolean }) {
  const qc = useQueryClient()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [registrationCode, setRegistrationCode] = useState('')
  const [registrationStarted, setRegistrationStarted] = useState(false)

  useEffect(() => {
    void api<void>('/api/auth/csrf').catch(() => undefined)
  }, [])

  const finishAuthentication = async () => {
    await qc.invalidateQueries({ queryKey: ['me'] })
    window.location.assign('/')
  }

  const authenticate = useMutation({
    mutationFn: () => api<void>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    }),
    onSuccess: finishAuthentication,
  })
  const beginRegistration = useMutation({
    mutationFn: () => api<void>('/api/auth/register', {
      method: 'POST',
      body: { name, email, password },
    }),
    onSuccess: () => {
      setRegistrationStarted(true)
      setRegistrationCode('')
    },
  })
  const verifyRegistration = useMutation({
    mutationFn: () => api<void>('/api/auth/register/verify', {
      method: 'POST',
      body: { email, code: registrationCode, password },
    }),
    onSuccess: finishAuthentication,
  })
  const registrationPending = beginRegistration.isPending || verifyRegistration.isPending
  const errorMessage = mode === 'register'
    ? (verifyRegistration.error?.message ?? beginRegistration.error?.message)
    : authenticate.error?.message

  const changeMode = (nextMode: 'login' | 'register') => {
    setMode(nextMode)
    setShowPassword(false)
    setRegistrationStarted(false)
    setRegistrationCode('')
    beginRegistration.reset()
    verifyRegistration.reset()
    authenticate.reset()
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-[430px] flex-col justify-center px-5 py-4">
      <div className="rise">
        <Logo size="md" />
        <p className="mt-3 text-3xl font-extrabold leading-tight tracking-tight">
          Make good things <span className="text-gblue">together</span>.
        </p>
        <p className="mt-2 text-sm text-muted">
          DevFest Pune · 13 December. Find companies, request referral help, and support other seekers from one profile.
        </p>

        {loginError && (
          <p role="alert" className="mt-4 rounded-2xl bg-gred/10 px-4 py-3 text-sm font-medium text-gred">
            Sign-in did not complete. Please try again.
          </p>
        )}

        <div className="mt-5 grid grid-cols-2 gap-1 rounded-2xl border border-line bg-page p-1" role="tablist" aria-label="Sign-in method">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            onClick={() => changeMode('login')}
            className={`min-h-11 rounded-xl text-sm font-semibold ${mode === 'login' ? 'bg-white text-gblue shadow-sm' : 'text-muted'}`}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            onClick={() => changeMode('register')}
            className={`min-h-11 rounded-xl text-sm font-semibold ${mode === 'register' ? 'bg-white text-gblue shadow-sm' : 'text-muted'}`}
          >
            Register
          </button>
        </div>

        <form
          className="mt-3 space-y-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (mode === 'login') {
              authenticate.mutate()
            } else if (registrationStarted) {
              verifyRegistration.mutate()
            } else {
              beginRegistration.mutate()
            }
          }}
        >
          {mode === 'register' && (
            <div>
              <label className="text-sm font-semibold" htmlFor="auth-name">Name</label>
              <input id="auth-name" required maxLength={120} value={name} onChange={(event) => setName(event.target.value)} disabled={registrationStarted} autoComplete="name" className="mt-1 w-full rounded-xl border border-line bg-white px-3 py-2 text-sm outline-none focus:border-gblue disabled:opacity-60" />
            </div>
          )}
          <div>
            <label className="text-sm font-semibold" htmlFor="auth-email">Email</label>
            <input id="auth-email" type="email" required maxLength={190} value={email} onChange={(event) => setEmail(event.target.value)} disabled={registrationStarted} autoComplete="email" className="mt-1 w-full rounded-xl border border-line bg-white px-3 py-2 text-sm outline-none focus:border-gblue disabled:opacity-60" />
          </div>
          <div>
            <label className="text-sm font-semibold" htmlFor="auth-password">Password</label>
            <div className="relative mt-1">
              <input
                id="auth-password"
                type={showPassword ? 'text' : 'password'}
                required
                minLength={8}
                maxLength={100}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={mode === 'register' && registrationStarted}
                autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                className="w-full rounded-xl border border-line bg-white px-3 py-2 pr-11 text-sm outline-none focus:border-gblue disabled:opacity-60"
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                disabled={mode === 'register' && registrationStarted}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                title={showPassword ? 'Hide password' : 'Show password'}
                className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-muted outline-none transition hover:text-gblue disabled:opacity-50"
              >
                {showPassword ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
              </button>
            </div>
            {mode === 'register' && <p className="mt-1 text-xs text-muted">At least 8 characters; passwords must stay within 72 UTF-8 bytes.</p>}
          </div>
          {mode === 'register' && registrationStarted && (
            <div>
              <label className="text-sm font-semibold" htmlFor="registration-code">Email verification code</label>
              <input
                id="registration-code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                required
                maxLength={6}
                value={registrationCode}
                onChange={(event) => setRegistrationCode(event.target.value)}
                placeholder="6-digit code"
                className="mt-1 w-full rounded-xl border border-line bg-white px-3 py-2 text-sm outline-none focus:border-gblue"
              />
              <p className="mt-1 text-xs text-muted">If this email can be registered, a code has been sent. It expires in 10 minutes.</p>
              <button type="button" disabled={registrationPending} onClick={() => { verifyRegistration.reset(); beginRegistration.mutate() }} className="mt-1 text-xs font-semibold text-gblue underline disabled:opacity-60">
                Resend code
              </button>
            </div>
          )}
          <ErrorNote message={errorMessage ?? null} />
          <Button type="submit" disabled={authenticate.isPending || registrationPending || (mode === 'register' && name.trim().length === 0)} className="w-full">
            {authenticate.isPending || registrationPending
              ? 'Please wait…'
              : mode === 'login'
                ? 'Sign in'
                : registrationStarted
                  ? 'Verify email and create account'
                  : 'Send verification code'}
          </Button>
        </form>

        <div className="my-3 flex items-center gap-3 text-xs text-muted">
          <div className="h-px flex-1 bg-line" />
          <span>or continue with</span>
          <div className="h-px flex-1 bg-line" />
        </div>

        <div className="space-y-2">
          <a
            href={`${apiOrigin}/oauth2/authorization/google`}
            className="flex items-center justify-center gap-3 rounded-full border border-line bg-white py-2.5 font-semibold shadow-sm transition hover:shadow-md"
          >
            <svg viewBox="0 0 24 24" className="h-5 w-5" aria-hidden>
              <path fill="#4285F4" d="M23.49 12.27c0-.79-.07-1.54-.19-2.27H12v4.51h6.47c-.29 1.48-1.14 2.73-2.4 3.58v3h3.86c2.26-2.09 3.56-5.17 3.56-8.82z" />
              <path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.86-3c-1.08.72-2.45 1.16-4.07 1.16-3.13 0-5.78-2.11-6.73-4.96H1.29v3.09C3.26 21.3 7.31 24 12 24z" />
              <path fill="#FBBC04" d="M5.27 14.29c-.25-.72-.38-1.49-.38-2.29s.14-1.57.38-2.29V6.62H1.29C.47 8.24 0 10.06 0 12s.47 3.76 1.29 5.38l3.98-3.09z" />
              <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.7 1.29 6.62l3.98 3.09C6.22 6.86 8.87 4.75 12 4.75z" />
            </svg>
            Continue with Google
          </a>
          <a
            href={`${apiOrigin}/oauth2/authorization/github`}
            className="github-sign-in flex items-center justify-center gap-3 rounded-full bg-ink py-2.5 font-semibold text-white shadow-sm transition hover:brightness-110"
          >
            <svg viewBox="0 0 24 24" className="h-5 w-5 fill-white" aria-hidden>
              <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
            </svg>
            Continue with GitHub
          </a>
        </div>

        <p className="mt-4 text-xs text-muted">
          By continuing you agree to share your name and email so we can create your account. Contact details are shared for a referral only when you choose to share them.
        </p>
      </div>
    </main>
  )
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Me, type Profile as ProfileData, type ReferrerSettings } from '../api'
import { Button, Card, Chip, ErrorNote, PageTitle, Spinner } from '../ui'

const LOOKING_FOR = ['Frontend', 'Backend', 'Full Stack', 'Mobile', 'Data', 'ML / AI', 'DevOps', 'Cloud', 'QA', 'Design']
const SKILLS = ['React', 'TypeScript', 'JavaScript', 'Node.js', 'Java', 'Spring Boot', 'Python', 'SQL', 'Kotlin', 'Flutter', 'Go', 'GCP', 'AWS', 'Docker', 'Kubernetes', 'TensorFlow', 'Firebase', 'Angular']
const LEVELS = [
  { value: 'student', label: 'Student' },
  { value: 'fresher', label: 'Fresher' },
  { value: '1-3', label: '1-3 years' },
  { value: '3-6', label: '3-6 years' },
  { value: '6+', label: '6+ years' },
]

function ChipPicker({ options, value, onChange, max }: { options: string[]; value: string[]; onChange: (v: string[]) => void; max: number }) {
  const [custom, setCustom] = useState('')
  const all = Array.from(new Set([...options, ...value]))
  const toggle = (item: string) =>
    onChange(value.includes(item) ? value.filter((v) => v !== item) : value.length < max ? [...value, item] : value)
  const addCustom = () => {
    const item = custom.trim()
    if (item && !value.includes(item) && value.length < max) onChange([...value, item])
    setCustom('')
  }
  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {all.map((item) => {
          const on = value.includes(item)
          return (
            <button
              key={item}
              type="button"
              onClick={() => toggle(item)}
              className={`rounded-full border px-3 py-1.5 text-sm font-medium transition ${on ? 'border-gblue bg-gblue text-white' : 'border-line bg-white text-muted hover:border-gblue/50'}`}
            >
              {item}
            </button>
          )
        })}
      </div>
      <div className="mt-2 flex gap-2">
        <input
          value={custom}
          onChange={(e) => setCustom(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              addCustom()
            }
          }}
          maxLength={30}
          placeholder="Add your own"
          className="min-w-0 flex-1 rounded-full border border-line bg-white px-4 py-2 text-sm outline-none focus:border-gblue"
        />
        <Button variant="outline" onClick={addCustom}>Add</Button>
      </div>
      <p className="mt-1 text-xs text-muted">{value.length} of {max} selected</p>
    </div>
  )
}

export default function ProfilePage({ me }: { me: Me }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data, isPending } = useQuery({ queryKey: ['profile'], queryFn: () => api<ProfileData>('/api/me/profile') })
  const { data: referrerSettings } = useQuery({
    queryKey: ['referrer-settings'],
    queryFn: () => api<ReferrerSettings>('/api/referrer/settings'),
  })
  const [form, setForm] = useState<ProfileData | null>(null)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [workEmail, setWorkEmail] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [openToMeet, setOpenToMeet] = useState(false)
  const [slotCapacity, setSlotCapacity] = useState(3)
  const [referralLimit, setReferralLimit] = useState(10)

  useEffect(() => {
    if (data) setForm(data)
  }, [data])

  useEffect(() => {
    if (!referrerSettings) return
    setWorkEmail(referrerSettings.workEmail ?? '')
    setOpenToMeet(referrerSettings.openToMeet)
    setSlotCapacity(referrerSettings.slotCapacity)
    setReferralLimit(referrerSettings.referralLimit)
  }, [referrerSettings])

  const save = useMutation({
    mutationFn: (p: ProfileData) =>
      api('/api/me/profile', {
        method: 'PUT',
        body: {
          pitch: p.pitch ?? '',
          experienceLevel: p.experienceLevel,
          skills: p.skills,
          rolesWanted: p.rolesWanted,
          githubUrl: p.githubUrl ?? '',
          linkedinUrl: p.linkedinUrl ?? '',
          hideFromMyCompany: p.hideFromMyCompany,
        },
      }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['me'] })
      await qc.invalidateQueries({ queryKey: ['profile'] })
      navigate('/companies')
    },
  })

  const deleteAccount = useMutation({
    mutationFn: () => api<void>('/api/me/account', { method: 'DELETE' }),
    onSuccess: () => {
      qc.clear()
      window.location.assign('/')
    },
  })

  const startVerification = useMutation({
    mutationFn: () => api<{ sent: boolean }>('/api/referrer/verify/start', {
      method: 'POST',
      body: { workEmail },
    }),
    onSuccess: () => {
      setCodeSent(true)
      setVerificationCode('')
    },
  })

  const confirmVerification = useMutation({
    mutationFn: () => api<ReferrerSettings>('/api/referrer/verify/confirm', {
      method: 'POST',
      body: { workEmail, code: verificationCode },
    }),
    onSuccess: async () => {
      setCodeSent(false)
      setVerificationCode('')
      await qc.invalidateQueries({ queryKey: ['me'] })
      await qc.invalidateQueries({ queryKey: ['referrer-settings'] })
    },
  })

  const saveReferrerSettings = useMutation({
    mutationFn: () => api<ReferrerSettings>('/api/referrer/settings', {
      method: 'PUT',
      body: { openToMeet, slotCapacity, referralLimit },
    }),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['referrer-settings'] })
      await qc.invalidateQueries({ queryKey: ['me'] })
    },
  })

  if (isPending || !form) return <Spinner />
  const set = <K extends keyof ProfileData>(key: K, value: ProfileData[K]) => setForm({ ...form, [key]: value })

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate(form)
      }}
      className="space-y-4 pb-4"
    >
      <PageTitle
        title={me.hasProfile ? 'Your profile' : `Welcome, ${me.name.split(' ')[0]}!`}
        subtitle={me.hasProfile ? 'Keep it short and honest. Referrers see this when they scan your badge.' : 'Takes about 2 minutes. You can edit it later from the Profile link.'}
      />

      <Card>
        <p className="mb-2 text-sm font-bold">Experience</p>
        <div className="flex flex-wrap gap-2">
          {LEVELS.map((l) => (
            <button
              key={l.value}
              type="button"
              onClick={() => set('experienceLevel', l.value)}
              className={`rounded-full border px-3 py-1.5 text-sm font-medium ${form.experienceLevel === l.value ? 'border-ggreen bg-ggreen text-white' : 'border-line bg-white text-muted'}`}
            >
              {l.label}
            </button>
          ))}
        </div>
      </Card>

      <Card>
        <p className="mb-2 text-sm font-bold">Looking for</p>
        <ChipPicker options={LOOKING_FOR} value={form.rolesWanted} onChange={(v) => set('rolesWanted', v)} max={6} />
      </Card>

      <Card>
        <p className="mb-2 text-sm font-bold">Skills</p>
        <ChipPicker options={SKILLS} value={form.skills} onChange={(v) => set('skills', v)} max={12} />
      </Card>

      <Card>
        <label className="text-sm font-bold" htmlFor="pitch">Your pitch</label>
        <textarea
          id="pitch"
          value={form.pitch ?? ''}
          onChange={(e) => set('pitch', e.target.value)}
          maxLength={200}
          rows={3}
          placeholder="One line about what you are looking for"
          className="mt-2 w-full resize-none rounded-2xl border border-line bg-white px-4 py-3 text-sm outline-none focus:border-gblue"
        />
        <p className="text-right text-xs text-muted">{(form.pitch ?? '').length}/200</p>

        <label className="mt-3 block text-sm font-bold" htmlFor="gh">GitHub</label>
        <input id="gh" value={form.githubUrl ?? ''} onChange={(e) => set('githubUrl', e.target.value)} placeholder="https://github.com/you" className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm outline-none focus:border-gblue" />
        <label className="mt-3 block text-sm font-bold" htmlFor="li">LinkedIn</label>
        <input id="li" value={form.linkedinUrl ?? ''} onChange={(e) => set('linkedinUrl', e.target.value)} placeholder="https://linkedin.com/in/you" className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm outline-none focus:border-gblue" />
      </Card>

      <Card>
        <label className="flex items-start gap-3 text-sm">
          <input type="checkbox" checked={form.hideFromMyCompany} onChange={(e) => set('hideFromMyCompany', e.target.checked)} className="mt-1 h-4 w-4 accent-gblue" />
          <span>
            <span className="font-bold">Hide my profile from people at my own company</span>
            <span className="block text-muted">On by default. Colleagues will not see you in lists or scans.</span>
          </span>
        </label>
      </Card>

      {referrerSettings?.verified ? (
        <Card className="bg-referrer-soft">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="font-bold text-referrer">Referrer at {referrerSettings.companyName}</p>
              <p className="text-sm text-muted">{referrerSettings.workEmail}</p>
            </div>
            <Chip tone={3}>Verified</Chip>
          </div>
          <label className="mt-4 flex items-start gap-3 text-sm">
            <input type="checkbox" checked={openToMeet} onChange={(event) => setOpenToMeet(event.target.checked)} className="mt-1 h-4 w-4 accent-ggreen" />
            <span>
              <span className="font-bold">Open to meet</span>
              <span className="block text-muted">Let seekers request a conversation with you.</span>
            </span>
          </label>
          <label className="mt-4 block text-sm font-semibold" htmlFor="slot-capacity">Conversation slots</label>
          <input id="slot-capacity" type="number" min={1} max={10} value={slotCapacity} onChange={(event) => setSlotCapacity(Number(event.target.value))} className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm outline-none focus:border-ggreen" />
          <label className="mt-3 block text-sm font-semibold" htmlFor="referral-limit">Referral limit</label>
          <input id="referral-limit" type="number" min={1} max={10} value={referralLimit} onChange={(event) => setReferralLimit(Number(event.target.value))} className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm outline-none focus:border-ggreen" />
          <ErrorNote message={saveReferrerSettings.error?.message ?? null} />
          <Button disabled={saveReferrerSettings.isPending} onClick={() => saveReferrerSettings.mutate()} className="mt-4">
            {saveReferrerSettings.isPending ? 'Saving…' : 'Save referrer settings'}
          </Button>
        </Card>
      ) : (
        <Card className="bg-referrer-soft">
          <p className="font-bold text-referrer">Become a referrer</p>
          <p className="text-sm text-muted">Verify your work email to refer people. You can still use your seeker profile without verifying.</p>
          <label className="mt-3 block text-sm font-semibold" htmlFor="work-email">Work email</label>
          <input
            id="work-email"
            type="email"
            required
            maxLength={190}
            value={workEmail}
            onChange={(event) => setWorkEmail(event.target.value)}
            disabled={codeSent || startVerification.isPending}
            placeholder="you@company.com"
            className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm outline-none focus:border-ggreen disabled:opacity-60"
          />
          {codeSent && (
            <>
              <p className="mt-2 text-sm text-muted">Enter the 6-digit code sent to {workEmail}. It expires in 10 minutes.</p>
              <label className="mt-3 block text-sm font-semibold" htmlFor="verification-code">Verification code</label>
              <input
                id="verification-code"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={verificationCode}
                onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm tracking-widest outline-none focus:border-ggreen"
              />
            </>
          )}
          <ErrorNote message={startVerification.error?.message ?? confirmVerification.error?.message ?? null} />
          {codeSent ? (
            <div className="mt-4 flex flex-wrap gap-2">
              <Button disabled={verificationCode.length !== 6 || confirmVerification.isPending} onClick={() => confirmVerification.mutate()}>
                {confirmVerification.isPending ? 'Verifying…' : 'Verify email'}
              </Button>
              <Button variant="outline" disabled={startVerification.isPending} onClick={() => startVerification.mutate()}>
                {startVerification.isPending ? 'Sending…' : 'Resend code'}
              </Button>
              <Button variant="ghost" onClick={() => setCodeSent(false)}>Change email</Button>
            </div>
          ) : (
            <Button className="mt-4" disabled={!workEmail.includes('@') || startVerification.isPending} onClick={() => startVerification.mutate()}>
              {startVerification.isPending ? 'Sending…' : 'Send verification code'}
            </Button>
          )}
        </Card>
      )}

      <ErrorNote message={save.error?.message ?? null} />
      <Button type="submit" disabled={save.isPending} className="w-full py-3.5 text-base">
        {save.isPending ? 'Saving…' : me.hasProfile ? 'Save changes' : 'Save and continue'}
      </Button>

      <Card className="border-gred/30">
        <h2 className="font-bold text-gred">Delete account</h2>
        <p className="mt-1 text-sm text-muted">
          Permanently delete your profile, conversations, messages, and referral records. This cannot be undone.
        </p>
        <label className="mt-3 block text-sm font-semibold" htmlFor="delete-confirmation">
          Type DELETE to confirm
        </label>
        <input
          id="delete-confirmation"
          value={deleteConfirmation}
          onChange={(event) => setDeleteConfirmation(event.target.value)}
          autoComplete="off"
          className="mt-1 w-full rounded-full border border-line bg-white px-4 py-2.5 text-sm outline-none focus:border-gred"
        />
        <ErrorNote message={deleteAccount.error?.message ?? null} />
        <Button
          variant="danger"
          disabled={deleteConfirmation !== 'DELETE' || deleteAccount.isPending}
          onClick={() => deleteAccount.mutate()}
          className="mt-3"
        >
          {deleteAccount.isPending ? 'Deleting…' : 'Delete my account'}
        </Button>
      </Card>
    </form>
  )
}

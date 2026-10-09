import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Me, type Profile as ProfileData } from '../api'
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
  const [form, setForm] = useState<ProfileData | null>(null)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')

  useEffect(() => {
    if (data) setForm(data)
  }, [data])

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

  if (isPending || !form) return <Spinner />
  const set = <K extends keyof ProfileData>(key: K, value: ProfileData[K]) => setForm({ ...form, [key]: value })

  return (
    <form
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

      {me.isReferrer ? (
        <Chip tone={3}>Work email verified</Chip>
      ) : (
        <Card className="bg-referrer-soft">
          <p className="font-bold text-referrer">Work at a company?</p>
          <p className="text-sm text-muted">Verify your work email to refer people too. Coming in the next update.</p>
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

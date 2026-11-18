import { useEffect, useRef, useState } from 'react'
import {
  DEFAULT_LOOKING_FOR,
  getAllKnownValues,
  LEGACY_ALIAS,
  LOOKING_FOR_BY_EXPERIENCE,
  MAX_LOOKING_FOR,
  resolveLegacyAliases,
  type ExperienceBucket,
  type LookingForOption,
} from '../config/lookingForOptions'
import { validateCustomLookingFor } from '../utils/validateCustomLookingFor'
import { Button } from '../ui'

const CHIPS_SHOWN_BY_DEFAULT = 8
export const MAX_CUSTOM_PER_SESSION = 3

export function isCustomValue(v: string, bucketOptions: LookingForOption[]): boolean {
  const lv = v.toLowerCase()
  const inBucket = bucketOptions.some((o) => o.value.toLowerCase() === lv)
  const inAny = Object.values(LOOKING_FOR_BY_EXPERIENCE).some((opts) =>
    opts.some((o) => o.value.toLowerCase() === lv)
  )
  const inDefault = DEFAULT_LOOKING_FOR.some((o) => o.value.toLowerCase() === lv)
  const isAlias = Object.keys(LEGACY_ALIAS).some((k) => k.toLowerCase() === lv)
  return !inBucket && !inAny && !inDefault && !isAlias
}

export function LookingForChipPicker({
  experienceLevel,
  value,
  onChange,
  onPrunedToast,
}: {
  experienceLevel: string
  value: string[]
  onChange: (v: string[]) => void
  onPrunedToast: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const [customInput, setCustomInput] = useState('')
  const [customError, setCustomError] = useState<string | null>(null)
  const [customAddedCount, setCustomAddedCount] = useState(0)
  const prevExpLevel = useRef<string>(experienceLevel)

  const bucketOptions: LookingForOption[] =
    LOOKING_FOR_BY_EXPERIENCE[experienceLevel as ExperienceBucket] ?? DEFAULT_LOOKING_FOR

  useEffect(() => {
    if (prevExpLevel.current === experienceLevel) return
    prevExpLevel.current = experienceLevel

    const resolved = resolveLegacyAliases(value)
    const bucketValues = new Set(bucketOptions.map((o) => o.value.toLowerCase()))
    const kept: string[] = []
    for (const v of resolved) {
      const lv = v.toLowerCase()
      if (bucketValues.has(lv) || isCustomValue(v, bucketOptions)) {
        kept.push(v)
      }
    }
    const dropped = kept.length < value.length || resolved.some((v, i) => v !== value[i])
    if (dropped || kept.length !== value.length) {
      onChange(kept)
      if (kept.length < value.length) onPrunedToast()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [experienceLevel])

  const toggle = (item: string) => {
    if (value.includes(item)) {
      onChange(value.filter((v) => v !== item))
    } else if (value.length < MAX_LOOKING_FOR) {
      onChange([...value, item])
    }
  }

  const handleAddCustom = () => {
    setCustomError(null)
    const result = validateCustomLookingFor(customInput, value, getAllKnownValues())
    if (!result.ok) {
      setCustomError(result.error)
      return
    }
    const lowerNorm = result.normalized.toLowerCase()
    const matchInBucket = bucketOptions.find((o) => o.value.toLowerCase() === lowerNorm)
    if (matchInBucket) {
      if (!value.includes(matchInBucket.value) && value.length < MAX_LOOKING_FOR) {
        onChange([...value, matchInBucket.value])
      }
      setCustomInput('')
      return
    }
    if (customAddedCount >= MAX_CUSTOM_PER_SESSION) {
      setCustomError(`You can add at most ${MAX_CUSTOM_PER_SESSION} custom values per session.`)
      return
    }
    if (value.length >= MAX_LOOKING_FOR) {
      setCustomError(`Maximum ${MAX_LOOKING_FOR} reached. Remove one to add another.`)
      return
    }
    onChange([...value, result.normalized])
    setCustomAddedCount((c) => c + 1)
    setCustomInput('')
  }

  const atMax = value.length >= MAX_LOOKING_FOR
  const shownOptions = expanded ? bucketOptions : bucketOptions.slice(0, CHIPS_SHOWN_BY_DEFAULT)
  const hasMore = bucketOptions.length > CHIPS_SHOWN_BY_DEFAULT
  const customValues = value.filter((v) => isCustomValue(v, bucketOptions))

  return (
    <div>
      <div className="flex flex-wrap gap-2" data-testid="chip-list">
        {shownOptions.map((opt) => {
          const on = value.some((v) => v.toLowerCase() === opt.value.toLowerCase())
          const disabled = atMax && !on
          return (
            <button
              key={opt.value}
              type="button"
              aria-pressed={on}
              aria-disabled={disabled || undefined}
              title={disabled ? `Maximum ${MAX_LOOKING_FOR} reached. Remove one to add another.` : undefined}
              onClick={() => { if (!disabled) toggle(opt.value) }}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition
                ${on ? 'border-gblue bg-gblue text-white' : disabled ? 'cursor-not-allowed border-line bg-white text-muted opacity-40' : 'border-line bg-white text-muted hover:border-gblue/50'}`}
            >
              {opt.label}
              {opt.trending && (
                <span className={`rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${on ? 'bg-white/20 text-white' : 'bg-gyellow/20 text-yellow-700'}`}>
                  ↑
                </span>
              )}
            </button>
          )
        })}
        {customValues.map((v) => (
          <button
            key={v}
            type="button"
            aria-pressed={true}
            onClick={() => toggle(v)}
            className="rounded-full border border-gblue bg-gblue px-3 py-1.5 text-sm font-medium text-white transition"
          >
            {v}
          </button>
        ))}
      </div>
      {hasMore && (
        <button
          type="button"
          onClick={() => setExpanded((e) => !e)}
          className="mt-2 text-xs font-medium text-gblue underline-offset-2 hover:underline"
        >
          {expanded ? 'Show less' : `+${bucketOptions.length - CHIPS_SHOWN_BY_DEFAULT} more`}
        </button>
      )}
      <div className="mt-2 flex gap-2">
        <input
          value={customInput}
          onChange={(e) => { setCustomInput(e.target.value); if (customError) setCustomError(null) }}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleAddCustom() } }}
          disabled={atMax || customAddedCount >= MAX_CUSTOM_PER_SESSION}
          maxLength={40}
          placeholder="Add your own"
          aria-label="Add a custom role or skill"
          data-testid="custom-input"
          className="min-w-0 flex-1 rounded-full border border-line bg-white px-4 py-2 text-sm outline-none focus:border-gblue disabled:cursor-not-allowed disabled:opacity-50"
        />
        <Button
          variant="outline"
          onClick={handleAddCustom}
          disabled={atMax || customAddedCount >= MAX_CUSTOM_PER_SESSION}
        >
          Add
        </Button>
      </div>
      {customError && (
        <p role="alert" className="mt-1 text-xs text-gred" aria-live="assertive" data-testid="custom-error">
          {customError}
        </p>
      )}
      <p className="mt-1 text-xs text-muted" aria-live="polite" data-testid="counter">
        {value.length} of {MAX_LOOKING_FOR} selected
      </p>
    </div>
  )
}

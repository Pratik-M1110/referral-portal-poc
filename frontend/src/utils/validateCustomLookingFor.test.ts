import { describe, expect, it } from 'vitest'
import { validateCustomLookingFor } from './validateCustomLookingFor'

const noSelected: string[] = []
const noKnown: string[] = []
const knownValues = ['frontend', 'backend', 'full stack', 'ml / ai', 'devops']

function valid(raw: string, existing = noSelected, known = noKnown) {
  const r = validateCustomLookingFor(raw, existing, known)
  expect(r.ok, `Expected "${raw}" to be valid but got: ${r.ok ? '' : (r as { error: string }).error}`).toBe(true)
  if (r.ok) return r.normalized
}

function invalid(raw: string, existing = noSelected, known = noKnown) {
  const r = validateCustomLookingFor(raw, existing, known)
  expect(r.ok, `Expected "${raw}" to be invalid but it passed`).toBe(false)
  if (!r.ok) return r.error
}

describe('validateCustomLookingFor — valid inputs', () => {
  it('accepts "Site Reliability Engineer"', () => {
    expect(valid('Site Reliability Engineer')).toBe('Site Reliability Engineer')
  })

  it('accepts "C++ Developer"', () => {
    expect(valid('C++ Developer')).toBe('C++ Developer')
  })

  it('accepts "ML / AI"', () => {
    expect(valid('ML / AI')).toBe('ML / AI')
  })

  it('accepts "iOS Engineer"', () => {
    expect(valid('iOS Engineer')).toBe('iOS Engineer')
  })

  it('accepts "Staff Engineer"', () => {
    expect(valid('Staff Engineer')).toBe('Staff Engineer')
  })

  it('accepts "AI Engineer (RAG / Agents)"', () => {
    expect(valid('AI Engineer (RAG / Agents)')).toBe('AI Engineer (RAG / Agents)')
  })

  it('accepts "Solutions Architect"', () => {
    expect(valid('Solutions Architect')).toBe('Solutions Architect')
  })

  it('normalizes extra whitespace', () => {
    expect(valid('  Node.js   Dev  ')).toBe('Node.js Dev')
  })

  it('accepts 2-character input', () => {
    expect(valid('Go')).toBe('Go')
  })

  it('accepts 4-word 40-character input', () => {
    const s = 'Site Reliability Engineering Lead' // 4 words, 33 chars
    expect(valid(s)).toBe(s)
  })

  it('accepts "#techops"', () => {
    expect(valid('#techops')).toBe('#techops')
  })

  it('accepts "Data & AI Engineer"', () => {
    expect(valid('Data & AI Engineer')).toBe('Data & AI Engineer')
  })
})

describe('validateCustomLookingFor — invalid inputs', () => {
  it('rejects empty string', () => {
    expect(invalid('')).toBeTruthy()
  })

  it('rejects whitespace-only string', () => {
    expect(invalid('   ')).toBeTruthy()
  })

  it('rejects single character', () => {
    expect(invalid('x')).toMatch(/2 character/)
  })

  it('rejects string longer than 40 chars', () => {
    expect(invalid('A'.repeat(41))).toMatch(/40/)
  })

  it('rejects more than 4 words', () => {
    expect(invalid('one two three four five')).toMatch(/4 word/)
  })

  it('rejects "asdf"', () => {
    expect(invalid('asdf')).toBeTruthy()
  })

  it('rejects "qwerty"', () => {
    expect(invalid('qwerty')).toBeTruthy()
  })

  it('rejects "zxcv"', () => {
    expect(invalid('zxcv')).toBeTruthy()
  })

  it('rejects "aaaa" (3+ identical chars)', () => {
    expect(invalid('aaaa')).toBeTruthy()
  })

  it('rejects "xxx"', () => {
    expect(invalid('xxx')).toBeTruthy()
  })

  it('rejects "123" (no letters)', () => {
    expect(invalid('123')).toBeTruthy()
  })

  it('rejects digits-only string', () => {
    expect(invalid('9999')).toBeTruthy()
  })

  it('rejects "test"', () => {
    expect(invalid('test')).toBeTruthy()
  })

  it('rejects "dummy"', () => {
    expect(invalid('dummy')).toBeTruthy()
  })

  it('rejects "null"', () => {
    expect(invalid('null')).toBeTruthy()
  })

  it('rejects "lorem"', () => {
    expect(invalid('lorem')).toBeTruthy()
  })

  it('rejects "<script>alert(1)</script>"', () => {
    expect(invalid('<script>alert(1)</script>')).toMatch(/HTML|script/)
  })

  it('rejects HTML entity &lt;', () => {
    expect(invalid('&lt;')).toMatch(/HTML|script/)
  })

  it('rejects URL "https://example.com"', () => {
    expect(invalid('https://example.com')).toMatch(/URL/)
  })

  it('rejects "www.site.org"', () => {
    expect(invalid('www.site.org')).toMatch(/URL/)
  })

  it('rejects email-like "user@company.com"', () => {
    expect(invalid('user@company.com')).toMatch(/[Ee]mail/)
  })

  it('rejects disallowed characters like @', () => {
    expect(invalid('@admin')).toBeTruthy()
  })

  it('rejects "bcdfg" (no vowels in 5+ letters)', () => {
    expect(invalid('bcdfg')).toBeTruthy()
  })

  it('rejects already-selected duplicate (case-insensitive)', () => {
    const error = invalid('Frontend', ['Frontend'], noKnown)
    expect(error).toMatch(/[Aa]lready/)
  })

  it('rejects case-insensitive duplicate in existing', () => {
    const error = invalid('frontend', ['Frontend'], noKnown)
    expect(error).toMatch(/[Aa]lready/)
  })

  it('rejects value present in known catalogue', () => {
    const error = invalid('frontend', noSelected, knownValues)
    expect(error).toMatch(/list|already/i)
  })

  it('rejects profane word', () => {
    expect(invalid('fuck')).toBeTruthy()
  })

  it('rejects profanity in a phrase', () => {
    expect(invalid('shit developer')).toBeTruthy()
  })
})

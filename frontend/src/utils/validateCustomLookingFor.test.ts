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

describe('validateCustomLookingFor — tech names with dot-TLD patterns (valid)', () => {
  it('accepts ".NET Developer"', () => {
    expect(valid('.NET Developer')).toBe('.NET Developer')
  })

  it('accepts "ASP.NET Engineer"', () => {
    expect(valid('ASP.NET Engineer')).toBe('ASP.NET Engineer')
  })

  it('accepts "ASP.NET Core Developer"', () => {
    expect(valid('ASP.NET Core Developer')).toBe('ASP.NET Core Developer')
  })

  it('accepts "VB.NET Developer"', () => {
    expect(valid('VB.NET Developer')).toBe('VB.NET Developer')
  })

  it('accepts "Socket.IO"', () => {
    expect(valid('Socket.IO')).toBe('Socket.IO')
  })

  it('accepts "Node.js Developer"', () => {
    expect(valid('Node.js Developer')).toBe('Node.js Developer')
  })

  it('accepts "asp.net developer" (case-insensitive)', () => {
    expect(valid('asp.net developer')).toBe('asp.net developer')
  })
})

describe('validateCustomLookingFor — URL/domain inputs must still be rejected', () => {
  it('rejects "google.com"', () => {
    expect(invalid('google.com')).toMatch(/URL/)
  })

  it('rejects "www.example"', () => {
    expect(invalid('www.example')).toMatch(/URL/)
  })

  it('rejects "example.io"', () => {
    expect(invalid('example.io')).toMatch(/URL/)
  })

  it('rejects "foo.net"', () => {
    expect(invalid('foo.net')).toMatch(/URL/)
  })

  it('rejects "my site.com"', () => {
    expect(invalid('my site.com')).toMatch(/URL/)
  })

  it('rejects "mysite.io"', () => {
    expect(invalid('mysite.io')).toMatch(/URL/)
  })

  it('rejects "http://x.y"', () => {
    expect(invalid('http://x.y')).toMatch(/URL/)
  })

  it('rejects "https://example.org"', () => {
    expect(invalid('https://example.org')).toMatch(/URL/)
  })
})

describe('validateCustomLookingFor — common technology names (valid)', () => {
  it('accepts "Node.JS"', () => {
    expect(valid('Node.JS')).toBe('Node.JS')
  })

  it('accepts "Next.js"', () => {
    expect(valid('Next.js')).toBe('Next.js')
  })

  it('accepts "Express.js"', () => {
    expect(valid('Express.js')).toBe('Express.js')
  })

  it('accepts "Ember.js"', () => {
    expect(valid('Ember.js')).toBe('Ember.js')
  })

  it('accepts "Vue.js"', () => {
    expect(valid('Vue.js')).toBe('Vue.js')
  })

  it('accepts "React.js Developer"', () => {
    expect(valid('React.js Developer')).toBe('React.js Developer')
  })

  it('accepts "D3.js"', () => {
    expect(valid('D3.js')).toBe('D3.js')
  })

  it('accepts "HTML"', () => {
    expect(valid('HTML')).toBe('HTML')
  })

  it('accepts "SCSS"', () => {
    expect(valid('SCSS')).toBe('SCSS')
  })

  it('accepts "gRPC"', () => {
    expect(valid('gRPC')).toBe('gRPC')
  })

  it('accepts "MVVM"', () => {
    expect(valid('MVVM')).toBe('MVVM')
  })

  it('accepts "XSLT"', () => {
    expect(valid('XSLT')).toBe('XSLT')
  })

  it('accepts "SQL"', () => {
    expect(valid('SQL')).toBe('SQL')
  })

  it('accepts "AWS"', () => {
    expect(valid('AWS')).toBe('AWS')
  })

  it('accepts "C++ Developer"', () => {
    expect(valid('C++ Developer')).toBe('C++ Developer')
  })

  it('accepts "C#"', () => {
    expect(valid('C#')).toBe('C#')
  })

  it('accepts "R"', () => {
    expect(valid('R')).toBe('R')
  })
})

describe('validateCustomLookingFor — still-rejected cases', () => {
  it('rejects "bcdfgh" (no vowels)', () => {
    expect(invalid('bcdfgh')).toBeTruthy()
  })

  it('rejects "asdf" (keyboard mash)', () => {
    expect(invalid('asdf')).toBeTruthy()
  })

  it('rejects "qwerty" (keyboard mash)', () => {
    expect(invalid('qwerty')).toBeTruthy()
  })

  it('rejects "google.com" (domain)', () => {
    expect(invalid('google.com')).toMatch(/URL/)
  })
})

describe('validateCustomLookingFor — additional accepted tech names', () => {
  it('accepts "tRPC"', () => {
    expect(valid('tRPC')).toBe('tRPC')
  })

  it('accepts "RxJS"', () => {
    expect(valid('RxJS')).toBe('RxJS')
  })

  it('accepts "pnpm"', () => {
    expect(valid('pnpm')).toBe('pnpm')
  })

  it('accepts "mTLS"', () => {
    expect(valid('mTLS')).toBe('mTLS')
  })

  it('accepts "MySQL"', () => {
    expect(valid('MySQL')).toBe('MySQL')
  })

  it('accepts "bcrypt"', () => {
    expect(valid('bcrypt')).toBe('bcrypt')
  })

  it('accepts "F#"', () => {
    expect(valid('F#')).toBe('F#')
  })

  it('accepts "C++" (bare)', () => {
    expect(valid('C++')).toBe('C++')
  })

  it('accepts "S3"', () => {
    expect(valid('S3')).toBe('S3')
  })

  it('accepts "Go"', () => {
    expect(valid('Go')).toBe('Go')
  })
})

describe('validateCustomLookingFor — extended rejection cases', () => {
  it('rejects "a" (single letter, not an allowlisted token)', () => {
    expect(invalid('a')).toBeTruthy()
  })

  it('rejects "x" (single letter, not an allowlisted token)', () => {
    expect(invalid('x')).toBeTruthy()
  })

  it('rejects "1" (single digit)', () => {
    expect(invalid('1')).toBeTruthy()
  })

  it('rejects "a++" (1 letter, not allowlisted)', () => {
    expect(invalid('a++')).toBeTruthy()
  })

  it('rejects "x#" (1 letter, not allowlisted)', () => {
    expect(invalid('x#')).toBeTruthy()
  })

  it('rejects "BCDFGH" (6 all-caps vowelless letters, exceeds short-token cap)', () => {
    expect(invalid('BCDFGH')).toBeTruthy()
  })

  it('rejects "ASDF" (uppercase keyboard mash)', () => {
    expect(invalid('ASDF')).toBeTruthy()
  })
})

describe('validateCustomLookingFor — casing variants of vowelless tech names', () => {
  it('accepts "html"', () => { expect(valid('html')).toBe('html') })
  it('accepts "Html"', () => { expect(valid('Html')).toBe('Html') })
  it('accepts "HTML"', () => { expect(valid('HTML')).toBe('HTML') })
  it('accepts "scss"', () => { expect(valid('scss')).toBe('scss') })
  it('accepts "Scss"', () => { expect(valid('Scss')).toBe('Scss') })
  it('accepts "SCSS"', () => { expect(valid('SCSS')).toBe('SCSS') })
  it('accepts "mvvm"', () => { expect(valid('mvvm')).toBe('mvvm') })
  it('accepts "xslt"', () => { expect(valid('xslt')).toBe('xslt') })
  it('accepts "htmx"', () => { expect(valid('htmx')).toBe('htmx') })
  it('accepts "HTMX"', () => { expect(valid('HTMX')).toBe('HTMX') })
  it('accepts "grpc"', () => { expect(valid('grpc')).toBe('grpc') })
  it('accepts "gRPC"', () => { expect(valid('gRPC')).toBe('gRPC') })
  it('accepts "GRPC"', () => { expect(valid('GRPC')).toBe('GRPC') })

  it('rejects "bcdfgh"', () => { expect(invalid('bcdfgh')).toBeTruthy() })
  it('rejects "BCDFGH"', () => { expect(invalid('BCDFGH')).toBeTruthy() })
  it('rejects "asdf"',   () => { expect(invalid('asdf')).toBeTruthy() })
  it('rejects "qwerty"', () => { expect(invalid('qwerty')).toBeTruthy() })
})

export type ValidationResult =
  | { ok: true; normalized: string }
  | { ok: false; error: string }

const ALLOWED_CHARS = /^[A-Za-z\d /\-.+#&()]+$/
const URL_SCHEME_RE = /https?:\/\/|www\./i
// Tech names whose suffix looks like a domain TLD but are legitimate framework names.
// Sorted longest-first so the regex engine tries the most specific alternative first.
const TECH_NAME_ALLOWLIST = ['socket.io', 'asp.net', 'vb.net'] as const
const TECH_NAMES_RE = new RegExp(
  [...TECH_NAME_ALLOWLIST]
    .sort((a, b) => b.length - a.length)
    .map(s => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|'),
  'gi',
)
// Domain-shaped token: starts with alnum, ends with a known TLD.
// Lookbehind/lookahead prevent partial matches inside longer identifiers.
const DOMAIN_TOKEN_RE = /(?<![a-z0-9-])[a-z0-9][a-z0-9-]*\.(com|org|net|io)(?![a-z0-9-])/i
const EMAIL_RE = /@[a-z0-9.-]+\.[a-z]{2,}/i
const HTML_RE = /<[^>]+>|&[a-z]+;/i
const REPEAT3_RE = /(.)\1{2,}/

const KEYBOARD_MASHES = new Set([
  'asdf', 'qwerty', 'zxcv', 'hjkl', 'uiop', 'bnm',
  'asd', 'qwe', 'zxc', 'dfg', 'ghj', 'jkl', 'xcv',
  'asdfg', 'qwert', 'zxcvb', 'asdfgh', 'zxcvbn',
])

const PLACEHOLDER_WORDS = new Set([
  'test', 'dummy', 'abc', 'xyz', 'asdf', 'none',
  'na', 'null', 'undefined', 'lorem', 'ipsum',
  'foo', 'bar', 'baz', 'qux', 'placeholder', 'n/a',
])

const PROFANITY = new Set([
  'fuck', 'shit', 'ass', 'bitch', 'cunt', 'dick',
  'cock', 'pussy', 'bastard', 'piss', 'twat', 'wank',
])

// Vowelless mixed-case acronyms that are legitimate technology names.
const VOWELLESS_ALLOWLIST = new Set(['grpc', 'trpc', 'rxjs', 'pnpm', 'mtls'])

// Short tokens (≤3 chars) that are well-known language / platform names.
// They skip only the min-length, min-letters, and vowel checks; all other
// rules (URL, characters, duplicates, placeholder, profanity) still apply.
const SHORT_TOKEN_ALLOWLIST = new Set(['c', 'r', 'c#', 'c++', 'f#', 's3', 'go'])

function collapseWhitespace(s: string): string {
  return s.replace(/\s+/g, ' ').trim()
}

function letterCount(s: string): number {
  return (s.match(/[a-zA-Z]/g) ?? []).length
}

function hasNoVowels(s: string): boolean {
  const letters = s.replace(/[^a-zA-Z]/g, '')
  if (letters.length < 4) return false
  if (VOWELLESS_ALLOWLIST.has(s.toLowerCase())) return false
  if (letters === letters.toUpperCase() && letters.length <= 5) return false
  return !/[aeiouy]/.test(letters.toLowerCase())
}

export function validateCustomLookingFor(
  raw: string,
  existingSelected: string[],
  allKnownValues: string[],
): ValidationResult {
  const normalized = collapseWhitespace(raw)
  const isShortToken = SHORT_TOKEN_ALLOWLIST.has(normalized.toLowerCase())

  if (!normalized) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }
  if (!isShortToken && normalized.length < 2) {
    return { ok: false, error: 'Must be at least 2 characters.' }
  }
  if (normalized.length > 40) {
    return { ok: false, error: 'Must be 40 characters or fewer.' }
  }
  // Check specific patterns before the allowed-chars gate so the error is specific
  if (EMAIL_RE.test(normalized)) {
    return { ok: false, error: 'Email addresses are not allowed.' }
  }
  if (URL_SCHEME_RE.test(normalized) || DOMAIN_TOKEN_RE.test(normalized.replace(TECH_NAMES_RE, ''))) {
    return { ok: false, error: 'URLs are not allowed.' }
  }
  if (HTML_RE.test(normalized)) {
    return { ok: false, error: 'HTML or script content is not allowed.' }
  }
  // Count words: skip bare punctuation tokens (/, +, etc.) that act as conjunctions
  const wordCount = normalized.split(/\s+/).filter((w) => /[A-Za-z\d]/.test(w)).length
  if (wordCount > 4) {
    return { ok: false, error: 'Use at most 4 words.' }
  }
  if (!ALLOWED_CHARS.test(normalized)) {
    return { ok: false, error: 'Only letters, digits, spaces and / - + . # & ( ) are allowed.' }
  }
  if (!isShortToken && letterCount(normalized) < 2) {
    return { ok: false, error: 'Must contain at least 2 letters.' }
  }
  if (/^\d+$/.test(normalized) || /^[^A-Za-z]+$/.test(normalized)) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }
  if (REPEAT3_RE.test(normalized.replace(/\s/g, ''))) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }

  const lower = normalized.toLowerCase()

  if (KEYBOARD_MASHES.has(lower)) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }
  if (PLACEHOLDER_WORDS.has(lower)) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }
  for (const word of normalized.split(/\s+/)) {
    if (PROFANITY.has(word.toLowerCase())) {
      return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
    }
  }
  if (!isShortToken && hasNoVowels(normalized)) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }

  if (existingSelected.map((v) => v.toLowerCase()).includes(lower)) {
    return { ok: false, error: 'Already selected.' }
  }
  if (allKnownValues.includes(lower)) {
    return { ok: false, error: 'This option is already in the list — select it from the chips above.' }
  }

  return { ok: true, normalized }
}

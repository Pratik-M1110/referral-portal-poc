export type ValidationResult =
  | { ok: true; normalized: string }
  | { ok: false; error: string }

const ALLOWED_CHARS = /^[A-Za-z\d /\-.+#&()]+$/
const URL_RE = /https?:\/\/|www\.|\.com\b|\.org\b|\.net\b|\.io\b/i
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

function collapseWhitespace(s: string): string {
  return s.replace(/\s+/g, ' ').trim()
}

function letterCount(s: string): number {
  return (s.match(/[a-zA-Z]/g) ?? []).length
}

function hasNoVowels(s: string): boolean {
  const letters = s.replace(/[^a-zA-Z]/g, '').toLowerCase()
  if (letters.length < 4) return false
  return !/[aeiou]/.test(letters)
}

export function validateCustomLookingFor(
  raw: string,
  existingSelected: string[],
  allKnownValues: string[],
): ValidationResult {
  const normalized = collapseWhitespace(raw)

  if (!normalized) {
    return { ok: false, error: 'Please enter a valid role or skill, like "Site Reliability Engineer".' }
  }
  if (normalized.length < 2) {
    return { ok: false, error: 'Must be at least 2 characters.' }
  }
  if (normalized.length > 40) {
    return { ok: false, error: 'Must be 40 characters or fewer.' }
  }
  // Check specific patterns before the allowed-chars gate so the error is specific
  if (EMAIL_RE.test(normalized)) {
    return { ok: false, error: 'Email addresses are not allowed.' }
  }
  if (URL_RE.test(normalized)) {
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
  if (letterCount(normalized) < 2) {
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
  if (hasNoVowels(normalized)) {
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

import { describe, expect, it } from 'vitest'
import { resolveLegacyAliases } from './lookingForOptions'

describe('resolveLegacyAliases — legacy mappings', () => {
  it('maps "Staff Software" -> "Staff Engineer"', () => {
    expect(resolveLegacyAliases(['Staff Software'])).toEqual(['Staff Engineer'])
  })

  it('maps "QA" -> "SDET / QA"', () => {
    expect(resolveLegacyAliases(['QA'])).toEqual(['SDET / QA'])
  })

  it('maps "QA Engineer" -> "SDET / QA"', () => {
    expect(resolveLegacyAliases(['QA Engineer'])).toEqual(['SDET / QA'])
  })

  it('leaves unknown values unchanged', () => {
    expect(resolveLegacyAliases(['Backend', 'Frontend'])).toEqual(['Backend', 'Frontend'])
  })

  it('handles empty array', () => {
    expect(resolveLegacyAliases([])).toEqual([])
  })
})

describe('resolveLegacyAliases — inherited property names return strings', () => {
  it.each(['constructor', 'toString', 'valueOf', 'hasOwnProperty', '__proto__'])(
    '"%s" is returned unchanged as a string',
    (key) => {
      const result = resolveLegacyAliases([key])
      expect(result).toEqual([key])
      expect(typeof result[0]).toBe('string')
    },
  )
})

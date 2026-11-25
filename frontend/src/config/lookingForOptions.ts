export type ExperienceBucket = 'student' | 'fresher' | '1-3' | '3-6' | '6+'

export interface LookingForOption {
  label: string
  value: string
  trending?: boolean
}

export const MAX_LOOKING_FOR = 6

export const LOOKING_FOR_BY_EXPERIENCE: Record<ExperienceBucket, LookingForOption[]> = {
  student: [
    { label: 'SDE Intern', value: 'SDE Intern', trending: true },
    { label: 'Frontend', value: 'Frontend', trending: true },
    { label: 'Backend', value: 'Backend' },
    { label: 'Full Stack', value: 'Full Stack' },
    { label: 'ML / AI', value: 'ML / AI', trending: true },
    { label: 'Data Analyst', value: 'Data Analyst' },
    { label: 'Cybersecurity', value: 'Cybersecurity' },
    { label: 'Open Source / GSoC', value: 'Open Source / GSoC' },
  ],
  fresher: [
    { label: 'Associate SWE', value: 'Associate SWE', trending: true },
    { label: 'Full Stack', value: 'Full Stack', trending: true },
    { label: 'Backend', value: 'Backend' },
    { label: 'Frontend', value: 'Frontend' },
    { label: 'Data Analyst', value: 'Data Analyst' },
    { label: 'GenAI Developer', value: 'GenAI Developer', trending: true },
    { label: 'SDET / QA', value: 'SDET / QA' },
    { label: 'Cloud Support', value: 'Cloud Support' },
  ],
  '1-3': [
    { label: 'Backend', value: 'Backend', trending: true },
    { label: 'Full Stack', value: 'Full Stack', trending: true },
    { label: 'Frontend', value: 'Frontend' },
    { label: 'Mobile', value: 'Mobile' },
    { label: 'Data Engineer', value: 'Data Engineer' },
    { label: 'GenAI / LLM Engineer', value: 'GenAI / LLM Engineer', trending: true },
    { label: 'DevOps', value: 'DevOps' },
    { label: 'SDET', value: 'SDET' },
    { label: 'Product Engineer', value: 'Product Engineer' },
  ],
  '3-6': [
    { label: 'Senior SWE', value: 'Senior SWE', trending: true },
    { label: 'Backend', value: 'Backend' },
    { label: 'Platform Engineer', value: 'Platform Engineer', trending: true },
    { label: 'Data Engineer', value: 'Data Engineer' },
    { label: 'AI Engineer (RAG / Agents)', value: 'AI Engineer (RAG / Agents)', trending: true },
    { label: 'MLOps', value: 'MLOps' },
    { label: 'SRE', value: 'SRE' },
    { label: 'Cloud Engineer', value: 'Cloud Engineer' },
    { label: 'Security Engineer', value: 'Security Engineer' },
    { label: 'Tech Lead', value: 'Tech Lead' },
  ],
  '6+': [
    { label: 'Staff Engineer', value: 'Staff Engineer', trending: true },
    { label: 'Principal Engineer', value: 'Principal Engineer' },
    { label: 'Tech Lead', value: 'Tech Lead' },
    { label: 'Engineering Manager', value: 'Engineering Manager', trending: true },
    { label: 'Solutions / Cloud Architect', value: 'Solutions / Cloud Architect' },
    { label: 'Platform Engineering', value: 'Platform Engineering' },
    { label: 'Applied AI Lead', value: 'Applied AI Lead', trending: true },
    { label: 'Data Platform Architect', value: 'Data Platform Architect' },
    { label: 'Security Architect', value: 'Security Architect' },
    { label: 'Founding Engineer', value: 'Founding Engineer' },
  ],
}

export const DEFAULT_LOOKING_FOR: LookingForOption[] = [
  { label: 'Frontend', value: 'Frontend' },
  { label: 'Backend', value: 'Backend' },
  { label: 'Full Stack', value: 'Full Stack' },
  { label: 'Mobile', value: 'Mobile' },
  { label: 'Data', value: 'Data' },
  { label: 'ML / AI', value: 'ML / AI' },
  { label: 'DevOps', value: 'DevOps' },
  { label: 'Cloud', value: 'Cloud' },
  { label: 'QA', value: 'QA' },
  { label: 'Design', value: 'Design' },
]

export const LEGACY_ALIAS: Record<string, string> = {
  'Staff Software': 'Staff Engineer',
  'QA': 'SDET / QA',
  'QA Engineer': 'SDET / QA',
}

export function resolveLegacyAliases(values: string[]): string[] {
  return Array.from(new Set(values.map((v) => Object.prototype.hasOwnProperty.call(LEGACY_ALIAS, v) ? LEGACY_ALIAS[v] : v)))
}

export function getAllKnownValues(): string[] {
  const seen = new Set<string>()
  for (const opts of Object.values(LOOKING_FOR_BY_EXPERIENCE)) {
    for (const o of opts) seen.add(o.value.toLowerCase())
  }
  for (const o of DEFAULT_LOOKING_FOR) seen.add(o.value.toLowerCase())
  for (const [k, v] of Object.entries(LEGACY_ALIAS)) {
    seen.add(k.toLowerCase())
    seen.add(v.toLowerCase())
  }
  return Array.from(seen)
}

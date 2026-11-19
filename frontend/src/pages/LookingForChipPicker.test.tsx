import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LookingForChipPicker } from './LookingForChipPicker'
import { MAX_LOOKING_FOR } from '../config/lookingForOptions'

function setup(props: Partial<Parameters<typeof LookingForChipPicker>[0]> = {}) {
  const onChange = vi.fn()
  const onPrunedToast = vi.fn()
  const defaults = {
    experienceLevel: 'student',
    value: [] as string[],
    onChange,
    onPrunedToast,
  }
  const merged = { ...defaults, ...props }
  const { rerender } = render(<LookingForChipPicker {...merged} />)
  return { onChange, onPrunedToast, rerender, merged }
}

describe('LookingForChipPicker — option lists', () => {
  it('shows student options when experience is student', () => {
    setup({ experienceLevel: 'student' })
    expect(screen.getByText('SDE Intern')).toBeInTheDocument()
    expect(screen.queryByText('Senior SWE')).not.toBeInTheDocument()
  })

  it('shows 3-6 options when experience is 3-6', () => {
    setup({ experienceLevel: '3-6' })
    expect(screen.getByText('Senior SWE')).toBeInTheDocument()
    expect(screen.queryByText('SDE Intern')).not.toBeInTheDocument()
  })

  it('shows 6+ options when experience is 6+', () => {
    setup({ experienceLevel: '6+' })
    expect(screen.getByText('Staff Engineer')).toBeInTheDocument()
  })

  it('falls back to default options for unknown experience', () => {
    setup({ experienceLevel: '' })
    expect(screen.getByText('Frontend')).toBeInTheDocument()
    expect(screen.getByText('Backend')).toBeInTheDocument()
  })

  it.each(['constructor', 'toString', '__proto__'])(
    'falls back to default options when experienceLevel is "%s"',
    (level) => {
      setup({ experienceLevel: level })
      expect(screen.getByText('Frontend')).toBeInTheDocument()
      expect(screen.getByText('Backend')).toBeInTheDocument()
    },
  )

  it('shows trending badge on trending chips', () => {
    setup({ experienceLevel: 'student' })
    // SDE Intern is trending — its button should contain a ↑ span
    const sdeBtn = screen.getByText('SDE Intern').closest('button')
    expect(sdeBtn?.textContent).toContain('↑')
  })

  it('shows "More" button when bucket has >8 options', () => {
    setup({ experienceLevel: '3-6' }) // 10 options
    expect(screen.getByText(/\+\d+ more/)).toBeInTheDocument()
  })

  it('does not show "More" button when bucket has ≤8 options', () => {
    setup({ experienceLevel: 'student' }) // 8 options
    expect(screen.queryByText(/\+\d+ more/)).not.toBeInTheDocument()
  })

  it('expands to show all options when "More" is clicked', () => {
    setup({ experienceLevel: '3-6' })
    fireEvent.click(screen.getByText(/\+\d+ more/))
    expect(screen.getByText('Tech Lead')).toBeInTheDocument()
  })
})

describe('LookingForChipPicker — selection and counter', () => {
  it('counter shows 0 of MAX_LOOKING_FOR at start', () => {
    setup({ value: [] })
    expect(screen.getByTestId('counter').textContent).toBe(`0 of ${MAX_LOOKING_FOR} selected`)
  })

  it('counter reflects current selection size', () => {
    setup({ experienceLevel: 'student', value: ['Frontend', 'Backend'] })
    expect(screen.getByTestId('counter').textContent).toBe(`2 of ${MAX_LOOKING_FOR} selected`)
  })

  it('calls onChange when a chip is clicked', () => {
    const { onChange } = setup({ experienceLevel: 'student', value: [] })
    fireEvent.click(screen.getByText('Frontend'))
    expect(onChange).toHaveBeenCalledWith(['Frontend'])
  })

  it('deselects when a selected chip is clicked', () => {
    const { onChange } = setup({ experienceLevel: 'student', value: ['Frontend'] })
    fireEvent.click(screen.getByText('Frontend'))
    expect(onChange).toHaveBeenCalledWith([])
  })

  it('at MAX: unselected chips are aria-disabled', () => {
    const full = ['Frontend', 'Backend', 'Full Stack', 'ML / AI', 'Data Analyst', 'Cybersecurity']
    setup({ experienceLevel: 'student', value: full })
    const openSourceBtn = screen.getByText('Open Source / GSoC').closest('button')
    expect(openSourceBtn).toHaveAttribute('aria-disabled', 'true')
  })

  it('at MAX: selected chips remain clickable (not aria-disabled)', () => {
    const full = ['Frontend', 'Backend', 'Full Stack', 'ML / AI', 'Data Analyst', 'Cybersecurity']
    setup({ experienceLevel: 'student', value: full })
    const frontendBtn = screen.getByText('Frontend').closest('button')
    expect(frontendBtn).not.toHaveAttribute('aria-disabled')
  })

  it('at MAX: custom input is disabled', () => {
    const full = ['Frontend', 'Backend', 'Full Stack', 'ML / AI', 'Data Analyst', 'Cybersecurity']
    setup({ experienceLevel: 'student', value: full })
    expect(screen.getByTestId('custom-input')).toBeDisabled()
  })

  it('at MAX-1: custom input is enabled', () => {
    const almostFull = ['Frontend', 'Backend', 'Full Stack', 'ML / AI', 'Data Analyst']
    setup({ experienceLevel: 'student', value: almostFull })
    expect(screen.getByTestId('custom-input')).not.toBeDisabled()
  })

  it('deselecting at MAX re-enables other chips', () => {
    const full = ['Frontend', 'Backend', 'Full Stack', 'ML / AI', 'Data Analyst', 'Cybersecurity']
    const { onChange, rerender, merged } = setup({ experienceLevel: 'student', value: full })
    fireEvent.click(screen.getByText('Frontend'))
    const newValue = full.filter((v) => v !== 'Frontend')
    rerender(<LookingForChipPicker {...merged} value={newValue} onChange={onChange} />)
    const openSourceBtn = screen.getByText('Open Source / GSoC').closest('button')
    expect(openSourceBtn).not.toHaveAttribute('aria-disabled')
  })
})

describe('LookingForChipPicker — experience change pruning', () => {
  it('prunes selections not valid for new bucket', async () => {
    const { onChange, onPrunedToast, rerender, merged } = setup({
      experienceLevel: 'student',
      value: ['SDE Intern', 'Frontend'], // SDE Intern is in student, not in 1-3
    })

    await act(async () => {
      rerender(
        <LookingForChipPicker
          {...merged}
          experienceLevel="1-3"
          value={['SDE Intern', 'Frontend']}
        />
      )
    })

    // Frontend is in 1-3, SDE Intern is not
    expect(onChange).toHaveBeenCalledWith(['Frontend'])
    expect(onPrunedToast).toHaveBeenCalled()
  })

  it('keeps selections that are in the new bucket', async () => {
    const { onChange, rerender, merged } = setup({
      experienceLevel: 'student',
      value: ['Frontend', 'Backend'],
    })

    await act(async () => {
      rerender(
        <LookingForChipPicker
          {...merged}
          experienceLevel="1-3"
          value={['Frontend', 'Backend']}
        />
      )
    })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('resolves legacy alias QA -> SDET / QA on experience change', async () => {
    const { onChange, rerender, merged } = setup({
      experienceLevel: 'student',
      value: ['QA'], // legacy alias
    })

    await act(async () => {
      rerender(
        <LookingForChipPicker
          {...merged}
          experienceLevel="fresher"
          value={['QA']}
        />
      )
    })

    // QA resolves to SDET / QA which IS in the fresher bucket
    expect(onChange).toHaveBeenCalledWith(['SDET / QA'])
  })

  it('keeps custom values matching inherited object property names', async () => {
    const values = ['constructor', 'toString']
    const { onChange, rerender, merged } = setup({
      experienceLevel: 'student',
      value: values,
    })

    await act(async () => {
      rerender(
        <LookingForChipPicker
          {...merged}
          experienceLevel="fresher"
          value={values}
        />
      )
    })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('renders without throwing when value contains inherited property names', () => {
    expect(() =>
      setup({ experienceLevel: 'student', value: ['constructor', 'toString'] }),
    ).not.toThrow()
  })

  it('does not call onChange when experience does not change', () => {
    const { onChange, rerender, merged } = setup({
      experienceLevel: 'student',
      value: ['Frontend'],
    })
    rerender(<LookingForChipPicker {...merged} value={['Frontend']} />)
    expect(onChange).not.toHaveBeenCalled()
  })
})

describe('LookingForChipPicker — custom input validation', () => {
  it('shows error for invalid custom input', () => {
    setup({ experienceLevel: 'student', value: [] })
    const input = screen.getByTestId('custom-input') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'asdf' } })
    fireEvent.click(screen.getByText('Add'))
    expect(screen.getByTestId('custom-error')).toBeInTheDocument()
  })

  it('adds valid custom value', () => {
    const { onChange } = setup({ experienceLevel: 'student', value: [] })
    const input = screen.getByTestId('custom-input') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'Site Reliability Engineer' } })
    fireEvent.click(screen.getByText('Add'))
    expect(onChange).toHaveBeenCalledWith(['Site Reliability Engineer'])
  })

  it('clears error when input changes', () => {
    setup({ experienceLevel: 'student', value: [] })
    const input = screen.getByTestId('custom-input') as HTMLInputElement
    fireEvent.change(input, { target: { value: 'asdf' } })
    fireEvent.click(screen.getByText('Add'))
    expect(screen.getByTestId('custom-error')).toBeInTheDocument()
    fireEvent.change(input, { target: { value: 'x' } })
    expect(screen.queryByTestId('custom-error')).not.toBeInTheDocument()
  })
})

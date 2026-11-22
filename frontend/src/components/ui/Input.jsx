import { forwardRef, useId } from 'react'

const Input = forwardRef(function Input(
  {
    label,
    error,
    hint,
    className = '',
    style = {},
    id: providedId,
    fullWidth = true,
    ...props
  },
  ref
) {
  const generatedId = useId()
  const id = providedId || generatedId
  const errorId = `${id}-error`
  const hintId = `${id}-hint`

  const baseStyles = `
    w-full
    bg-[var(--color-bg-elevated)]
    border
    border-[var(--color-border)]
    rounded-[var(--radius-md)]
    text-[var(--color-text)]
    placeholder-[var(--color-text-subtle)]
    transition-all
    duration-200
    outline-none
  `

  const sizeStyles = 'px-4 py-2.5 text-sm'

  const stateStyles = error
    ? 'border-[var(--color-error)] focus:border-[var(--color-error)] focus:ring-1 focus:ring-[var(--color-error)]'
    : 'focus:border-[var(--color-accent)] focus:ring-1 focus:ring-[var(--color-accent)]'

  const disabledStyles = props.disabled
    ? 'opacity-50 cursor-not-allowed bg-[var(--color-bg-card)]'
    : ''

  const widthStyle = fullWidth ? 'w-full' : ''

  return (
    <div className={`${className} ${widthStyle}`} style={style}>
      {label && (
        <label
          htmlFor={id}
          className="block text-sm font-medium text-[var(--color-text)] mb-1.5"
        >
          {label}
        </label>
      )}
      <input
        ref={ref}
        id={id}
        className={`${baseStyles} ${sizeStyles} ${stateStyles} ${disabledStyles}`}
        aria-invalid={error ? 'true' : 'false'}
        aria-describedby={error ? errorId : hint ? hintId : undefined}
        {...props}
      />
      {error && (
        <p id={errorId} className="mt-1.5 text-xs text-[var(--color-error)]" role="alert">
          {error}
        </p>
      )}
      {hint && !error && (
        <p id={hintId} className="mt-1.5 text-xs text-[var(--color-text-subtle)]">
          {hint}
        </p>
      )}
    </div>
  )
})

Input.displayName = 'Input'
export default Input
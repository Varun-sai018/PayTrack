import { forwardRef } from 'react'

const Button = forwardRef(function Button(
  {
    children,
    variant = 'primary',
    size = 'md',
    disabled = false,
    loading = false,
    fullWidth = false,
    type = 'button',
    className = '',
    style = {},
    onClick,
    ...props
  },
  ref
) {
  const baseStyles = `
    display: inline-flex
    items-center
    justify-center
    gap-2
    font-family: var(--font-display)
    font-weight: 500
    border: none
    border-radius: var(--radius-md)
    cursor: pointer
    transition: all var(--transition-fast)
    text-decoration: none
    white-space: nowrap
    outline: none
  `

  const variants = {
    primary: `
      background: var(--color-accent)
      color: var(--color-bg)
      box-shadow: var(--shadow-sm)
    `,
    secondary: `
      background: var(--color-bg-elevated)
      color: var(--color-text)
      border: 1px solid var(--color-border)
    `,
    ghost: `
      background: transparent
      color: var(--color-text-muted)
    `,
    danger: `
      background: var(--color-error)
      color: white
      box-shadow: var(--shadow-sm)
    `,
    outline: `
      background: transparent
      color: var(--color-accent)
      border: 1px solid var(--color-accent)
    `,
  }

  const sizes = {
    sm: 'px-3 py-1.5 text-xs',
    md: 'px-5 py-2 text-sm',
    lg: 'px-7 py-3 text-base',
  }

  const widthStyle = fullWidth ? 'w-full' : ''

  const disabledStyle = disabled || loading
    ? 'opacity-50 cursor-not-allowed pointer-events-none'
    : ''

  const hoverStyles = {
    primary: 'hover:brightness-110 active:brightness-95',
    secondary: 'hover:bg-[var(--color-bg-card)] hover:border-[var(--color-border-hover)]',
    ghost: 'hover:bg-[var(--color-bg-card)] hover:text-[var(--color-text)]',
    danger: 'hover:brightness-110 active:brightness-95',
    outline: 'hover:bg-[var(--color-accent-glow)]',
  }

  const classNames = [
    baseStyles,
    variants[variant],
    sizes[size],
    widthStyle,
    disabledStyle,
    hoverStyles[variant],
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      ref={ref}
      type={type}
      className={classNames}
      style={style}
      disabled={disabled || loading}
      onClick={onClick}
      {...props}
    >
      {loading && (
        <svg
          className="w-4 h-4 animate-spin"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          aria-hidden="true"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
      )}
      {children}
    </button>
  )
})

Button.displayName = 'Button'
export default Button
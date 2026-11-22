import { forwardRef } from 'react'

const Badge = forwardRef(function Badge(
  {
    children,
    variant = 'default',
    size = 'md',
    className = '',
    style = {},
    ...props
  },
  ref
) {
  const variants = {
    default: 'bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] border border-[var(--color-border)]',
    success: 'bg-[rgba(16,185,129,0.15)] text-[var(--color-success)] border border-[rgba(16,185,129,0.3)]',
    warning: 'bg-[rgba(245,158,11,0.15)] text-[var(--color-warning)] border border-[rgba(245,158,11,0.3)]',
    error: 'bg-[rgba(239,68,68,0.15)] text-[var(--color-error)] border border-[rgba(239,68,68,0.3)]',
    info: 'bg-[rgba(59,130,246,0.15)] text-[var(--color-info)] border border-[rgba(59,130,246,0.3)]',
    accent: 'bg-[var(--color-accent-glow)] text-[var(--color-accent)] border border-[var(--color-accent)]',
  }

  const sizes = {
    sm: 'px-2 py-0.5 text-xs',
    md: 'px-2.5 py-1 text-xs',
    lg: 'px-3 py-1.5 text-sm',
  }

  return (
    <span
      ref={ref}
      className={`
        inline-flex
        items-center
        font-family: var(--font-display)
        font-weight: 500
        rounded-[var(--radius-full)]
        ${variants[variant]}
        ${sizes[size]}
        ${className}
      `}
      style={style}
      {...props}
    >
      {children}
    </span>
  )
})

Badge.displayName = 'Badge'
export default Badge
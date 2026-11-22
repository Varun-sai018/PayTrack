import { forwardRef } from 'react'

const Card = forwardRef(function Card(
  {
    children,
    className = '',
    style = {},
    padding = 'md',
    hover = false,
    bordered = true,
    ...props
  },
  ref
) {
  const paddingStyles = {
    none: '',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
  }

  const borderStyle = bordered
    ? 'border border-[var(--color-border)]'
    : ''

  const hoverStyle = hover
    ? 'transition-all duration-200 hover:border-[var(--color-border-hover)] hover:shadow-[var(--shadow-md)]'
    : ''

  return (
    <div
      ref={ref}
      className={`
        bg-[var(--color-bg-card)]
        rounded-[var(--radius-lg)]
        ${paddingStyles[padding]}
        ${borderStyle}
        ${hoverStyle}
        ${className}
      `}
      style={style}
      {...props}
    >
      {children}
    </div>
  )
})

Card.displayName = 'Card'
export default Card
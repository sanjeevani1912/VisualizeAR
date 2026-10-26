import type { PlanStatus } from '../types'
import { STATUS_META } from '../util'

export function StatusBadge({ status }: { status: PlanStatus }) {
  const m = STATUS_META[status]
  return (
    <span className={`badge badge-${m.tone}`}>
      <span className="badge-dot" aria-hidden="true" />
      {m.label}
    </span>
  )
}

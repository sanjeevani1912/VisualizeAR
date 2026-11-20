import { assetIconUrl } from '../assetIcons'
import type { AssetType } from '../types'

/** Decorative asset symbol for legends and tables. The text next to it carries the meaning, so it is hidden from screen readers. */
export function AssetIcon({ type, size = 18 }: { type: AssetType; size?: number }) {
  return <img className="asset-icon" src={assetIconUrl(type)} width={size} height={size} alt="" aria-hidden="true" draggable={false} />
}

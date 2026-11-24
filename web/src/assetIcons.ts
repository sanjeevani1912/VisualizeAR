// Small top-down symbols for every asset type, drawn by us (no icon set, no licence to record).
//
// Each glyph is drawn on a 24 x 24 grid with its FORWARD direction pointing UP (top views), except the three side-view
// vehicle icons (military vehicle, truck, car), which face right and stay upright on the map (see UPRIGHT_TYPES). The glyph is placed on a
// rounded badge in the asset's colour so it stays readable on any background (beige, satellite-like,
// busy basemap). The map rotates the whole badge by (heading - rotationDegrees), see MapView.tsx.
//
// Glyph colour (white or near-black) is chosen per badge colour so contrast stays at least 4.5:1 where possible.

import { ASSET_DEFAULTS } from './geo'
import type { AssetType } from './types'

const INK = '#231F20'
const PAPER = '#FCF9F2'

/** Glyph markup. `c` is the glyph colour. Fill and stroke use `c`; shapes with fill="none" are outlines. */
const GLYPHS: Record<AssetType, (c: string, bg: string) => string> = {
  // Rotor cross over a body, tail boom and tail rotor.
  GUNSHIP: (c) =>
    `<g fill="${c}" stroke="${c}" stroke-linecap="round">` +
    `<path d="M4.2 3.2L19.8 18.8M19.8 3.2L4.2 18.8" stroke-width="1.8" fill="none"/>` +
    `<ellipse cx="12" cy="11" rx="2.8" ry="4.6" stroke="none"/>` +
    `<rect x="11.2" y="13" width="1.6" height="8" stroke="none"/>` +
    `<rect x="8.6" y="20" width="6.8" height="1.6" stroke="none"/></g>`,
  // Fixed-wing: centre line, straight wings, tail plane, nose bar.
  K131: (c) =>
    `<g fill="${c}"><rect x="10.9" y="2" width="2.2" height="19" rx="1.1"/>` +
    `<rect x="3.5" y="8.6" width="17" height="2.6" rx="1"/>` +
    `<rect x="8" y="18" width="8" height="1.8" rx="0.9"/></g>`,
  // Same aircraft, shifted up, with two dashes behind it for the take-off run.
  K131_TAKEOFF: (c) =>
    `<g fill="${c}"><rect x="10.9" y="1.5" width="2.2" height="14" rx="1.1"/>` +
    `<rect x="4.5" y="6" width="15" height="2.4" rx="1"/>` +
    `<rect x="8.5" y="12.6" width="7" height="1.6" rx="0.8"/>` +
    `<rect x="11" y="17" width="2" height="2" rx="0.4"/><rect x="11" y="21" width="2" height="2" rx="0.4"/></g>`,
  // SIDE VIEW (faces right), armoured vehicle: sloped hull, window slit, three wheels.
  MILITARY_VEHICLE: (c, bg) =>
    `<g fill="${c}"><path d="M2.5 9h13.5l4.5 4h1.5v4.5h-19.5z"/>` +
    `<path d="M12.5 10.6h3l2.2 2h-5.2z" fill="${bg}"/>` +
    `<g stroke="${bg}" stroke-width="1.3"><circle cx="6.5" cy="17.8" r="2.4"/><circle cx="12" cy="17.8" r="2.4"/><circle cx="17.5" cy="17.8" r="2.4"/></g></g>`,
  // SIDE VIEW (faces right): boxy jeep, open cabin with windscreen, two large wheels.
  OFFROAD_VEHICLE: (c, bg) =>
    `<g fill="${c}"><rect x="2.5" y="10.5" width="19" height="5" rx="1"/>` +
    `<path d="M5 10.5V6.8h8.5l2.2 3.7z"/><path d="M6.6 8h5.6v2.1H6.6z" fill="${bg}"/>` +
    `<g stroke="${bg}" stroke-width="1.3"><circle cx="7.5" cy="16.8" r="3.2"/><circle cx="17.5" cy="16.8" r="3.2"/></g></g>`,
  // SIDE VIEW (faces right): wheeled armoured hull, turret, long gun barrel, four wheels.
  STR_MGS: (c, bg) =>
    `<g fill="${c}"><path d="M2.5 11.5h16.5l3 2.2v2.8H2.5z"/>` +
    `<rect x="7" y="7" width="6.5" height="4.5" rx="1"/><rect x="13" y="8" width="9.5" height="1.5" rx="0.5"/>` +
    `<g stroke="${bg}" stroke-width="1.2"><circle cx="5.5" cy="17.6" r="2.2"/><circle cx="10" cy="17.6" r="2.2"/><circle cx="14.5" cy="17.6" r="2.2"/><circle cx="19" cy="17.6" r="2.2"/></g></g>`,
  // Roof outline, ridge line and a door chevron at the front.
  TENT: (c) =>
    `<g fill="none" stroke="${c}" stroke-width="1.7" stroke-linejoin="round" stroke-linecap="round">` +
    `<rect x="5" y="3.5" width="14" height="17" rx="1.2"/><path d="M12 3.5V20.5"/><path d="M8.6 8.8L12 5.6L15.4 8.8"/></g>`,
  // SIDE VIEW (faces right): long cargo box, cab with window, three wheels.
  TRUCK: (c, bg) =>
    `<g fill="${c}"><rect x="1.5" y="6" width="13" height="10.5" rx="1"/>` +
    `<path d="M15.5 9h4.2l3 3.6v3.9h-7.2z"/><path d="M17 10.5h2.3l1.7 2.1H17z" fill="${bg}"/>` +
    `<g stroke="${bg}" stroke-width="1.3"><circle cx="6" cy="17.8" r="2.4"/><circle cx="11" cy="17.8" r="2.4"/><circle cx="19" cy="17.8" r="2.4"/></g></g>`,
  // SIDE VIEW (faces right): low body, cabin with two windows, two wheels.
  CAR: (c, bg) =>
    `<g fill="${c}"><path d="M2 14.2c0-1.5.8-2.4 2.2-2.7L7 11l2.6-3.6h6l3 3.6 2.7.6c1.1.3 1.7 1.1 1.7 2.4v2.6H2z"/>` +
    `<path d="M10.2 8.6h2.6V11h-4.1zM14 8.6h1.4l1.9 2.4H14z" fill="${bg}"/>` +
    `<g stroke="${bg}" stroke-width="1.3"><circle cx="7" cy="17" r="2.5"/><circle cx="17" cy="17" r="2.5"/></g></g>`,
  // Mast with a transmitter dot and radio arcs either side.
  ANTENNA: (c) =>
    `<g fill="${c}" stroke="${c}" stroke-width="1.7" stroke-linecap="round">` +
    `<circle cx="12" cy="10" r="1.9" stroke="none"/><path d="M12 12V21.5" fill="none"/>` +
    `<path d="M8.6 6.6a4.8 4.8 0 0 0 0 6.8M15.4 6.6a4.8 4.8 0 0 1 0 6.8M6 4.2a8.4 8.4 0 0 0 0 11.6M18 4.2a8.4 8.4 0 0 1 0 11.6" fill="none"/></g>`,
  // SIDE VIEW / cross-section: a block of ground with a deep notch cut into it.
  TRENCH: (c, bg) =>
    `<g fill="${c}"><rect x="1.5" y="7" width="21" height="12" rx="1"/>` +
    `<rect x="8.5" y="7" width="7" height="8.5" fill="${bg}"/>` +
    `<path d="M3.5 4.5h3M17.5 4.5h3" stroke="${c}" stroke-width="1.6" stroke-linecap="round"/></g>`,
}

function luminance(hex: string): number {
  const v = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2]
}

/** White or near-black, whichever has better contrast against the badge colour. */
export function glyphColor(badge: string): string {
  const l = luminance(badge)
  const white = 1.05 / (l + 0.05)
  const ink = (l + 0.05) / (luminance(INK) + 0.05)
  return white >= ink ? '#FFFFFF' : INK
}

/** Complete SVG (32 x 32 viewBox): coloured rounded badge with a pale edge, glyph centred, forward = up. */
export function assetIconSvg(type: AssetType): string {
  const color = ASSET_DEFAULTS[type].color
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">` +
    `<rect x="1.5" y="1.5" width="29" height="29" rx="7" fill="${color}" stroke="${PAPER}" stroke-width="2"/>` +
    `<rect x="0.5" y="0.5" width="31" height="31" rx="7.6" fill="none" stroke="${INK}" stroke-opacity="0.55" stroke-width="1"/>` +
    `<g transform="translate(4 4)">${GLYPHS[type](glyphColor(color), color)}</g></svg>`
  )
}

/** Rust rounded-square ring, drawn around the selected asset on the map. */
export const SELECTION_RING_SVG =
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40" width="40" height="40">` +
  `<rect x="2" y="2" width="36" height="36" rx="9" fill="none" stroke="#B56B37" stroke-width="3.5"/></svg>`

export function svgDataUrl(svg: string): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export function assetIconUrl(type: AssetType): string {
  return svgDataUrl(assetIconSvg(type))
}

export const ASSET_TYPES = Object.keys(ASSET_DEFAULTS) as AssetType[]

/** Side-view icons: they stay upright on the map (a side view turned upside down would be unreadable). Their footprint shows the orientation. */
export const UPRIGHT_TYPES: ReadonlySet<AssetType> = new Set<AssetType>(['MILITARY_VEHICLE', 'OFFROAD_VEHICLE', 'STR_MGS', 'TRUCK', 'CAR', 'TRENCH'])

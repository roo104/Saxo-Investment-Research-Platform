import type {AllocationSlice} from '../allocation'

const VIEW = 120
const CENTER = VIEW / 2
const RADIUS = 48
const STROKE = 16
const CIRCUMFERENCE = 2 * Math.PI * RADIUS
// A hairline gap (in circumference units) between segments, matching the reference's split ring.
const GAP = 3

interface Props {
    slices: AllocationSlice[]
}

/**
 * A ring chart in the inline-SVG idiom used elsewhere in the app (fixed viewBox, colours via CSS
 * custom properties). Each slice is one circle whose dash pattern draws just its arc; segments are
 * offset by the running total and the whole ring is rotated so it starts at 12 o'clock. Colours are
 * assigned upstream in allocation.ts. The centre is intentionally empty.
 */
export function DonutChart({slices}: Props) {
    let offset = 0
    return (
        <svg className="donut" viewBox={`0 0 ${VIEW} ${VIEW}`} role="img"
             aria-label={slices.map((s) => `${s.label} ${(s.pct * 100).toFixed(1)}%`).join(', ') || 'No data'}>
            {/* Track shows through the gaps between segments. */}
            <circle cx={CENTER} cy={CENTER} r={RADIUS} fill="none" stroke="var(--border)" strokeWidth={STROKE}/>
            {slices.map((s) => {
                const arc = s.pct * CIRCUMFERENCE
                const dash = Math.max(arc - GAP, 0)
                const seg = (
                    <circle
                        key={s.label}
                        cx={CENTER}
                        cy={CENTER}
                        r={RADIUS}
                        fill="none"
                        stroke={s.color}
                        strokeWidth={STROKE}
                        strokeDasharray={`${dash} ${CIRCUMFERENCE - dash}`}
                        strokeDashoffset={-offset}
                        transform={`rotate(-90 ${CENTER} ${CENTER})`}
                    />
                )
                offset += arc
                return seg
            })}
        </svg>
    )
}

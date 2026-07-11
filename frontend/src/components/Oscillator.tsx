// Small fixed-viewBox sub-charts for indicators that live on their own scale (not the price axis):
// RSI on a 0..100 band, and MACD with its signal line and histogram. Rendered below the price chart.

const OW = 560
const OH = 96
const PAD = {top: 10, right: 16, bottom: 14, left: 34}
const INNER_W = OW - PAD.left - PAD.right
const INNER_H = OH - PAD.top - PAD.bottom

const xAt = (n: number) => (i: number) => PAD.left + (n <= 1 ? 0 : (i / (n - 1)) * INNER_W)

/** Build an SVG path over a nullable series, breaking the line across warm-up gaps. */
function linePath(values: (number | null)[], x: (i: number) => number, y: (v: number) => number): string {
    let d = ''
    let pen = false
    values.forEach((v, i) => {
        if (v == null || !Number.isFinite(v)) {
            pen = false
            return
        }
        d += `${pen ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)} `
        pen = true
    })
    return d.trim()
}

export function RsiPanel({values, tip}: { values: (number | null)[]; tip?: string }) {
    if (values.length < 2) return null
    const x = xAt(values.length)
    const y = (v: number) => PAD.top + INNER_H * (1 - v / 100)
    return (
        <div className="osc">
            <div className={`osc-title${tip ? ' has-tip' : ''}`} tabIndex={tip ? 0 : undefined}>
                RSI (14)
                {tip ? <span className="sig-info" aria-hidden="true">i</span> : null}
                {tip ? <span className="sig-tip" role="tooltip">{tip}</span> : null}
            </div>
            <svg className="osc-svg" viewBox={`0 0 ${OW} ${OH}`} preserveAspectRatio="none" role="img" aria-label="RSI">
                <rect className="osc-zone" x={PAD.left} width={INNER_W} y={y(70)} height={y(30) - y(70)}/>
                {[30, 70].map((g) => (
                    <line key={g} className="osc-guide" x1={PAD.left} x2={OW - PAD.right} y1={y(g)} y2={y(g)}/>
                ))}
                <path className="osc-line osc-rsi" d={linePath(values, x, y)} vectorEffect="non-scaling-stroke"/>
                <text className="osc-axis" x={PAD.left - 5} y={y(70) + 3} textAnchor="end">70</text>
                <text className="osc-axis" x={PAD.left - 5} y={y(30) + 3} textAnchor="end">30</text>
            </svg>
        </div>
    )
}

export function MacdPanel(
    {macd, signal, histogram, tip}:
    { macd: (number | null)[]; signal: (number | null)[]; histogram: (number | null)[]; tip?: string },
) {
    const finite = [...macd, ...signal, ...histogram].filter((v): v is number => v != null && Number.isFinite(v))
    if (macd.length < 2 || finite.length === 0) return null
    const bound = Math.max(...finite.map(Math.abs)) || 1
    const x = xAt(macd.length)
    const y = (v: number) => PAD.top + INNER_H * (1 - (v + bound) / (2 * bound))
    const barW = Math.max(1, (INNER_W / macd.length) * 0.6)
    const zero = y(0)
    return (
        <div className="osc">
            <div className={`osc-title${tip ? ' has-tip' : ''}`} tabIndex={tip ? 0 : undefined}>
                MACD (12,26,9)
                {tip ? <span className="sig-info" aria-hidden="true">i</span> : null}
                {tip ? <span className="sig-tip up" role="tooltip">{tip}</span> : null}
            </div>
            <svg className="osc-svg" viewBox={`0 0 ${OW} ${OH}`} preserveAspectRatio="none" role="img"
                 aria-label="MACD">
                <line className="osc-guide" x1={PAD.left} x2={OW - PAD.right} y1={zero} y2={zero}/>
                {histogram.map((v, i) => {
                    if (v == null || !Number.isFinite(v)) return null
                    const yv = y(v)
                    return (
                        <rect key={i} className={`osc-hist ${v >= 0 ? 'up' : 'down'}`} x={x(i) - barW / 2}
                              width={barW} y={Math.min(yv, zero)} height={Math.max(0.5, Math.abs(yv - zero))}/>
                    )
                })}
                <path className="osc-line osc-macd" d={linePath(macd, x, y)} vectorEffect="non-scaling-stroke"/>
                <path className="osc-line osc-signal" d={linePath(signal, x, y)} vectorEffect="non-scaling-stroke"/>
            </svg>
        </div>
    )
}

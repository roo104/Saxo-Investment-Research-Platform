import type {EnvironmentInfo} from '../types'

/** Small pill in the top bar showing which Saxo environment the backend is wired to. */
export function EnvironmentBadge({env}: { env: EnvironmentInfo | null }) {
    if (!env) {
        return (
            <span className="env-badge">
        <span className="spinner"/>
        connecting
      </span>
        )
    }
    const isLive = env.environment.toUpperCase() === 'LIVE'
    return (
        <span
            className={`env-badge${isLive ? ' is-live' : ''}`}
            title={env.restBaseUrl}
        >
      <span className="dot"/>
      env <strong>{env.environment}</strong>
    </span>
    )
}

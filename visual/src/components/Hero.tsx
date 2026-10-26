import { GATES } from '../data/site';
import type { SectionId } from '../data/site';

export function Hero({ onNavigate }: { onNavigate: (id: SectionId) => void }) {
  return (
    <header className="hero">
      <div className="hero-copy">
        <div className="hero-kicker kicker">StructureCumulus / Design review 1.0</div>
        <h1 className="hero-title">
          A structure<br />
          you can <em>prove.</em>
        </h1>
        <p className="hero-lede">
          Eleven kernel packages. Three phases. One typed key. A design where the blueprint is a value,
          every settlement is a single write, and every module is checked by the direction of its edges.
        </p>
        <div className="hero-actions">
          <button className="btn btn-solid" type="button" onClick={() => onNavigate('lattice')}>
            Enter the lattice <span aria-hidden="true">↘</span>
          </button>
          <button className="btn btn-ghost" type="button" onClick={() => onNavigate('proof')}>
            Read the proof <span aria-hidden="true">↓</span>
          </button>
        </div>
      </div>

      <div className="hero-art">
        <div className="hero-art-label">
          <span>Fig. 00 — the object is the architecture</span>
          <span>Axonometric / NTS</span>
        </div>
        <svg className="cube-figure" viewBox="0 0 560 420" role="img" aria-label="Wireframe drawing of one blueprint passing through a world view to a match outcome">
          <g className="draw">
            <path className="wire-soft" d="M112 158 L280 76 L448 158 L280 240 Z" />
            <path className="wire" d="M112 158 L112 286 L280 368 L448 286 L448 158" />
            <path className="wire" d="M112 286 L280 368 L280 240" />
            <path className="wire" d="M448 286 L280 368" />
            <path className="wire-soft" d="M280 76 L280 240" />
            <path className="wire-soft" d="M112 158 L280 240 L448 158" />

            <path className="wire-soft" d="M208 184 L280 220 L352 184 L280 148 Z" />
            <path className="wire" d="M208 184 L208 248 L280 284 L352 248 L352 184" />
            <path className="wire" d="M208 248 L280 284 L280 220" />
            <path className="wire" d="M352 248 L280 284" />

            <path className="accent" d="M246 186 L280 204 L314 186 L280 168 Z" />
            <path className="accent" d="M246 186 L246 214 L280 232 L314 214 L314 186" />
            <path className="accent" d="M246 214 L280 232 L280 204" />

            <path className="wire-soft" d="M112 158 L112 144 M448 158 L448 144 M112 286 L112 300 M448 286 L448 300" />
            <path className="wire-soft" d="M280 76 L280 58 M280 240 L280 258 M280 284 L280 300" />

            <circle cx="112" cy="158" r="3" className="fill-ink" />
            <circle cx="448" cy="158" r="3" className="fill-ink" />
            <circle cx="280" cy="368" r="3" className="fill-ink" />
            <circle cx="280" cy="204" r="4" className="fill-accent" />
          </g>

          <g className="caption-strong">
            <text x="78" y="132">WORLD VIEW</text>
            <text x="304" y="116">BLUEPRINT</text>
            <text x="304" y="300">OUTCOME</text>
          </g>
          <g className="caption">
            <text x="78" y="145">one snapshot</text>
            <text x="304" y="129">same IR / one plan</text>
            <text x="304" y="313">pure value</text>
            <text x="18" y="380">LOCAL → WORLD</text>
            <text x="444" y="380" textAnchor="end">SETTLE ONCE</text>
          </g>

          <g className="caption">
            <path className="wire-soft" d="M280 204 L402 110 L452 110" />
            <path className="wire-soft" d="M280 232 L402 338 L452 338" />
            <text x="458" y="104">key × type</text>
            <text x="458" y="116">not a stage</text>
            <text x="458" y="332">rows × revision</text>
            <text x="458" y="344">not a plan</text>
          </g>

          <g className="caption">
            <text x="18" y="60">A / SECTION</text>
            <text x="18" y="72">immutable read face</text>
            <path className="wire-soft" d="M18 84 L18 96 L280 96" />
            <text x="290" y="100">recursive frame</text>
          </g>
        </svg>
        <div className="hero-art-label" style={{ borderBottom: 0, borderTop: '1px solid var(--line-strong)', paddingTop: 8, marginTop: 6 }}>
          <span>StructureCumulus kernel</span>
          <span>11 / 3 / 1</span>
        </div>
      </div>

      <div className="hero-stats">
        {GATES.map((gate) => (
          <div className="hero-stat" key={gate.label}>
            <span className="n">{gate.value}</span>
            <span className="l">{gate.label} · {gate.note}</span>
          </div>
        ))}
      </div>

      <div className="hero-foot">
        <span>Reviewed 2026.10.26 · commit 0d1218e</span>
        <span>English source only · adapter specified, not implemented</span>
      </div>
    </header>
  );
}

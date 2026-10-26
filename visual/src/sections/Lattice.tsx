import { Reveal, SectionHead } from '../components/ui';
import { LatticeGraph } from '../components/LatticeGraph';
import type { ModuleId } from '../data/site';
import '../styles/lattice.css';

export function Lattice({ focus }: { focus: ModuleId | null }) {
  return (
    <section className="section" id="lattice" data-section>
      <SectionHead
        no="01"
        kicker="Module graph"
        title={<>Eleven packages. <em>One direction.</em></>}
        note={<>A package is a rank, not a folder. A lower rank may never import a higher one; the edge table is checked by the same repository that checks the docs.</>}
      />
      <Reveal>
        <LatticeGraph focus={focus} />
      </Reveal>
    </section>
  );
}

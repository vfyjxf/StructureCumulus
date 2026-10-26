import { Reveal, SectionHead } from '../components/ui';
import { LatticeGraph } from '../components/LatticeGraph';
import type { ModuleId } from '../data/site';
import { useLanguage } from '../i18n/Language';
import '../styles/lattice.css';

export function Lattice({ focus }: { focus: ModuleId | null }) {
  const { zh } = useLanguage();
  return (
    <section className="section" id="lattice" data-section>
      <SectionHead
        no="01"
        kicker={zh ? '模块图谱' : 'Module graph'}
        title={zh ? <>十一个 package。<em>一个方向。</em></> : <>Eleven packages. <em>One direction.</em></>}
        note={zh ? <>package 是一个 rank，不是文件夹。低 rank 永远不能 import 高 rank；边表由检查文档的同一个仓库检查。</> : <>A package is a rank, not a folder. A lower rank may never import a higher one; the edge table is checked by the same repository that checks the docs.</>}
      />
      <Reveal>
        <LatticeGraph focus={focus} />
      </Reveal>
    </section>
  );
}

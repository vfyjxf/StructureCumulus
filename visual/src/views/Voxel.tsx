import { useState } from 'react';
import { Panel } from '../components/Panel';
import { Readout } from '../components/Readout';
import { WhyNote } from '../components/WhyNote';
import { Segmented, Toggle, NumberStepper } from '../components/Controls';
import { VoxelCanvas, type Camera } from '../components/VoxelCanvas';
import { useExplain } from '../components/ExplainDrawer';
import { useLang } from '../i18n/LangContext';
import { EXTRA } from '../i18n/extra';
import {
  BAY_META,
  VOXEL_COUNTS,
  VOXELS,
  VOXEL_BOUNDS,
  type BayId,
  type Voxel,
} from '../data/voxel';

const ALL_BAYS: BayId[] = ['wall', 'mouth', 'interior'];

export function VoxelView() {
  const { lang } = useLang();
  const copy = EXTRA[lang].voxel;
  const { open } = useExplain();
  const [camera, setCamera] = useState<Camera>({ az: 35, el: 0.62, zoom: 30 });
  const [layer, setLayer] = useState<number>(VOXEL_BOUNDS.sy - 1);
  const [bays, setBays] = useState<Set<BayId>>(new Set(ALL_BAYS));
  const [overlay, setOverlay] = useState<'kind' | 'bay' | 'claim'>('kind');
  const [selected, setSelected] = useState<Voxel | null>(null);
  const [cutaway, setCutaway] = useState(true);

  const toggleBay = (bay: BayId) => {
    setBays((current) => {
      const next = new Set(current);
      if (next.has(bay)) next.delete(bay);
      else next.add(bay);
      return next;
    });
  };

  const kindLabel = selected
    ? selected.kind === 'shell'
      ? copy.shell
      : selected.kind === 'air'
        ? copy.air
        : copy.door
    : copy.none;

  return (
    <div className="g12">
      <div className="c8">
        <Panel
          title={copy.panel}
          count={copy.sub}
          className="np-voxel-panel"
          actions={
            <button type="button" className="np-mini-btn" onClick={() => setCamera({ az: 35, el: 0.62, zoom: 30 })}>
              {copy.reset}
            </button>
          }
        >
          <div className="np-voxel-wrap">
            <VoxelCanvas
              voxels={VOXELS}
              camera={camera}
              onCamera={setCamera}
              layer={layer}
              bays={bays}
              overlay={overlay}
              selected={selected}
              onSelect={setSelected}
              cutaway={cutaway}
            />
          </div>
          <p className="np-voxel-hint">{copy.hint}</p>
        </Panel>
      </div>

      <div className="c4">
        <Panel title={copy.overlay}>
          <div className="np-voxel-controls">
            <Segmented
              label={copy.overlay}
              options={[
                { value: 'kind', label: copy.kind },
                { value: 'bay', label: copy.bay },
                { value: 'claim', label: copy.claim },
              ]}
              value={overlay}
              onChange={(value) => setOverlay(value as 'kind' | 'bay' | 'claim')}
            />
            <NumberStepper label={copy.layer} value={layer} min={0} max={VOXEL_BOUNDS.sy - 1} onChange={setLayer} />
            <Toggle pressed={cutaway} onChange={setCutaway}>
              {copy.cutaway}
            </Toggle>
            <div className="np-voxel-bays">
              {ALL_BAYS.map((bay) => (
                <Toggle key={bay} pressed={bays.has(bay)} onChange={() => toggleBay(bay)}>
                  {copy[bay]}
                </Toggle>
              ))}
            </div>
          </div>
        </Panel>

        <Panel title={copy.selected}>
          {selected ? (
            <>
              <div className="np-readout-grid">
                <Readout label="cell" value={`${selected.x}, ${selected.y}, ${selected.z}`} />
                <Readout label={copy.kind} value={kindLabel} />
                <Readout label={copy.bay} value={copy[selected.bay]} />
                <Readout label={copy.stage} value={BAY_META[selected.bay].stage} />
                <Readout label={copy.claimState} value={BAY_META[selected.bay].claim} />
                <Readout label={copy.section} value="0,0,0" />
              </div>
              <button type="button" className="np-mini-btn np-mini-btn--wide" onClick={() => open('claimscope')}>
                {copy.explain}
              </button>
            </>
          ) : (
            <p className="np-empty">{copy.none}</p>
          )}
        </Panel>

        <Panel title={copy.counts}>
          <div className="np-readout-grid">
            <Readout label="voxels" value={VOXEL_COUNTS.total} />
            <Readout label={copy.shell} value={VOXEL_COUNTS.shell} />
            <Readout label={copy.air} value={VOXEL_COUNTS.air} />
            <Readout label={copy.door} value={VOXEL_COUNTS.mouth} />
          </div>
        </Panel>
      </div>

      <div className="c12">
        <WhyNote>{copy.note}</WhyNote>
      </div>
    </div>
  );
}

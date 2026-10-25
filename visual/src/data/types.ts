export type StatusKind = 'ok' | 'warn' | 'fail' | 'info' | 'neutral' | 'unknown';

export type Stage = 'DESIGN' | 'PROBE' | 'FORM' | 'SETTLE' | 'STALE';

export type PageId =
  | 'overview'
  | 'mission'
  | 'space'
  | 'authoring'
  | 'anatomy'
  | 'voxel'
  | 'slice'
  | 'predicates'
  | 'context'
  | 'pipeline'
  | 'claims'
  | 'results'
  | 'glossary';

export interface PageMeta {
  id: PageId;
  no: string;
  title: string;
  subtitle: string;
  sheet: string;
  revision: string;
  scale: string;
  date: string;
  zone: string;
  stage: Stage;
  stamp: string;
  sources: string[];
}

export interface ReadoutSpec {
  label: string;
  value: string;
  unit?: string;
  state?: 'nominal' | 'active' | 'fail' | 'unknown' | 'ok';
  delta?: string;
  primary?: boolean;
}

/** One named playback scenario. Copy is English source text translated by COPY.zh. */
export interface DemoScenario<Id extends string = string> {
  id: Id;
  label: string;
  labelZh?: string;
  note: string;
  steps: number;
}

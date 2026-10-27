export type ModuleId =
  | 'core' | 'space' | 'finding' | 'world' | 'claim' | 'structure'
  | 'context' | 'document' | 'engine' | 'remedy' | 'spi';

export type Module = {
  id: ModuleId;
  rank: number;
  x: number;
  y: number;
  owns: string;
  imports: ModuleId[];
  law: string;
  doc: string;
};

export const MODULES: Module[] = [
  { id: 'core', rank: 0, x: 5.5, y: 50, owns: 'identity · phase · key · value · catalog', imports: [], law: 'No geometry. No report. No traversal.', doc: 'docs/modules/core.md' },
  { id: 'space', rank: 1, x: 15.5, y: 50, owns: 'lattice · cuboid set · orientation · siting', imports: ['core'], law: 'Geometry is a value; a siting maps local to world once.', doc: 'docs/modules/space.md' },
  { id: 'finding', rank: 2, x: 26.5, y: 27, owns: 'finding · bundle · code table · renderer', imports: ['core', 'space'], law: 'A finding is a value; a bundle is the only aggregator.', doc: 'docs/modules/finding.md' },
  { id: 'world', rank: 2, x: 26.5, y: 73, owns: 'occupant · trait · section · capture lease', imports: ['core', 'space'], law: 'A snapshot is immutable; the lease is only isCurrent().', doc: 'docs/modules/world.md' },
  { id: 'claim', rank: 3, x: 37.5, y: 50, owns: 'row · sharing · footprint · ledger port', imports: ['core', 'space', 'finding'], law: 'One row per cell × owner × siting; one conflict predicate.', doc: 'docs/modules/claim.md' },
  { id: 'structure', rank: 4, x: 48.5, y: 50, owns: 'bay · check · blueprint · focus · plan', imports: ['core', 'space', 'finding', 'world', 'claim'], law: 'A bay owns cells; a check is a focus × an expectation.', doc: 'docs/modules/structure.md' },
  { id: 'context', rank: 5, x: 59.5, y: 50, owns: 'store · key semantics · fold · deriver', imports: ['core', 'space', 'finding', 'world', 'structure'], law: 'One key, two retentions, one publication.', doc: 'docs/modules/context.md' },
  { id: 'document', rank: 6, x: 70.5, y: 50, owns: 'IR · compiler · reader/writer ports', imports: ['core', 'space', 'finding', 'claim', 'world', 'structure', 'context'], law: 'Data in; one blueprint or a finding bundle out.', doc: 'docs/modules/document.md' },
  { id: 'engine', rank: 7, x: 81.5, y: 50, owns: 'request · result · planner · pipeline bodies', imports: ['core', 'space', 'finding', 'world', 'claim', 'structure', 'context', 'document'], law: 'One attempt; no stage machine.', doc: 'docs/modules/engine.md' },
  { id: 'remedy', rank: 8, x: 91.5, y: 32, owns: 'repair · preview · edit plan · provider SPI', imports: ['core', 'space', 'finding', 'world', 'claim', 'structure', 'context', 'engine'], law: 'A remedy is a value, not an executor.', doc: 'docs/modules/remedy.md' },
  { id: 'spi', rank: 9, x: 91.5, y: 70, owns: 'catalogs · facade · host ports · built-ins', imports: ['core', 'space', 'finding', 'world', 'claim', 'structure', 'context', 'document', 'engine', 'remedy'], law: 'The only package that knows every package.', doc: 'docs/modules/spi.md' },
];

export const MODULE_BY_ID = Object.fromEntries(MODULES.map((module) => [module.id, module])) as Record<ModuleId, Module>;

export const NAV = [
  { id: 'thesis', no: '00', label: 'Thesis', sub: 'The shape of the argument' },
  { id: 'lattice', no: '01', label: 'Lattice', sub: 'Eleven packages, one direction' },
  { id: 'object', no: '02', label: 'Object', sub: 'Bay, check, blueprint, outcome' },
  { id: 'workbench', no: '03', label: 'Workbench', sub: 'The campfire, end to end' },
  { id: 'ledger', no: '04', label: 'Ledger', sub: 'Claims without a plan' },
  { id: 'context', no: '05', label: 'Fold', sub: 'Absence is a value' },
  { id: 'codes', no: '06', label: 'Codes', sub: 'The report layer' },
  { id: 'proof', no: '07', label: 'Proof', sub: 'Decisions, tests, regeneration' },
] as const;

export type SectionId = (typeof NAV)[number]['id'];

export const REMOVED = [
  { term: 'Stage', replacement: 'Phase', why: 'A fact carries when it became knowable; a request never chooses an execution stage.' },
  { term: 'ContextTier', replacement: 'Retention', why: 'MATCH or INSTANCE is the whole lifetime decision; there is no second visibility axis.' },
  { term: 'Requirement', replacement: 'Coverage', why: 'EVERY_CELL, ONE_SUBJECT and ANY_SUBJECT already say what a bay owes.' },
  { term: 'BayBuilder', replacement: 'Bay.of', why: 'A bay is a value with a CuboidSet; no two-step construction knot.' },
  { term: 'Segment', replacement: 'CuboidSet', why: 'A shell, a door and an L-room are the same algebra.' },
  { term: 'Overlap', replacement: 'Sharing', why: 'Overlap is checked from the cells and one sharing value, not declared pairwise.' },
  { term: 'NestedView', replacement: 'WorldView', why: 'A nested view is the same immutable recursive face with a unique frame.' },
  { term: 'ClaimPlan', replacement: 'ClaimRow', why: 'Rows are derived after the check; Admitted carries bindings and a revision, not a plan.' },
  { term: 'DecisionKind', replacement: 'Focus', why: 'A focus declares the cells it decides and whether it binds subjects.' },
  { term: 'ReadSet', replacement: 'ReadScope', why: 'One closure of cells, keys and windows; no invalidation index.' },
  { term: 'Deferred', replacement: 'Undecided', why: 'A retry is a new call, not a stored token.' },
  { term: 'Augment', replacement: 'ContextKey', why: 'One typed key; values are not patched into a separate augmentation layer.' },
];

export const DOCS = [
  { group: 'Design', file: 'docs/README.md', title: 'The design of record' },
  { group: 'Design', file: 'docs/design/00-requirements.md', title: 'Requirements' },
  { group: 'Design', file: 'docs/design/01-constitution.md', title: 'Constitution' },
  { group: 'Design', file: 'docs/design/02-model.md', title: 'Model' },
  { group: 'Design', file: 'docs/design/03-modules.md', title: 'Modules' },
  { group: 'Design', file: 'docs/design/04-api.md', title: 'API tour' },
  { group: 'Design', file: 'docs/design/05-decisions.md', title: 'Decision ledger' },
  { group: 'Design', file: 'docs/design/06-verification.md', title: 'Verification' },
  { group: 'Design', file: 'docs/design/07-regeneration.md', title: 'Regeneration' },
  { group: 'Modules', file: 'docs/modules/structure.md', title: 'structure' },
  { group: 'Modules', file: 'docs/modules/document.md', title: 'document' },
  { group: 'Modules', file: 'docs/modules/engine.md', title: 'engine' },
  { group: 'Modules', file: 'docs/modules/claim.md', title: 'claim' },
  { group: 'Modules', file: 'docs/modules/world.md', title: 'world' },
  { group: 'Modules', file: 'docs/modules/context.md', title: 'context' },
  { group: 'Modules', file: 'docs/modules/finding.md', title: 'finding' },
  { group: 'Modules', file: 'docs/modules/core.md', title: 'core' },
  { group: 'Modules', file: 'docs/modules/space.md', title: 'space' },
  { group: 'Modules', file: 'docs/modules/remedy.md', title: 'remedy' },
  { group: 'Modules', file: 'docs/modules/spi.md', title: 'spi' },
  { group: 'Modules', file: 'docs/modules/adapter.md', title: 'adapter (M1 + partial M2)' },
];

export const HERO_STATS = [
  { value: '97', label: 'tests', note: 'kernel JUnit 5 · adapter 18 · GameTests 3/3' },
  { value: '55', label: 'codes', note: '12 kernel families + MC' },
  { value: '0', label: 'purity leaks', note: 'Minecraft names in kernel' },
  { value: '23', label: 'decisions', note: 'rulings, not preferences' },
];

export const GATES = [
  { label: 'Compile', value: 'PASS', note: 'javac 21 · gradlew check green' },
  { label: 'Purity', value: 'PASS', note: '0 Minecraft names' },
  { label: 'Tests', value: '97/97', note: 'kernel JUnit 5 · 15 containers; adapter 18 · GameTests 3/3' },
  { label: 'Spec index', value: 'PASS', note: 'spec-check.py · 55 codes' },
  { label: 'No stubs', value: 'PASS', note: 'TODO · FIXME · UOE scan' },
  { label: 'Determinism', value: 'N/R', note: 'not run in this sandbox' },
];

export const PRINCIPLES = [
  { n: '01', title: 'One key', text: 'ContextKey<T> is the only identity for a context value. Two keys with the same id but different types are two keys.' },
  { n: '02', title: 'One publication', text: 'A successful settle publishes INSTANCE once. A derive publishes one batch once. Nothing else writes a context.' },
  { n: '03', title: 'One direction', text: 'A package may import only lower-ranked packages. The graph is checked; the table is the law.' },
  { n: '04', title: 'One predicate', text: 'ClaimMatrix.conflicts is the only conflict rule. A plan cannot encode a second one.' },
];

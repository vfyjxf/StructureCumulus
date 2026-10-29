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
  { id: 'world', rank: 2, x: 26.5, y: 73, owns: 'occupant · typed host read · trait · section · capture lease', imports: ['core', 'space'], law: 'A snapshot is immutable; the lease is only isCurrent().', doc: 'docs/modules/world.md' },
  { id: 'claim', rank: 3, x: 37.5, y: 50, owns: 'row · sharing · footprint · ledger port', imports: ['core', 'space', 'finding'], law: 'One row per cell × owner × siting; one conflict predicate.', doc: 'docs/modules/claim.md' },
  { id: 'structure', rank: 4, x: 48.5, y: 50, owns: 'bay · check · blueprint · focus · plan · envelope', imports: ['core', 'space', 'finding', 'world', 'claim'], law: 'A bay owns cells; a check is a focus × an expectation.', doc: 'docs/modules/structure.md' },
  { id: 'context', rank: 5, x: 59.5, y: 50, owns: 'store · key semantics · fold · deriver · cached context', imports: ['core', 'space', 'finding', 'world', 'structure'], law: 'One key, two retentions, one publication.', doc: 'docs/modules/context.md' },
  { id: 'document', rank: 6, x: 70.5, y: 50, owns: 'IR · compiler · reader/writer ports · structure set', imports: ['core', 'space', 'finding', 'claim', 'world', 'structure', 'context'], law: 'Data in; one blueprint or a finding bundle out.', doc: 'docs/modules/document.md' },
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
  { term: 'HostData table', replacement: 'HostType + WorldView.get', why: 'One immutable snapshot, many typed reads; no per-cell host map and no union snapshot.' },
  { term: 'Descriptor projection', replacement: 'Typed snapshot read', why: 'The descriptor is (type, kind). Property, state, tag and fluid checks read the captured value in its own frame.' },
  { term: 'readBlock / readEntity', replacement: 'TraitProvider<T, H>', why: 'A provider names its holder once; a missing provider is absence, never a hidden default.' },
  { term: 'blockEntityNbt / entityNbt', replacement: 'typed provider', why: 'A trait is the owning API value; serialization is not an API and no item holder exists.' },
  { term: 'Structure-set transaction', replacement: 'StructureSetHost', why: 'Root, satellites and links are host orchestration; the kernel still checks and settles one blueprint.' },
  { term: 'Per-attempt set budget', replacement: 'One shared set budget', why: 'Presence and every member attempt draw from the same ceiling; the set is the unit the host admitted.' },
  { term: 'Kernel context rollback', replacement: 'ContextStore.remove', why: 'A set rollback releases claims and removes each formed StructureContext through the store; the kernel still has no transaction.' },
  { term: 'CapabilityLookup', replacement: 'NestedExtentLookup', why: 'One per-holder registry replaces the aggregate block/entity lookup; a new subject kind adds a holder and a provider, not an interface method.' },
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
  { group: 'Design', file: 'docs/design/08-context-cache.md', title: 'Context cache' },
  { group: 'Design', file: 'docs/design/09-structure-sets.md', title: 'Structure sets' },
  { group: 'Design', file: 'docs/design/10-host-data.md', title: 'Host snapshot' },
  { group: 'Design', file: 'docs/design/11-trait-providers.md', title: 'Trait providers' },
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
  { group: 'Modules', file: 'docs/modules/adapter.md', title: 'adapter' },
];

export const HERO_STATS = [
  { value: '261', label: 'tests', note: 'kernel JUnit 5 · adapter 296 · GameTests 71/71' },
  { value: '65', label: 'codes', note: '13 kernel families · one Codes table' },
  { value: '0', label: 'purity leaks', note: 'Minecraft names in kernel' },
  { value: '36', label: 'decisions', note: 'rulings, not preferences' },
];

export const LAYERS = [
  { id: 'D-30', title: 'Derived envelope', text: 'bounds minus declared cells becomes one reserved bay and check; a foreign occupant is SC-EXP-0006, an empty set is a no-op.' },
  { id: 'D-31', title: 'Cached context', text: 'MatchRequest.cachedContext is a host-owned (id, type) cache for declared instance keys; the current attempt always wins.' },
  { id: 'D-32', title: 'Structure sets', text: 'root + satellites + links + presence, sequenced by StructureSetHost; S5 ships /cumulus set check|settle over one unsited union capture (no orientation audit, so no spurious SC-MC-0004), and the kernel still has no set type.' },
  { id: 'D-33', title: 'Typed host snapshot', text: 'one snapshot per frame, HostType reads and Expectations.host; OccupantDescriptor is (type, kind) and D-29 is superseded.' },
  { id: 'D-34', title: 'Trait providers', text: 'TraitProvider<T, H>/TraitHolder per holder and one CapabilityTraitProvider class; capability traits return IItemHandler, IEnergyStorage and IFluidHandler. No NBT traits, no item holder.' },
  { id: 'D-35', title: 'Set close-out', text: 'SC-SET-0008 rejects a satellite on the root siting (compiler, StructureSet.place, host check); presence and every attempt share one set budget; rollback removes formed contexts through ContextStore.remove. The real-level StructureSetHostGameTest covers the same-siting rejection and a one-cell shared budget.' },
  { id: 'D-36', title: 'Nested-extent lookup', text: 'NestedExtentHolder/Provider/Lookup replace the aggregate nested lookup: NestedExtentHolders.block/entity, one CapabilityNestedExtentProvider over NestedExtentAccesses, and the standard NestedExtentProviders. The old mc.NestedExtentLookup and CapabilityLookup are deleted.' },
];

export const GATES = [
  { label: 'Compile', value: 'PASS', note: 'gradlew check green at f84dba8' },
  { label: 'Purity', value: 'PASS', note: '0 Minecraft names' },
  { label: 'Tests', value: '261/261', note: 'kernel JUnit 5 · 39 containers; adapter 296 · GameTests 71/71' },
  { label: 'Spec index', value: 'PASS', note: 'spec-check.py · 65 codes' },
  { label: 'No stubs', value: 'PASS', note: 'TODO · FIXME · UOE scan' },
  { label: 'Determinism', value: 'PASS', note: 'DeterminismTest · canonical bytes, fixed point, compile runs' },
];

export const PRINCIPLES = [
  { n: '01', title: 'One key', text: 'ContextKey<T> is the only identity for a context value. Two keys with the same id but different types are two keys.' },
  { n: '02', title: 'One publication', text: 'A successful settle publishes INSTANCE once. A derive publishes one batch once. A set rollback removes formed contexts through ContextStore.remove; nothing else writes or removes one.' },
  { n: '03', title: 'One direction', text: 'A package may import only lower-ranked packages. The graph is checked; the table is the law.' },
  { n: '04', title: 'One predicate', text: 'ClaimMatrix.conflicts is the only conflict rule. A plan cannot encode a second one.' },
  { n: '05', title: 'One host read', text: 'A host value is one typed read from one immutable snapshot. The descriptor stays (type, kind); the kernel never names or models the value.' },
];

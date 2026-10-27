export type TestClass = {
  file: string;
  package: string;
  className: string;
  count: number;
};

export const testClasses: TestClass[] = [
  {"file": "dev/vfyjxf/cumulus/kernel/claim/ClaimTest.java", "package": "claim", "className": "ClaimTest", "count": 5},
  {"file": "dev/vfyjxf/cumulus/kernel/context/ContextTest.java", "package": "context", "className": "ContextTest", "count": 3},
  {"file": "dev/vfyjxf/cumulus/kernel/core/CoreTest.java", "package": "core", "className": "CoreTest", "count": 9},
  {"file": "dev/vfyjxf/cumulus/kernel/document/DocumentCompilerTest.java", "package": "document", "className": "DocumentCompilerTest", "count": 5},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/FacadeTest.java", "package": "engine", "className": "FacadeTest", "count": 3},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ReadModelTest.java", "package": "engine", "className": "ReadModelTest", "count": 10},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ScenarioTest.java", "package": "engine", "className": "ScenarioTest", "count": 15},
  {"file": "dev/vfyjxf/cumulus/kernel/finding/CodesTest.java", "package": "finding", "className": "CodesTest", "count": 3},
  {"file": "dev/vfyjxf/cumulus/kernel/finding/FindingBundleTest.java", "package": "finding", "className": "FindingBundleTest", "count": 13},
  {"file": "dev/vfyjxf/cumulus/kernel/finding/FindingRendererTest.java", "package": "finding", "className": "FindingRendererTest", "count": 5},
  {"file": "dev/vfyjxf/cumulus/kernel/finding/FindingTest.java", "package": "finding", "className": "FindingTest", "count": 9},
  {"file": "dev/vfyjxf/cumulus/kernel/finding/SourceTextTest.java", "package": "finding", "className": "SourceTextTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/space/SpaceTest.java", "package": "space", "className": "SpaceTest", "count": 14},
  {"file": "dev/vfyjxf/cumulus/kernel/structure/AnchorsTest.java", "package": "structure", "className": "AnchorsTest", "count": 1},
  {"file": "dev/vfyjxf/cumulus/kernel/structure/StructureValidationTest.java", "package": "structure", "className": "StructureValidationTest", "count": 7},
  {"file": "dev/vfyjxf/cumulus/kernel/world/CaptureReportTest.java", "package": "world", "className": "CaptureReportTest", "count": 9},
  {"file": "dev/vfyjxf/cumulus/kernel/world/WorldTest.java", "package": "world", "className": "WorldTest", "count": 10},
];

export const TEST_GROUPS = [
  {"name": "claim", "count": 5},
  {"name": "context", "count": 3},
  {"name": "core", "count": 9},
  {"name": "document", "count": 5},
  {"name": "engine", "count": 28},
  {"name": "finding", "count": 34},
  {"name": "space", "count": 14},
  {"name": "structure", "count": 8},
  {"name": "world", "count": 19},
];

export const TEST_COUNT = 125;

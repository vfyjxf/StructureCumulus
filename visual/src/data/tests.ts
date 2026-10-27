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
  {"file": "dev/vfyjxf/cumulus/kernel/engine/Ae2CpuScenarioTest.java", "package": "engine", "className": "Ae2CpuScenarioTest", "count": 5},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/AnchorValueScenarioTest.java", "package": "engine", "className": "AnchorValueScenarioTest", "count": 3},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/AstralAltarScenarioTest.java", "package": "engine", "className": "AstralAltarScenarioTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/BloodAltarScenarioTest.java", "package": "engine", "className": "BloodAltarScenarioTest", "count": 5},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ClaimStressScenarioTest.java", "package": "engine", "className": "ClaimStressScenarioTest", "count": 5},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ContextAggregateScenarioTest.java", "package": "engine", "className": "ContextAggregateScenarioTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ContextInstanceScenarioTest.java", "package": "engine", "className": "ContextInstanceScenarioTest", "count": 1},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/DoorCoverageScenarioTest.java", "package": "engine", "className": "DoorCoverageScenarioTest", "count": 2},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/EntityWindowScenarioTest.java", "package": "engine", "className": "EntityWindowScenarioTest", "count": 2},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/FacadeTest.java", "package": "engine", "className": "FacadeTest", "count": 3},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/FusionVoidScenarioTest.java", "package": "engine", "className": "FusionVoidScenarioTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/GateBudgetScenarioTest.java", "package": "engine", "className": "GateBudgetScenarioTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/GtEbfScenarioTest.java", "package": "engine", "className": "GtEbfScenarioTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/NestedCoverageScenarioTest.java", "package": "engine", "className": "NestedCoverageScenarioTest", "count": 4},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/PncRefineryScenarioTest.java", "package": "engine", "className": "PncRefineryScenarioTest", "count": 7},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ReadModelTest.java", "package": "engine", "className": "ReadModelTest", "count": 10},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/ScenarioTest.java", "package": "engine", "className": "ScenarioTest", "count": 15},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/SeamScenarioTest.java", "package": "engine", "className": "SeamScenarioTest", "count": 2},
  {"file": "dev/vfyjxf/cumulus/kernel/engine/TerraPlateScenarioTest.java", "package": "engine", "className": "TerraPlateScenarioTest", "count": 5},
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
  {"name": "engine", "count": 89},
  {"name": "finding", "count": 34},
  {"name": "space", "count": 14},
  {"name": "structure", "count": 8},
  {"name": "world", "count": 19},
];

export const TEST_COUNT = 186;

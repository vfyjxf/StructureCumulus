# build-logic

A copy of CloudLib's `cloudlang-plugin` Gradle build, which supplies the three plugins this
project applies:

- `dev.vfyjxf.cloudlib.style` — Spotless: the shared Eclipse JDT profile, the three-rank import
  order, the lowerCamelCase field lint, the lowerCamelCase enum-constant lint
  (`lowerCamelEnumConstants`) and the fully-qualified-name lint;
- `dev.vfyjxf.cloudlib.pmd` — the PMD 7 gate (`pmdMain`), the advisory pass (`pmdAdvisory`) and
  the `verifyPackageInfo` walk, all driven by the `cloudpmd` rulesets in `cloudpmd/`;
- `dev.vfyjxf.cloudlib.lang` — the distributed yaml lang sources and the generated key class.

Upstream: `/Users/vfyjxf/ProjectDir/CloudMods-Inworld/CloudLib-InworldUI/cloudlang-plugin`.

The style plugin has one local addition: `EnumConstantNamingLint` as a Spotless step of its own
(`lowerCamelEnumConstants`), so enum constants are checked as the comma-separated list they are
rather than one line at a time. `SourceMask` is the comment/literal masker both lints share. These
three files and their tests are the only deviations; everything else is a byte-for-byte upstream
copy, so a re-sync is:

```
rsync -a --exclude build/ --exclude .gradle/ \
    /Users/vfyjxf/ProjectDir/CloudMods-Inworld/CloudLib-InworldUI/cloudlang-plugin/ build-logic/
```

then reapply the enum lint (`ConstantNamingLint`, `EnumConstantNamingLint`, `SourceMask`,
`CloudStylePlugin` and the two lint tests under `src/test`).

The plugin ids and the `dev.vfyjxf.cloudlib.*` packages stay as upstream on purpose: renaming them
would turn every re-sync into a manual merge, and `dev.vfyjxf` is the family's group either way.

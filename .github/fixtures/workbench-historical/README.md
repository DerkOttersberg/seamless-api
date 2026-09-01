# Historical Workbench fixture

This fixture represents the persisted contract from Workbench 2.0.0: an eight-slot `Items`
list plus `Progress` and `MaxProgress`, with none of the 2.1 pending-operation or screen-state
fields. The packaged-suite harness loads the SNBT into a real Workbench block entity while the
game is frozen, saves the world, stops the server, and verifies the same exact fields after a
fresh process reload on Fabric, Forge, and NeoForge.

`seamlessdeconstructor.json` is the historical config filename. The harness also verifies that
the original and its `.bak` remain byte-identical while the canonical
`seamless-deconstructing-workbench.json` is created with the same validated values.

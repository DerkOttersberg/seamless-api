param(
    [Parameter(Mandatory)][ValidateSet('fabric','forge','neoforge')][string]$Loader,
    [Parameter(Mandatory)][string]$Mod,
    [Parameter(Mandatory)][string]$RunDirectory
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
& "$repoRoot/gradlew.bat" --no-daemon -p "$repoRoot/.github/client-smoke" runClient "-PclientLoader=$Loader" "-PstandaloneMod=$Mod" "-PclientRunDir=$RunDirectory" '-PgraphicsBackend=opengl'
exit $LASTEXITCODE

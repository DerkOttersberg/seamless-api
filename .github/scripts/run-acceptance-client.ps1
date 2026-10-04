param(
    [Parameter(Mandatory)][ValidateSet('fabric','forge','neoforge')][string]$Loader,
    [Parameter(Mandatory)][ValidateSet('QA_A','QA_B')][string]$Role,
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][int]$Port,
    [ValidateSet('opengl','vulkan')][string]$Backend = 'opengl'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
& "$repoRoot/gradlew.bat" --no-daemon -p "$repoRoot/.github/client-smoke" runClient "-PclientLoader=$Loader" "-PqaUsername=$Role" "-PqaServer=127.0.0.1:$Port" "-PclientRunDir=$RunDirectory" "-PgraphicsBackend=$Backend"
exit $LASTEXITCODE

param(
    [Parameter(Mandatory)][string]$FabricServerTemplateDirectory,
    [Parameter(Mandatory)][string]$RuntimeJarsRoot,
    [Parameter(Mandatory)][string]$RunDirectory,
    [int]$SeedPort = 25620,
    [int]$SeedRconPort = 25621
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$template = (Resolve-Path -LiteralPath $FabricServerTemplateDirectory).Path
$jars = (Resolve-Path -LiteralPath $RuntimeJarsRoot).Path
$runRoot = [IO.Path]::GetFullPath($RunDirectory)
if ((Test-Path -LiteralPath $runRoot) -and (Get-ChildItem -LiteralPath $runRoot -Force | Select-Object -First 1)) { throw 'Use a new, empty QA directory.' }
if ($SeedPort -lt 1024 -or $SeedPort -gt 65535 -or $SeedRconPort -lt 1024 -or $SeedRconPort -gt 65535 -or $SeedPort -eq $SeedRconPort) { throw 'Invalid QA ports.' }
foreach ($port in @($SeedPort,$SeedRconPort)) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
    try { $listener.Start() } finally { $listener.Stop() }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
$javaVersion = (& $java --version | Out-String)
if ($javaVersion -notmatch '(?m)^(openjdk|java) 25(?:[. ]|$)') { throw 'Standalone acceptance requires Java 25.' }
$lock = Get-Content -LiteralPath "$repoRoot/suite-lock.json" -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
function Stop-OwnedTree($RootProcess) {
    $snapshot = @(Get-CimInstance Win32_Process)
    $owned = [Collections.Generic.HashSet[int]]::new()
    [void]$owned.Add($RootProcess.Id)
    do {
        $added = $false
        foreach ($process in $snapshot) {
            if ($owned.Contains([int]$process.ParentProcessId) -and $owned.Add([int]$process.ProcessId)) { $added = $true }
        }
    } while ($added)
    foreach ($processId in $owned) { Stop-Process -Id $processId -ErrorAction SilentlyContinue }
}
# Generate a vanilla-only seed, not a copied all-mod world with missing registries.
$seed = Join-Path $runRoot 'vanilla-seed'
New-Item -ItemType Directory -Path $seed -Force | Out-Null
foreach ($entry in @('libraries','versions','fabric-server-launch.jar','fabric-server-launcher.properties','server.jar')) {
    if ($entry -in @('versions','fabric-server-launcher.properties') -and !(Test-Path -LiteralPath (Join-Path $template $entry))) { continue }
    Copy-Item -LiteralPath (Join-Path $template $entry) -Destination (Join-Path $seed $entry) -Recurse
}
@("server-ip=127.0.0.1","server-port=$SeedPort","enable-rcon=true","rcon.port=$SeedRconPort","rcon.password=release-hardening-local-only","online-mode=false","enforce-secure-profile=false","level-name=qa-world","level-type=minecraft:flat",'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"features":false,"lakes":false}',"pause-when-empty-seconds=0") | Set-Content -LiteralPath "$seed/server.properties" -Encoding ASCII
'eula=true' | Set-Content -LiteralPath "$seed/eula.txt" -Encoding ASCII
$seedProcess = Start-Process $java -ArgumentList @('-Xmx1G','-jar','fabric-server-launch.jar','nogui') -WorkingDirectory $seed -WindowStyle Hidden -RedirectStandardOutput "$seed/console.log" -RedirectStandardError "$seed/stderr.log" -PassThru
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([string](Get-Content -LiteralPath "$seed/console.log" -Raw) -notmatch "RCON running on 127.0.0.1:$SeedRconPort") {
        if ($seedProcess.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw 'Vanilla seed generation failed.' }
        Start-Sleep -Seconds 2
    }
    & python -B "$PSScriptRoot/rcon-command.py" 127.0.0.1 $SeedRconPort release-hardening-local-only 1000 'save-all flush'
    if ($LASTEXITCODE -ne 0) { throw 'Vanilla seed save did not complete.' }
    & python -B "$PSScriptRoot/rcon-command.py" 127.0.0.1 $SeedRconPort release-hardening-local-only 1002 stop
    if (!$seedProcess.WaitForExit(30000)) { throw 'Vanilla seed did not stop cleanly.' }
} finally { if (!$seedProcess.HasExited) { Stop-OwnedTree $seedProcess } }

foreach ($loader in @('fabric','forge','neoforge')) {
    & "$repoRoot/gradlew.bat" --no-daemon -p "$repoRoot/.github/standalone-acceptance" clean jar "-PclientLoader=$loader" *> "$runRoot/helper-$loader.log"
    if ($LASTEXITCODE -ne 0) { throw "Standalone helper build failed: $loader" }
    $helper = "$repoRoot/.github/standalone-acceptance/build/libs/qa-standalone-acceptance-$loader.jar"
    foreach ($entry in $lock.repositories) {
        $mod = $entry.directory
        $profile = Join-Path $runRoot "$loader/$mod"
        New-Item -ItemType Directory -Path "$profile/mods","$profile/saves" -Force | Out-Null
        Copy-Item -LiteralPath "$seed/qa-world" -Destination "$profile/saves/suite-world" -Recurse
        Copy-Item -LiteralPath "$repoRoot/.github/client-smoke/options.txt" -Destination "$profile/options.txt"
        Copy-Item -LiteralPath "$jars/$loader/$($entry.artifactBase)-$($entry.artifactVersion)-$loader.jar" -Destination "$profile/mods/"
        if ($mod -in @('pretty-meteors-with-trails','seamless-deconstructing-workbench','sword-throw')) {
            $api = $lock.repositories | Where-Object directory -EQ 'seamless-api'
            Copy-Item -LiteralPath "$jars/$loader/$($api.artifactBase)-$($api.artifactVersion)-$loader.jar" -Destination "$profile/mods/"
        }
        if ($loader -eq 'fabric' -and $mod -ne 'seamless-api') {
            Copy-Item -LiteralPath "$template/mods/fabric-api-$($lock.loaders.fabricApi).jar" -Destination "$profile/mods/"
        }
        Copy-Item -LiteralPath $helper -Destination "$profile/mods/"
        $script = Join-Path $PSScriptRoot 'run-standalone-client.ps1'
        $client = Start-Process powershell.exe -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$script`"",'-Loader',$loader,'-Mod',$mod,'-RunDirectory',"`"$profile`"") -WindowStyle Hidden -RedirectStandardOutput "$profile/console.log" -RedirectStandardError "$profile/stderr.log" -PassThru
        try {
            $deadline = [DateTime]::UtcNow.AddSeconds(180)
            while (!(Test-Path -LiteralPath "$profile/standalone-passed.txt")) {
                if (Test-Path -LiteralPath "$profile/standalone-failed.txt") { throw (Get-Content -LiteralPath "$profile/standalone-failed.txt" -Raw) }
                if ($client.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw "Standalone client failed/timed out: $loader/$mod" }
                Start-Sleep -Seconds 2
            }
            if (!$client.WaitForExit(30000)) { throw "Standalone client did not shut down: $loader/$mod" }
            if ($client.ExitCode -ne 0) { throw "Standalone client exited unsuccessfully: $loader/$mod" }
            Write-Host "PASS standalone $loader/$mod"
        } finally { if (!$client.HasExited) { Stop-OwnedTree $client } }
    }
}
'PASS all 15 isolated integrated-world bootstrap/dependency profiles.' | Set-Content -LiteralPath "$runRoot/PASS.txt"

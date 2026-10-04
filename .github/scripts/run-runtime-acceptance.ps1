param(
    [Parameter(Mandatory)][ValidateSet('fabric','forge','neoforge')][string]$Loader,
    [Parameter(Mandatory)][string]$ServerTemplateDirectory,
    [Parameter(Mandatory)][string]$RuntimeJarsDirectory,
    [Parameter(Mandatory)][string]$RunDirectory,
    [Parameter(Mandatory)][int]$Port,
    [Parameter(Mandatory)][int]$RconPort,
    [ValidateSet('opengl','vulkan')][string]$Backend = 'opengl'
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$template = (Resolve-Path -LiteralPath $ServerTemplateDirectory).Path
$jars = (Resolve-Path -LiteralPath $RuntimeJarsDirectory).Path
$runRoot = [IO.Path]::GetFullPath($RunDirectory)
if ((Test-Path -LiteralPath $runRoot) -and (Get-ChildItem -LiteralPath $runRoot -Force | Select-Object -First 1)) { throw 'Use a new, empty QA directory; original worlds/profiles must not be used.' }
if ($Port -lt 1024 -or $Port -gt 65535 -or $RconPort -lt 1024 -or $RconPort -gt 65535 -or $Port -eq $RconPort) { throw 'Invalid or identical QA ports.' }
foreach ($testPort in @($Port,$RconPort)) {
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$testPort)
    try { $probe.Start() } finally { $probe.Stop() }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
$javaVersion = (& $java --version | Out-String)
if ($javaVersion -notmatch '(?m)^(openjdk|java) 25(?:[. ]|$)') { throw 'Runtime acceptance requires Java 25.' }
$lock = Get-Content -LiteralPath "$repoRoot/suite-lock.json" -Raw | ConvertFrom-Json
$runtimeJars = @(Get-ChildItem -LiteralPath $jars -File -Filter "*-$Loader.jar" | Where-Object Name -NotLike '*-sources.jar')
if ($runtimeJars.Count -ne 5) { throw 'Expected exactly five production runtime jars.' }
foreach ($entry in $lock.repositories) {
    if (!(Test-Path -LiteralPath (Join-Path $jars "$($entry.artifactBase)-$($entry.artifactVersion)-$Loader.jar"))) { throw "Missing locked artifact $($entry.artifactBase)" }
}
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
& "$repoRoot/gradlew.bat" --no-daemon -p "$repoRoot/.github/runtime-acceptance" clean jar "-PclientLoader=$Loader" "-PruntimeJarsDir=$jars" *> "$runRoot/helper-build.log"
if ($LASTEXITCODE -ne 0) { throw 'Acceptance helper did not build.' }
$helper = "$repoRoot/.github/runtime-acceptance/build/libs/qa-runtime-acceptance-$Loader.jar"
$serverRoot = Join-Path $runRoot 'server'
New-Item -ItemType Directory -Path "$serverRoot/mods" -Force | Out-Null
$entries = if ($Loader -eq 'fabric') { @('libraries','versions','fabric-server-launch.jar','fabric-server-launcher.properties','server.jar') } elseif ($Loader -eq 'forge') { @('libraries',"forge-$($lock.loaders.forge)-shim.jar") } else { @('libraries') }
foreach ($entry in $entries) { Copy-Item -LiteralPath (Join-Path $template $entry) -Destination (Join-Path $serverRoot $entry) -Recurse }
foreach ($jar in $runtimeJars) { Copy-Item -LiteralPath $jar.FullName -Destination "$serverRoot/mods/" }
Copy-Item -LiteralPath $helper -Destination "$serverRoot/mods/"
if ($Loader -eq 'fabric') {
    $fabricApi = "$serverRoot/mods/fabric-api-$($lock.loaders.fabricApi).jar"
    $cachedApi = Join-Path $template "mods/fabric-api-$($lock.loaders.fabricApi).jar"
    if (Test-Path -LiteralPath $cachedApi) { Copy-Item -LiteralPath $cachedApi -Destination $fabricApi }
    else { Invoke-WebRequest "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$($lock.loaders.fabricApi)/fabric-api-$($lock.loaders.fabricApi).jar" -OutFile $fabricApi }
}
@("allow-flight=true","enable-rcon=true","rcon.password=release-hardening-local-only","rcon.port=$RconPort","server-ip=127.0.0.1","server-port=$Port","online-mode=false","enforce-secure-profile=false","spawn-protection=0","level-name=qa-world","level-type=minecraft:flat","view-distance=8","simulation-distance=5","max-tick-time=120000","pause-when-empty-seconds=0") | Set-Content -LiteralPath "$serverRoot/server.properties" -Encoding ASCII
'eula=true' | Set-Content -LiteralPath "$serverRoot/eula.txt" -Encoding ASCII
$serverArguments = @('-Xms512M','-Xmx2G','-Dqa.runtime.acceptance=true')
if ($Loader -eq 'fabric') { $serverArguments += @('-jar','fabric-server-launch.jar','nogui') }
else {
    $loaderVersion = if ($Loader -eq 'forge') { $lock.loaders.forge } else { $lock.loaders.neoforge }
    $groupPath = if ($Loader -eq 'forge') { 'net/minecraftforge/forge' } else { 'net/neoforged/neoforge' }
    $argumentFile = "libraries/$groupPath/$loaderVersion/win_args.txt"
    if (!(Test-Path -LiteralPath "$serverRoot/$argumentFile")) { throw 'Installed server template has no Windows launch arguments.' }
    $serverArguments += @("@$argumentFile", 'nogui')
}
$ownedProcesses = @()
function Wait-For([scriptblock]$Condition,[int]$Seconds,[string]$Description) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (& $Condition) { return }
        $failed = @(Get-ChildItem -LiteralPath $runRoot -Recurse -Filter '*-failed.txt')
        if ($failed.Count) { throw "$Description failed: $(Get-Content -LiteralPath $failed[0].FullName -Raw)" }
        Start-Sleep -Seconds 2
    }
    throw "Timed out: $Description"
}
try {
    $server = Start-Process $java -ArgumentList $serverArguments -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput "$serverRoot/console.log" -RedirectStandardError "$serverRoot/stderr.log" -PassThru
    $ownedProcesses += $server
    Wait-For { (Get-Content -LiteralPath "$serverRoot/console.log" -Raw) -match 'Done \([0-9.]+s\)!' } 120 'dedicated-server startup'
    foreach ($role in @('QA_A','QA_B')) {
        $clientRoot = Join-Path $runRoot $role
        New-Item -ItemType Directory -Path "$clientRoot/mods" -Force | Out-Null
        Copy-Item "$serverRoot/mods/*.jar" -Destination "$clientRoot/mods/"
        Copy-Item -LiteralPath "$repoRoot/.github/client-smoke/options.txt" -Destination "$clientRoot/options.txt"
        if ($Loader -eq 'neoforge' -and $Backend -eq 'vulkan') {
            # NeoForge's OpenGL early window cannot be reused as a Vulkan surface.
            # Upstream: https://github.com/neoforged/NeoForge/issues/3230
            # This directory is freshly created QA data, never a user's config.
            New-Item -ItemType Directory -Path "$clientRoot/config" -Force | Out-Null
            'earlyWindowControl = false' | Set-Content -LiteralPath "$clientRoot/config/fml.toml" -Encoding ASCII
        }
        $clientScript = Join-Path $PSScriptRoot 'run-acceptance-client.ps1'
        $client = Start-Process powershell.exe -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$clientScript`"",'-Loader',$Loader,'-Role',$role,'-RunDirectory',"`"$clientRoot`"",'-Port',$Port,'-Backend',$Backend) -WindowStyle Hidden -RedirectStandardOutput "$clientRoot/console.log" -RedirectStandardError "$clientRoot/stderr.log" -PassThru
        $ownedProcesses += $client
        Wait-For { (Get-Content -LiteralPath "$serverRoot/console.log" -Raw) -match "$role joined the game" } 120 "$role connection"
        Write-Host "$Loader/${Backend}: $role connected"
    }
    Wait-For { (Test-Path -LiteralPath "$serverRoot/runtime-server-passed.txt") -and (Test-Path -LiteralPath "$runRoot/QA_A/runtime-client-passed.txt") -and (Test-Path -LiteralPath "$runRoot/QA_B/runtime-client-passed.txt") } 300 'paired-client scenarios'
    foreach ($role in @('QA_A','QA_B')) {
        $clientLog = Get-Content -LiteralPath "$runRoot/$role/logs/latest.log" -Raw
        $backendPattern = if ($Backend -eq 'vulkan') { 'Using graphics backend Vulkan' } else { 'Using graphics backend OpenGL' }
        if ($clientLog -notmatch $backendPattern) { throw "$role did not use the requested $Backend renderer." }
    }
    'PASS production jars; actual packets and rendered models; item conservation; disconnect/reconnect; charged dimension/respawn transitions.' | Set-Content -LiteralPath "$runRoot/PASS.txt"
    Write-Host "PASS $Loader/$Backend ($runRoot)"
} finally {
    if ($server -and !$server.HasExited) {
        try { & python -B "$PSScriptRoot/rcon-command.py" 127.0.0.1 $RconPort release-hardening-local-only 1000 stop }
        catch { Write-Warning 'RCON cleanup failed; stopping only the owned test process tree.' }
    }
    # Stop only descendants of launchers created by this invocation, never a user's client.
    $snapshot = @(Get-CimInstance Win32_Process)
    $ownedIds = [Collections.Generic.HashSet[int]]::new()
    foreach ($process in $ownedProcesses) { [void]$ownedIds.Add($process.Id) }
    do {
        $added = $false
        foreach ($process in $snapshot) { if ($ownedIds.Contains([int]$process.ParentProcessId) -and $ownedIds.Add([int]$process.ProcessId)) { $added = $true } }
    } while ($added)
    Start-Sleep -Seconds 5
    foreach ($processId in $ownedIds) { Stop-Process -Id $processId -ErrorAction SilentlyContinue }
}

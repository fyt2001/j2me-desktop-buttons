$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskGame = $args[0]
if ([string]::IsNullOrWhiteSpace($taskGame)) { $taskGame = Join-Path $taskRoot 'game.jar' }
$taskGame = [IO.Path]::GetFullPath($taskGame)
if (-not (Test-Path -LiteralPath $taskGame -PathType Leaf)) { throw "Game JAR not found: $taskGame" }
$taskJava = (Get-Command java.exe -ErrorAction Stop).Source
$taskJavac = (Get-Command javac.exe -ErrorAction Stop).Source
$taskJavaw = Join-Path (Split-Path -Parent $taskJava) 'javaw.exe'
$taskLibNames = @('microemulator-2.0.4.jar','microemu-nokiaui-2.0.4.jar','microemu-jsr-120-2.0.4.jar')
$taskLibPaths = foreach ($taskName in $taskLibNames) {
    $taskPath = Join-Path (Join-Path $taskRoot 'lib') $taskName
    if (-not (Test-Path -LiteralPath $taskPath -PathType Leaf)) { throw "Required library not found: $taskPath" }
    $taskPath
}
$taskLibClasspath = $taskLibPaths -join ';'
$taskClasses = Join-Path $taskRoot 'build'
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
& $taskJavac -encoding UTF-8 -cp $taskLibClasspath -d $taskClasses (Join-Path $taskRoot 'desktop\DesktopGame.java')
if ($LASTEXITCODE -ne 0) { throw 'Desktop window compilation failed.' }
$taskClasspath = "$taskClasses;$taskLibClasspath"
$taskStart = New-Object System.Diagnostics.ProcessStartInfo
$taskStart.FileName = $taskJavaw
$taskStart.Arguments = '-cp "' + $taskClasspath + '" DesktopGame "' + $taskRoot + '" "' + $taskGame + '"'
$taskStart.WorkingDirectory = $taskRoot
$taskStart.UseShellExecute = $false
$taskStart.CreateNoWindow = $true
$taskProcess = [System.Diagnostics.Process]::Start($taskStart)
if ($null -eq $taskProcess) { throw 'Desktop window failed to launch.' }

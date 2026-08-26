param([string]$RepositoryRoot = ".")

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$packageFile = Get-ChildItem `
    -Path "$RepositoryRoot/apps/desktop/build/compose/binaries/main/msi" `
    -Filter "*.msi" | Select-Object -First 1
if ($null -eq $packageFile) {
    throw "MSI package was not produced"
}

function Invoke-MsiExec([string]$Arguments) {
    $process = Start-Process msiexec.exe -ArgumentList $Arguments -Wait -PassThru
    if ($process.ExitCode -notin @(0, 3010)) {
        throw "msiexec failed with exit code $($process.ExitCode)"
    }
}

function Assert-X64Executable([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    $reader = [System.IO.BinaryReader]::new($stream)
    try {
        $stream.Position = 0x3c
        $peOffset = $reader.ReadUInt32()
        $stream.Position = $peOffset + 4
        $machine = $reader.ReadUInt16()
        if ($machine -ne 0x8664) {
            throw "Expected x64 PE executable, found machine type 0x$($machine.ToString('x4'))"
        }
    } finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

$msiPath = $packageFile.FullName
$installLog = Join-Path $env:RUNNER_TEMP "blowhard-msi-install.log"
Invoke-MsiExec "/i `"$msiPath`" /qn /norestart /L*v `"$installLog`""

try {
    $launcher = @(
        "$env:LOCALAPPDATA/Blowhard/Blowhard.exe",
        "$env:ProgramFiles/Blowhard/Blowhard.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($null -eq $launcher) {
        throw "Installed Blowhard launcher was not found"
    }

    $installRoot = Split-Path $launcher
    & python "$RepositoryRoot/tools/packaging/verify_distribution.py" `
        $installRoot `
        --forbid-text $env:GITHUB_WORKSPACE `
        --forbid-text $env:RUNNER_TEMP
    if ($LASTEXITCODE -ne 0) {
        throw "Distribution inspection failed"
    }

    Assert-X64Executable $launcher
    $signature = Get-AuthenticodeSignature $launcher
    if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::NotSigned) {
        throw "Expected an unsigned development package, found signature status $($signature.Status)"
    }
    $savedJavaHome = $env:JAVA_HOME
    $savedPath = $env:PATH
    try {
        Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
        $env:PATH = "$env:SystemRoot/System32;$env:SystemRoot"
        $env:BLOWHARD_PACKAGING_SMOKE_TEST = "1"
        $application = Start-Process $launcher -Wait -PassThru
        if ($application.ExitCode -ne 0) {
            throw "Installed application exited with code $($application.ExitCode)"
        }
    } finally {
        $env:JAVA_HOME = $savedJavaHome
        $env:PATH = $savedPath
        Remove-Item Env:BLOWHARD_PACKAGING_SMOKE_TEST -ErrorAction SilentlyContinue
    }
    Write-Output "Installed and launched $msiPath (x64)"
} finally {
    $uninstallLog = Join-Path $env:RUNNER_TEMP "blowhard-msi-uninstall.log"
    Invoke-MsiExec "/x `"$msiPath`" /qn /norestart /L*v `"$uninstallLog`""
}

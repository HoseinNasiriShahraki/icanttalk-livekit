$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js 22 or newer is required."
}

$nodeVersion = (node --version).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Unable to execute Node.js."
}

$nodeMajor = [int]($nodeVersion.TrimStart("v").Split(".")[0])
if ($nodeMajor -lt 22) {
    throw "Node.js 22 or newer is required. Current version: $nodeVersion"
}

Set-Location $PSScriptRoot

Invoke-NativeCommand npm install
Invoke-NativeCommand npm run dist:win

$package = Get-Content (Join-Path $PSScriptRoot "package.json") -Raw | ConvertFrom-Json
$installer = Join-Path $PSScriptRoot "release\iCANTTalk-Setup-$($package.version)-x64.exe"

if (-not (Test-Path $installer)) {
    throw "Build reported success, but the expected installer was not found: $installer"
}

$item = Get-Item $installer
Write-Host ""
Write-Host "Build complete." -ForegroundColor Green
Write-Host "Installer: $($item.FullName)" -ForegroundColor Green
Write-Host "Size: $([math]::Round($item.Length / 1MB, 2)) MB" -ForegroundColor Green

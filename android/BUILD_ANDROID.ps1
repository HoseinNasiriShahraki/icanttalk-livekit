$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$gradleVersion = "8.13"
$toolsDir = Join-Path $PSScriptRoot ".tools"
$gradleHome = Join-Path $toolsDir "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome "bin\gradle.bat"
$zipPath = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"

function Get-JavaMajorVersion {
    param([Parameter(Mandatory = $true)][string]$JavaExe)

    # java -version writes normal output to stderr. Temporarily avoid turning
    # that native stderr stream into a terminating PowerShell error.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionOutput = (& $JavaExe -version 2>&1 | Out-String)
        $javaExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($javaExitCode -ne 0) {
        return $null
    }

    if ($versionOutput -match 'version\s+"(?<major>\d+)(?:\.|\")') {
        return [int]$Matches.major
    }

    if ($versionOutput -match 'openjdk\s+(?<major>\d+)(?:\.|\s)') {
        return [int]$Matches.major
    }

    return $null
}

function Find-Jdk17 {
    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:ICANTTALK_JAVA_HOME) {
        $candidates.Add($env:ICANTTALK_JAVA_HOME)
    }

    if ($env:JAVA_HOME) {
        $candidates.Add($env:JAVA_HOME)
    }

    $searchRoots = @(
        "C:\Program Files\Microsoft",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\BellSoft",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) {
            continue
        }

        Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -match '(?i)(jdk|openjdk|corretto|liberica).*17|17.*(jdk|openjdk|corretto|liberica)'
            } |
            Sort-Object LastWriteTime -Descending |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        $javaExe = Join-Path $candidate "bin\java.exe"
        if (-not (Test-Path $javaExe)) {
            continue
        }

        $major = Get-JavaMajorVersion -JavaExe $javaExe
        if ($major -eq 17) {
            return $candidate
        }
    }

    return $null
}

$jdk17 = Find-Jdk17
if (-not $jdk17) {
    throw @"
JDK 17 was not found.

Install it from an Administrator PowerShell window with:
    choco install microsoft-openjdk17 -y

Then close PowerShell, open it again, and rerun this script.
You can also set ICANTTALK_JAVA_HOME to a JDK 17 directory.
"@
}

$env:JAVA_HOME = $jdk17
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
$javaMajor = Get-JavaMajorVersion -JavaExe $javaExe
if ($javaMajor -ne 17) {
    throw "This build requires JDK 17, but JAVA_HOME resolves to Java $javaMajor at $env:JAVA_HOME"
}

Write-Host "Using JDK 17: $env:JAVA_HOME" -ForegroundColor Cyan
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & $javaExe -version
    $javaExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousPreference
}
if ($javaExitCode -ne 0) {
    throw "Unable to run Java from $javaExe"
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultSdk) {
        $env:ANDROID_HOME = $defaultSdk
        $env:ANDROID_SDK_ROOT = $defaultSdk
    }
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "Android SDK was not found. Set ANDROID_HOME to your Android SDK directory."
}

if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading Gradle $gradleVersion..."
        Invoke-WebRequest `
            -Uri "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" `
            -OutFile $zipPath
    }

    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
}

& $gradleExe --no-daemon --stacktrace clean :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Android build failed with exit code $LASTEXITCODE."
}

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "Gradle reported success, but the APK was not found at $apk"
}

Write-Host ""
Write-Host "Build complete." -ForegroundColor Green
Write-Host "APK: $apk" -ForegroundColor Green

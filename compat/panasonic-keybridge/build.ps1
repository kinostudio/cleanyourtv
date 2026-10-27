param([Parameter(Mandatory=$true)][string]$ToolchainRoot)
$ErrorActionPreference = 'Stop'
$toolchain = (Resolve-Path $ToolchainRoot).Path
$jdk = Get-ChildItem (Join-Path $toolchain 'jdk') -Directory | Select-Object -First 1 -ExpandProperty FullName
$androidJar = Join-Path $toolchain 'sdk/platforms/android-34/android.jar'
$bin = Join-Path $toolchain 'sdk/build-tools/36.1.0'
$keystore = Join-Path $toolchain 'android-user/debug.keystore'
if (-not (Test-Path -LiteralPath $keystore)) { throw 'Existing local debug keystore is required; none was created.' }
$build = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path "$build/classes", "$build/dex", "$build/shell-classes", "$build/shell-dex" | Out-Null
$env:JAVA_HOME = $jdk
& "$bin/aapt2.exe" compile --dir "$PSScriptRoot/res" -o "$build/resources.zip"
if ($LASTEXITCODE -ne 0) { throw 'Resource compilation failed' }
& "$bin/aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot/AndroidManifest.xml" -o "$build/base.apk" "$build/resources.zip"
if ($LASTEXITCODE -ne 0) { throw 'Resource linking failed' }
$sources = Get-ChildItem "$PSScriptRoot/src" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& "$jdk/bin/javac.exe" -source 8 -target 8 -classpath $androidJar -d "$build/classes" @sources
if ($LASTEXITCODE -ne 0) { throw 'APK javac failed' }
& "$jdk/bin/jar.exe" cf "$build/classes.jar" -C "$build/classes" .
if ($LASTEXITCODE -ne 0) { throw 'APK jar failed' }
& "$bin/d8.bat" --lib $androidJar --min-api 26 --output "$build/dex" "$build/classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'APK d8 failed' }
Copy-Item "$build/base.apk" "$build/unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open("$build/unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$build/dex/classes.dex", 'classes.dex') | Out-Null }
finally { $zip.Dispose() }
& "$bin/zipalign.exe" -f 4 "$build/unsigned.apk" "$build/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
$apk = "$build/PanasonicKeyBridge-0.1.1.apk"
& "$bin/apksigner.bat" sign --ks $keystore --out $apk "$build/aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& "$bin/apksigner.bat" verify --verbose --print-certs $apk *> "$build/signature.txt"
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& "$jdk/bin/javac.exe" -source 8 -target 8 -classpath $androidJar -d "$build/shell-classes" "$PSScriptRoot/shell/LayoutTrial.java"
if ($LASTEXITCODE -ne 0) { throw 'Trial javac failed' }
& "$jdk/bin/jar.exe" cf "$build/shell-classes.jar" -C "$build/shell-classes" .
if ($LASTEXITCODE -ne 0) { throw 'Trial jar failed' }
& "$bin/d8.bat" --lib $androidJar --min-api 26 --output "$build/shell-dex" "$build/shell-classes.jar"
if ($LASTEXITCODE -ne 0) { throw 'Trial d8 failed' }
Copy-Item "$build/shell-dex/classes.dex" "$build/layout-trial.dex" -Force
$artifacts = @($apk, "$build/layout-trial.dex") | ForEach-Object {
    [ordered]@{name=[IO.Path]::GetFileName($_); bytes=(Get-Item -LiteralPath $_).Length; sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$artifacts | ConvertTo-Json | Set-Content "$build/artifacts.json" -Encoding utf8
$artifacts | ConvertTo-Json


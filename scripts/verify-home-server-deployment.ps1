[CmdletBinding()]
param(
    [ValidateRange(120, 1800)]
    [int] $StartupTimeoutSeconds = 600,

    [switch] $RunBrowserSuite,

    [switch] $RunRecoverySuite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-Condition {
    param(
        [Parameter(Mandatory)]
        [bool] $Condition,

        [Parameter(Mandatory)]
        [string] $Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-ProcessEnvironmentNameComparer {
    if ($IsWindows) {
        return [StringComparer]::OrdinalIgnoreCase
    }
    return [StringComparer]::Ordinal
}

function Invoke-WithProcessEnvironment {
    param(
        [Parameter(Mandatory)]
        [Collections.IDictionary] $Variables,

        [Parameter(Mandatory)]
        [scriptblock] $Action
    )

    $previous = [Collections.Generic.Dictionary[string, object]]::new(
        (Get-ProcessEnvironmentNameComparer)
    )
    try {
        foreach ($name in $Variables.Keys) {
            $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
            if ($null -eq $Variables[$name]) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            }
            else {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    [string] $Variables[$name],
                    "Process"
                )
            }
        }
        & $Action
    }
    finally {
        foreach ($name in $Variables.Keys) {
            if ($null -eq $previous[$name]) {
                Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
            }
            else {
                [Environment]::SetEnvironmentVariable(
                    $name,
                    [string] $previous[$name],
                    "Process"
                )
            }
        }
    }
}

function New-ProcessEnvironmentMap {
    $result = [Collections.Generic.Dictionary[string, object]]::new(
        (Get-ProcessEnvironmentNameComparer)
    )
    return ,$result
}

function Add-InheritedEnvironmentRemovals {
    param(
        [Parameter(Mandatory)]
        [Collections.Generic.Dictionary[string, object]] $Variables,

        [Parameter(Mandatory)]
        [string] $NamePattern
    )

    foreach ($inheritedName in [Environment]::GetEnvironmentVariables("Process").Keys) {
        $name = [string] $inheritedName
        if ($name -match $NamePattern) {
            $Variables[$name] = $null
        }
    }
}

function Get-SelectedDockerEndpoint {
    param([Parameter(Mandatory)][string] $DockerCommand)

    $contextOverride = [Environment]::GetEnvironmentVariable("DOCKER_CONTEXT", "Process")
    $hostOverride = [Environment]::GetEnvironmentVariable("DOCKER_HOST", "Process")
    if (-not [string]::IsNullOrWhiteSpace($contextOverride)) {
        $arguments = @(
            "context", "inspect", $contextOverride.Trim(),
            "--format", "{{.Endpoints.docker.Host}}"
        )
    }
    elseif (-not [string]::IsNullOrWhiteSpace($hostOverride)) {
        return $hostOverride.Trim()
    }
    else {
        $arguments = @("context", "inspect", "--format", "{{.Endpoints.docker.Host}}")
    }

    $endpointOutput = & $DockerCommand @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the selected Docker context without contacting its daemon."
    }
    return (($endpointOutput -join "").Trim())
}

function Assert-LocalDockerEndpoint {
    param([Parameter(Mandatory)][string] $Endpoint)

    $localEndpoint =
        $Endpoint -match '^unix:///.+' -or
        $Endpoint -match '^npipe:////\./pipe/.+' -or
        $Endpoint -match '^fd://.+' -or
        $Endpoint -match '^tcp://(?:127(?:\.[0-9]{1,3}){3}|\[::1\]):[0-9]+$'
    Assert-Condition $localEndpoint `
        "The rehearsal rejects remote Docker endpoints before daemon contact."
}

function Invoke-DockerProcess {
    param([Parameter(Mandatory)][string[]] $Arguments)

    $result = Invoke-WithProcessEnvironment $script:DockerEnvironment {
        $nativeOutput = @(& $script:DockerCommand @Arguments 2>&1)
        [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = $nativeOutput
        }
    }
    return ,$result
}

function Invoke-DockerCommand {
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [switch] $Capture
    )

    $result = Invoke-DockerProcess -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $($result.ExitCode)."
    }

    if ($Capture) {
        return @($result.Output)
    }
    $result.Output | ForEach-Object { Write-Host $_ }
}

function Invoke-ComposeCommand {
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [switch] $Capture
    )

    $composeArguments = @(
        "compose",
        "--env-file", $script:EnvFile,
        "--file", $script:ComposeFile
    )
    if ($null -ne $script:ComposeOverlayFile) {
        $composeArguments += @("--file", $script:ComposeOverlayFile)
    }
    $composeArguments += @("--project-name", $script:ProjectName)
    $composeArguments += $Arguments
    return Invoke-DockerCommand -Arguments $composeArguments -Capture:$Capture
}

function Get-ExactDockerImageTagId {
    param([Parameter(Mandatory)][string] $ImageReference)

    $tagPrefix = "$ImageReference|"
    $rows = Invoke-DockerCommand -Arguments @(
        "image", "ls", "--all", "--no-trunc",
        "--format", "{{.Repository}}:{{.Tag}}|{{.ID}}"
    ) -Capture
    $matchingRows = @(
        $rows | Where-Object {
            ([string] $_).StartsWith($tagPrefix, [StringComparison]::Ordinal)
        }
    )
    Assert-Condition ($matchingRows.Count -le 1) `
        "Docker returned an ambiguous image-tag inventory for $ImageReference."
    if ($matchingRows.Count -eq 0) {
        return $null
    }

    $imageId = ([string] $matchingRows[0]).Substring($tagPrefix.Length).Trim()
    Assert-Condition ($imageId -cmatch '^sha256:[0-9a-f]{64}$') `
        "Docker returned a non-canonical image ID for $ImageReference."
    return $imageId
}

function Test-ExactDockerImageIdPresent {
    param([Parameter(Mandatory)][string] $ImageId)

    Assert-Condition ($ImageId -cmatch '^sha256:[0-9a-f]{64}$') `
        "Refused to inspect a non-canonical owned image ID."
    $ids = Invoke-DockerCommand -Arguments @(
        "image", "ls", "--all", "--no-trunc", "--quiet"
    ) -Capture
    return @(
        $ids | Where-Object { ([string] $_).Trim() -ceq $ImageId }
    ).Count -gt 0
}

function Remove-ExactOwnedImageTag {
    param(
        [Parameter(Mandatory)][string] $ImageReference,
        [AllowNull()][string] $OwnedImageId
    )

    $currentImageId = Get-ExactDockerImageTagId -ImageReference $ImageReference
    if ([string]::IsNullOrWhiteSpace($OwnedImageId)) {
        Assert-Condition ([string]::IsNullOrWhiteSpace($currentImageId)) `
            "Refused to remove image tag $ImageReference because this run did not record its exact image ID."
        Write-Host "[cleanup] Image tag $ImageReference was never published by this run."
        return
    }

    Assert-Condition ($OwnedImageId -cmatch '^sha256:[0-9a-f]{64}$') `
        "Refused to clean an image without a canonical recorded image ID."
    if ([string]::IsNullOrWhiteSpace($currentImageId)) {
        if (Test-ExactDockerImageIdPresent -ImageId $OwnedImageId) {
            Write-Warning (
                "Owned image tag $ImageReference is already absent; exact image ID " +
                "$OwnedImageId remains and was retained because its current tag ownership cannot be proven."
            )
        }
        else {
            Write-Host "[cleanup] Owned image tag and image ID are already absent: $ImageReference."
        }
        return
    }

    Assert-Condition ($currentImageId -ceq $OwnedImageId) `
        "Refused to remove image tag $ImageReference because it no longer points to this run's exact image ID."
    Invoke-DockerCommand -Arguments @("image", "rm", $ImageReference) | Out-Null
    $remainingTagId = Get-ExactDockerImageTagId -ImageReference $ImageReference
    Assert-Condition ([string]::IsNullOrWhiteSpace($remainingTagId)) `
        "The exact owned image tag still exists after cleanup: $ImageReference."
    if (Test-ExactDockerImageIdPresent -ImageId $OwnedImageId) {
        Write-Host (
            "[cleanup] Removed exact owned tag $ImageReference; image ID $OwnedImageId " +
            "remains under another Docker reference and was not force-deleted."
        )
    }
    else {
        Write-Host "[cleanup] Removed exact owned image tag and unreferenced ID: $ImageReference."
    }
}

function New-DockerProcessStartInfo {
    param(
        [Parameter(Mandatory)][string[]] $Arguments,
        [switch] $RedirectStandardInput,
        [switch] $RedirectStandardOutput
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:DockerCommand
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $RedirectStandardInput
    $startInfo.RedirectStandardOutput = $RedirectStandardOutput
    $startInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) {
        [void] $startInfo.ArgumentList.Add($argument)
    }
    foreach ($name in $script:DockerEnvironment.Keys) {
        if ($null -eq $script:DockerEnvironment[$name]) {
            [void] $startInfo.Environment.Remove($name)
        }
        else {
            $startInfo.Environment[$name] = [string] $script:DockerEnvironment[$name]
        }
    }
    return $startInfo
}

function Invoke-DockerBinaryToFile {
    param(
        [Parameter(Mandatory)][string[]] $Arguments,
        [Parameter(Mandatory)][string] $DestinationPath
    )

    Assert-Condition (-not (Test-Path -LiteralPath $DestinationPath)) `
        "Refused to overwrite an existing recovery artifact."
    $startInfo = New-DockerProcessStartInfo `
        -Arguments $Arguments `
        -RedirectStandardOutput
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $output = $null
    try {
        Assert-Condition ($process.Start()) "Unable to start the Docker dump process."
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $output = [IO.FileStream]::new(
            $DestinationPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        $process.StandardOutput.BaseStream.CopyTo($output)
        $output.Flush($true)
        $output.Dispose()
        $output = $null
        $process.WaitForExit()
        $stderrText = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            throw "The Docker dump process failed with exit code $($process.ExitCode): $stderrText"
        }
    }
    finally {
        if ($null -ne $output) {
            $output.Dispose()
        }
        $process.Dispose()
    }
}

function Invoke-DockerInputFileProcess {
    param(
        [Parameter(Mandatory)][string] $InputPath,
        [Parameter(Mandatory)][string[]] $Arguments
    )

    Assert-Condition (Test-Path -LiteralPath $InputPath -PathType Leaf) `
        "The Docker input artifact is missing."
    $startInfo = New-DockerProcessStartInfo `
        -Arguments $Arguments `
        -RedirectStandardInput `
        -RedirectStandardOutput
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $inputStream = $null
    $copyFailure = $null
    try {
        Assert-Condition ($process.Start()) "Unable to start the Docker input process."
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $inputStream = [IO.File]::OpenRead($InputPath)
        try {
            $inputStream.CopyTo($process.StandardInput.BaseStream)
            $process.StandardInput.BaseStream.Flush()
        }
        catch {
            $copyFailure = $_
        }
        finally {
            $process.StandardInput.Close()
        }
        $process.WaitForExit()
        $stdoutText = $stdoutTask.GetAwaiter().GetResult()
        $stderrText = $stderrTask.GetAwaiter().GetResult()
        if ($process.ExitCode -eq 0 -and $null -ne $copyFailure) {
            throw $copyFailure
        }
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout = $stdoutText
            Stderr = $stderrText
        }
    }
    finally {
        if ($null -ne $inputStream) {
            $inputStream.Dispose()
        }
        $process.Dispose()
    }
}

function Assert-RecoveryDigest {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $ExpectedSha256
    )

    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    Assert-Condition ($actual -eq $ExpectedSha256.ToLowerInvariant()) `
        "Recovery artifact SHA-256 mismatch."
}

function Get-RecoveryManifestKeys {
    return @(
        "schema_version",
        "backup_id",
        "started_utc",
        "completed_utc",
        "project",
        "database_name",
        "database_bytes",
        "archive_file",
        "pg_dump_options",
        "archive_bytes",
        "archive_sha256",
        "archive_inventory_file",
        "archive_inventory_bytes",
        "archive_inventory_entries",
        "archive_inventory_sha256",
        "encryption",
        "store_identity_sha256",
        "git_sha",
        "postgres_server_version_num",
        "pg_dump_version",
        "postgres_volume_name",
        "postgres_image_reference",
        "postgres_image_id",
        "postgres_image_revision",
        "api_image_reference",
        "api_image_id",
        "api_image_revision",
        "web_image_reference",
        "web_image_id",
        "web_image_revision",
        "caddy_production_image_reference",
        "caddy_production_image_id",
        "caddy_production_image_revision"
    )
}

function Write-RecoveryKeyValueManifest {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][Collections.IDictionary] $Manifest
    )

    Assert-Condition (-not (Test-Path -LiteralPath $Path)) `
        "Refused to overwrite an existing recovery manifest."
    $requiredKeys = @(Get-RecoveryManifestKeys)
    Assert-Condition ($Manifest.Count -eq $requiredKeys.Count) `
        "The local recovery manifest does not have the production key count."
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($key in $requiredKeys) {
        Assert-Condition (@($Manifest.Keys) -ccontains $key) `
            "The local recovery manifest is missing required key $key."
        $value = [string] $Manifest[$key]
        Assert-Condition (
            $key -cmatch '^[a-z][a-z0-9_]*$' -and
            $value -cmatch '^[A-Za-z0-9._:/+-]+$'
        ) "The local recovery manifest contains a non-canonical key or value."
        [void] $lines.Add("$key=$value")
    }
    foreach ($key in $Manifest.Keys) {
        Assert-Condition ($requiredKeys -ccontains ([string] $key)) `
            "The local recovery manifest contains unknown key $key."
    }
    [IO.File]::WriteAllText(
        $Path,
        ($lines -join "`n") + "`n",
        [Text.UTF8Encoding]::new($false)
    )
}

function Assert-ExactRecoveryPointBundle {
    param(
        [Parameter(Mandatory)][string] $BundlePath,
        [Parameter(Mandatory)][Collections.IDictionary] $ExpectedManifest,
        [switch] $AllowStagingName
    )

    $bundle = Get-Item -LiteralPath $BundlePath -Force -ErrorAction Stop
    Assert-Condition (
        $bundle.PSIsContainer -and
        ($bundle.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0
    ) "The recovery bundle must be a regular non-link directory."

    $expectedMembers = @(
        "database.dump",
        "database.dump.sha256",
        "database.inventory",
        "manifest"
    )
    $members = @(Get-ChildItem -LiteralPath $BundlePath -Force)
    $memberNames = @($members | ForEach-Object { $_.Name } | Sort-Object -CaseSensitive)
    $memberDifference = @(
        Compare-Object `
            -ReferenceObject ($expectedMembers | Sort-Object -CaseSensitive) `
            -DifferenceObject $memberNames `
            -CaseSensitive
    )
    Assert-Condition (
        $members.Count -eq $expectedMembers.Count -and
        $memberDifference.Count -eq 0
    ) "A completed recovery point must contain exactly the production four-member bundle."
    foreach ($member in $members) {
        Assert-Condition (
            -not $member.PSIsContainer -and
            ($member.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -and
            $member.LinkType -cne "HardLink"
        ) "Recovery bundle members must be regular single-link files."
    }

    $manifestPath = Join-Path $BundlePath "manifest"
    $requiredKeys = @(Get-RecoveryManifestKeys)
    $manifest = [Collections.Generic.Dictionary[string, string]]::new(
        [StringComparer]::Ordinal
    )
    $manifestBytes = [IO.File]::ReadAllBytes($manifestPath)
    $hasUtf8Bom =
        $manifestBytes.Length -ge 3 -and
        $manifestBytes[0] -eq 0xef -and
        $manifestBytes[1] -eq 0xbb -and
        $manifestBytes[2] -eq 0xbf
    Assert-Condition (-not $hasUtf8Bom) `
        "The recovery manifest must be BOM-free canonical UTF-8."
    $manifestLines = [IO.File]::ReadAllLines($manifestPath, [Text.Encoding]::UTF8)
    Assert-Condition ($manifestLines.Count -eq $requiredKeys.Count) `
        "The recovery manifest must contain exactly one line per required key."
    foreach ($line in $manifestLines) {
        $match = [regex]::Match(
            $line,
            '^(?<key>[a-z][a-z0-9_]*)=(?<value>[A-Za-z0-9._:/+-]+)$',
            [Text.RegularExpressions.RegexOptions]::CultureInvariant
        )
        Assert-Condition $match.Success "The recovery manifest contains invalid K/V syntax."
        $key = $match.Groups["key"].Value
        $value = $match.Groups["value"].Value
        Assert-Condition ($requiredKeys -ccontains $key) `
            "The recovery manifest contains unknown key $key."
        Assert-Condition (-not $manifest.ContainsKey($key)) `
            "The recovery manifest contains duplicate key $key."
        $manifest.Add($key, $value)
    }
    foreach ($key in $requiredKeys) {
        Assert-Condition ($manifest.ContainsKey($key)) `
            "The recovery manifest is missing required key $key."
        Assert-Condition (
            @($ExpectedManifest.Keys) -ccontains $key -and
            $manifest[$key] -ceq ([string] $ExpectedManifest[$key])
        ) "Recovery manifest source fact $key differs from the observed local rehearsal fact."
    }
    Assert-Condition ($ExpectedManifest.Count -eq $requiredKeys.Count) `
        "The expected recovery facts do not match the production manifest allowlist."

    $timestampFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    $timestampStyles =
        [Globalization.DateTimeStyles]::AssumeUniversal -bor
        [Globalization.DateTimeStyles]::AdjustToUniversal
    try {
        $startedUtc = [DateTimeOffset]::ParseExact(
            $manifest["started_utc"],
            $timestampFormat,
            [Globalization.CultureInfo]::InvariantCulture,
            $timestampStyles
        )
        $completedUtc = [DateTimeOffset]::ParseExact(
            $manifest["completed_utc"],
            $timestampFormat,
            [Globalization.CultureInfo]::InvariantCulture,
            $timestampStyles
        )
    }
    catch {
        throw "The recovery manifest contains a non-canonical UTC timestamp."
    }
    Assert-Condition (
        $startedUtc.ToString($timestampFormat, [Globalization.CultureInfo]::InvariantCulture) -ceq
            $manifest["started_utc"] -and
        $completedUtc.ToString($timestampFormat, [Globalization.CultureInfo]::InvariantCulture) -ceq
            $manifest["completed_utc"] -and
        $completedUtc -ge $startedUtc
    ) "The recovery manifest UTC interval is not canonical and ordered."
    $expectedBackupPrefix = $startedUtc.ToString(
        "yyyyMMdd'T'HHmmss'Z'",
        [Globalization.CultureInfo]::InvariantCulture
    )
    Assert-Condition (
        $manifest["backup_id"] -cmatch "^$([regex]::Escape($expectedBackupPrefix))-[0-9a-z]{8}$" -and
        ($AllowStagingName -or (Split-Path -Leaf $BundlePath) -ceq $manifest["backup_id"])
    ) "The recovery manifest backup ID is not bound to its UTC start and published directory."

    Assert-Condition ($manifest["schema_version"] -ceq "1") `
        "The recovery manifest schema version changed."
    Assert-Condition ($manifest["database_name"] -ceq "wsr") `
        "The recovery manifest database identity changed."
    Assert-Condition ($manifest["archive_file"] -ceq "database.dump") `
        "The recovery manifest archive filename changed."
    Assert-Condition (
        $manifest["pg_dump_options"] -ceq
            "format-custom+compress-6+no-owner+no-privileges+no-password"
    ) "The recovery manifest pg_dump option contract changed."
    Assert-Condition ($manifest["archive_inventory_file"] -ceq "database.inventory") `
        "The recovery manifest inventory filename changed."
    Assert-Condition ($manifest["encryption"] -ceq "none-demo-only") `
        "The local rehearsal must retain the reduced-assurance DEMO encryption decision."
    Assert-Condition ($manifest["database_bytes"] -cmatch '^[1-9][0-9]{0,14}$') `
        "Recovery manifest database byte count is invalid."
    foreach ($numericKey in @(
        "archive_bytes",
        "archive_inventory_bytes",
        "archive_inventory_entries"
    )) {
        Assert-Condition ($manifest[$numericKey] -cmatch '^[1-9][0-9]*$') `
            "Recovery manifest numeric field $numericKey is invalid."
    }
    foreach ($hashKey in @(
        "archive_sha256",
        "archive_inventory_sha256",
        "store_identity_sha256"
    )) {
        Assert-Condition ($manifest[$hashKey] -cmatch '^[0-9a-f]{64}$') `
            "Recovery manifest SHA-256 field $hashKey is invalid."
    }
    Assert-Condition ($manifest["git_sha"] -cmatch '^[0-9a-f]{40}$') `
        "The recovery manifest Git SHA is invalid."
    Assert-Condition ($manifest["postgres_server_version_num"] -cmatch '^17[0-9]{4}$') `
        "The recovery manifest PostgreSQL server version is invalid."
    Assert-Condition ($manifest["pg_dump_version"] -cmatch '^17(?:[.][0-9]+)+$') `
        "The recovery manifest pg_dump version is invalid."
    Assert-Condition (
        $manifest["postgres_image_reference"] -ceq "postgres:17-alpine" -and
        $manifest["postgres_image_id"] -cmatch '^sha256:[0-9a-f]{64}$' -and
        (
            $manifest["postgres_image_revision"] -ceq "unavailable" -or
            $manifest["postgres_image_revision"] -cmatch '^[0-9a-f]{40}$'
        )
    ) "The recovery manifest PostgreSQL image identity is invalid."
    $releaseImagePrefixes = [ordered]@{
        api = "wall-street-receipts-api:"
        web = "wall-street-receipts-web:"
        caddy_production = "wall-street-receipts-caddy:"
    }
    foreach ($service in $releaseImagePrefixes.Keys) {
        Assert-Condition (
            $manifest["${service}_image_reference"] -ceq
                "$($releaseImagePrefixes[$service])$($manifest['git_sha'])" -and
            $manifest["${service}_image_id"] -cmatch '^sha256:[0-9a-f]{64}$' -and
            $manifest["${service}_image_revision"] -ceq $manifest["git_sha"]
        ) "The recovery manifest $service image reference, ID, and revision do not agree."
    }

    $dumpPath = Join-Path $BundlePath "database.dump"
    $checksumPath = Join-Path $BundlePath "database.dump.sha256"
    $inventoryPath = Join-Path $BundlePath "database.inventory"
    $actualDumpBytes = (Get-Item -LiteralPath $dumpPath).Length
    $actualDumpSha256 = (
        Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    Assert-Condition (
        [string] $actualDumpBytes -ceq $manifest["archive_bytes"] -and
        $actualDumpSha256 -ceq $manifest["archive_sha256"]
    ) "The recovery dump differs from its manifest byte count or SHA-256."
    $expectedChecksumBytes = [Text.UTF8Encoding]::new($false).GetBytes(
        "$actualDumpSha256  database.dump`n"
    )
    $actualChecksumBytes = [IO.File]::ReadAllBytes($checksumPath)
    Assert-Condition (
        [Collections.StructuralComparisons]::StructuralEqualityComparer.Equals(
            $actualChecksumBytes,
            $expectedChecksumBytes
        )
    ) `
        "The recovery checksum member is not the exact database.dump checksum line."

    $actualInventoryBytes = (Get-Item -LiteralPath $inventoryPath).Length
    $actualInventorySha256 = (
        Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $actualInventoryEntries = @(
        [IO.File]::ReadAllLines($inventoryPath, [Text.Encoding]::UTF8) |
            Where-Object { $_ -cnotmatch '^[;\s]*$' }
    ).Count
    Assert-Condition (
        [string] $actualInventoryBytes -ceq $manifest["archive_inventory_bytes"] -and
        [string] $actualInventoryEntries -ceq $manifest["archive_inventory_entries"] -and
        $actualInventorySha256 -ceq $manifest["archive_inventory_sha256"]
    ) "The recovery inventory differs from its manifest bytes, entry count, or SHA-256."

    return ,$manifest
}

function Copy-RecoveryBundleForMutation {
    param(
        [Parameter(Mandatory)][string] $SourcePath,
        [Parameter(Mandatory)][string] $DestinationPath,
        [string] $OmitMember = ""
    )

    $memberNames = @(
        "database.dump",
        "database.dump.sha256",
        "database.inventory",
        "manifest"
    )
    Assert-Condition (
        [string]::IsNullOrEmpty($OmitMember) -or $memberNames -ccontains $OmitMember
    ) "A mutation requested an unknown omitted recovery member."
    Assert-Condition (-not (Test-Path -LiteralPath $DestinationPath)) `
        "A recovery mutation destination already exists."
    [IO.Directory]::CreateDirectory($DestinationPath) | Out-Null
    foreach ($memberName in $memberNames) {
        if ($memberName -cne $OmitMember) {
            [IO.File]::Copy(
                (Join-Path $SourcePath $memberName),
                (Join-Path $DestinationPath $memberName),
                $false
            )
        }
    }
}

function Assert-RecoveryBundleRejected {
    param(
        [Parameter(Mandatory)][string] $BundlePath,
        [Parameter(Mandatory)][Collections.IDictionary] $ExpectedManifest,
        [Parameter(Mandatory)][string] $CaseName
    )

    $rejected = $false
    try {
        Assert-ExactRecoveryPointBundle `
            -BundlePath $BundlePath `
            -ExpectedManifest $ExpectedManifest | Out-Null
    }
    catch {
        $rejected = $true
    }
    Assert-Condition $rejected `
        "Recovery bundle negative case '$CaseName' was unexpectedly accepted."
}

function Assert-ExactRecoveryRuntime {
    param(
        [Parameter(Mandatory)][Collections.IDictionary] $Container,
        [Parameter(Mandatory)][string] $ExpectedContainerId,
        [Parameter(Mandatory)][string] $ExpectedContainerName,
        [Parameter(Mandatory)][string] $ExpectedOwnerMarker,
        [Parameter(Mandatory)][string] $ExpectedImageId,
        [Parameter(Mandatory)][string] $ExpectedTargetVolume,
        [Parameter(Mandatory)][string] $ExpectedSourceVolume
    )

    Assert-Condition (
        $Container.Id -eq $ExpectedContainerId -and
        $Container.Name -eq "/$ExpectedContainerName" -and
        $Container.Config.Labels["com.wallstreetreceipts.run-id"] -eq
            $ExpectedOwnerMarker -and
        $Container.Config.Labels["com.wallstreetreceipts.role"] -eq
            "recovery-rehearsal-database"
    ) "The recovery target lost its exact ID, name, or ownership labels."
    Assert-Condition ($Container.Image -eq $ExpectedImageId) `
        "The recovery target did not use the observed source PostgreSQL image ID."
    Assert-Condition ($Container.HostConfig.NetworkMode -eq "none") `
        "The recovery target must use network none."
    Assert-Condition (
        $null -eq $Container.HostConfig.PortBindings -or
        $Container.HostConfig.PortBindings.Count -eq 0
    ) "The recovery target unexpectedly publishes a host port."

    $networkNames = @()
    if (
        $null -ne $Container.NetworkSettings -and
        $null -ne $Container.NetworkSettings.Networks
    ) {
        $networkNames = @(
            $Container.NetworkSettings.Networks.Keys | Sort-Object -CaseSensitive
        )
    }
    Assert-Condition (
        $networkNames.Count -eq 0 -or
        ($networkNames.Count -eq 1 -and $networkNames[0] -ceq "none")
    ) "The recovery target has an unexpected live Docker network endpoint."

    $targetMounts = @($Container.Mounts)
    Assert-Condition (
        $targetMounts.Count -eq 1 -and
        $targetMounts[0].Type -eq "volume" -and
        $targetMounts[0].Name -eq $ExpectedTargetVolume -and
        $targetMounts[0].Destination -eq "/var/lib/postgresql/data" -and
        $targetMounts[0].RW -eq $true -and
        $targetMounts[0].Name -ne $ExpectedSourceVolume
    ) "The recovery target must have exactly one writable owned data-volume mount."
}

function Get-RecoverySourceGitSha {
    param([Parameter(Mandatory)][string] $RepositoryRoot)

    $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    Assert-Condition ($null -ne $gitCommand) `
        "Git is required when -RunRecoverySuite is selected."
    $buildInputs = @(
        "apps/api",
        "apps/web",
        "fixtures/v1",
        "package.json",
        "pnpm-lock.yaml",
        "pnpm-workspace.yaml",
        "deploy/home-server/api.Dockerfile",
        "deploy/home-server/api.Dockerfile.dockerignore",
        "deploy/home-server/web.Dockerfile",
        "deploy/home-server/web.Dockerfile.dockerignore",
        "deploy/home-server/caddy.Dockerfile",
        "deploy/home-server/caddy.Dockerfile.dockerignore"
    )
    $status = @(& $gitCommand.Source -C $RepositoryRoot status --porcelain=v1 --untracked-files=all -- @buildInputs 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 0) `
        "Unable to compare recovery image build inputs with Git HEAD."
    $meaningfulChanges = @(
        $status | Where-Object { $_ -notmatch 'apps[\\/]web[\\/]next-env\.d\.ts$' }
    )
    Assert-Condition ($meaningfulChanges.Count -eq 0) `
        "Recovery image build inputs must match Git HEAD; only excluded apps/web/next-env.d.ts may differ."

    $head = (@(& $gitCommand.Source -C $RepositoryRoot rev-parse --verify HEAD 2>&1) -join "").Trim()
    Assert-Condition ($LASTEXITCODE -eq 0 -and $head -cmatch '^[0-9a-f]{40}$') `
        "Git HEAD must resolve to one lowercase 40-character commit SHA."
    return $head
}

function Invoke-RecoveryDatabaseRehearsal {
    param(
        [Parameter(Mandatory)][string] $RepositoryRoot,
        [Parameter(Mandatory)][string] $TemporaryRoot,
        [Parameter(Mandatory)][string] $RunId,
        [Parameter(Mandatory)][string] $GitSha,
        [Parameter(Mandatory)][string] $ExpectedSourceVolume
    )

    $databaseEvidenceSql = Join-Path $RepositoryRoot "deploy/home-server/database-evidence.sql"
    Assert-Condition (Test-Path -LiteralPath $databaseEvidenceSql -PathType Leaf) `
        "ADR-047 database-evidence.sql is missing."

    $serviceMap = [ordered]@{
        postgres = "postgres"
        api = "api"
        web = "web"
        caddy = "caddy-rehearsal"
    }
    $runtimeFacts = [ordered]@{}
    $sourcePostgresId = $null
    $apiContainerId = $null
    foreach ($logicalName in $serviceMap.Keys) {
        $composeService = $serviceMap[$logicalName]
        $matching = Invoke-DockerCommand -Arguments @(
            "ps", "--filter", "status=running",
            "--filter", "label=com.docker.compose.project=$($script:ProjectName)",
            "--filter", "label=com.docker.compose.service=$composeService",
            "--format", "{{.ID}}"
        ) -Capture
        $matchingIds = @($matching | ForEach-Object { ([string] $_).Trim() } | Where-Object { $_ })
        Assert-Condition ($matchingIds.Count -eq 1) `
            "Expected exactly one running $composeService container in the owned source project."
        $containerId = $matchingIds[0]
        $containerJson = Invoke-DockerCommand -Arguments @("inspect", $containerId) -Capture
        $container = @(((@($containerJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
        Assert-Condition (
            $container.Config.Labels["com.docker.compose.project"] -eq $script:ProjectName -and
            $container.Config.Labels["com.docker.compose.service"] -eq $composeService
        ) "The observed $composeService container lost its Compose identity."
        Assert-Condition (
            $container.State.Running -eq $true -and
            $container.State.Health.Status -eq "healthy"
        ) "The observed $composeService container is not running and healthy."
        $publishedPorts = $container.HostConfig.PortBindings
        if ($logicalName -ne "caddy") {
            Assert-Condition ($null -eq $publishedPorts -or $publishedPorts.Count -eq 0) `
                "$composeService unexpectedly publishes a host port."
        }

        $imageJson = Invoke-DockerCommand -Arguments @("image", "inspect", $container.Image) -Capture
        $image = @(((@($imageJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
        $revision = $null
        if ($image.Config.ContainsKey("Labels") -and $null -ne $image.Config.Labels) {
            $revision = $image.Config.Labels["org.opencontainers.image.revision"]
        }
        $runtimeFacts[$logicalName] = [ordered]@{
            compose_service = $composeService
            container_id = $container.Id
            configured_image = $container.Config.Image
            image_id = $container.Image
            oci_revision = $revision
        }
        if ($logicalName -eq "postgres") {
            $sourcePostgresId = $container.Id
            Assert-Condition (
                $container.Config.Labels["com.wallstreetreceipts.role"] -eq
                    "production-primary-database"
            ) "The source database role label changed."
            $sourceMounts = @($container.Mounts)
            $databaseMounts = @(
                $container.Mounts | Where-Object { $_.Destination -eq "/var/lib/postgresql/data" }
            )
            $secretMounts = @(
                $container.Mounts | Where-Object {
                    $_.Destination -eq "/run/secrets/postgres_password"
                }
            )
            Assert-Condition (
                $sourceMounts.Count -eq 2 -and
                $databaseMounts.Count -eq 1 -and
                $databaseMounts[0].Type -eq "volume" -and
                $databaseMounts[0].Name -eq $ExpectedSourceVolume -and
                $databaseMounts[0].RW -eq $true -and
                $secretMounts.Count -eq 1 -and
                $secretMounts[0].Type -eq "bind" -and
                $secretMounts[0].RW -eq $false
            ) "The source database must have only its writable data volume and read-only password bind."
        }
        elseif ($logicalName -eq "api") {
            $apiContainerId = $container.Id
        }
    }
    Assert-Condition ($null -ne $sourcePostgresId) "The source PostgreSQL container was not observed."
    foreach ($applicationService in @("api", "web", "caddy")) {
        Assert-Condition ($runtimeFacts[$applicationService].oci_revision -eq $GitSha) `
            "The observed $applicationService image is not bound to Git HEAD."
    }

    $databaseBytesResult = Invoke-DockerCommand -Arguments @(
        "exec", $sourcePostgresId,
        "psql", "--username=wsr", "--dbname=wsr", "--no-password",
        "--tuples-only", "--no-align",
        "--command", "SELECT pg_database_size(current_database());"
    ) -Capture
    $databaseBytes = ((@($databaseBytesResult) -join "").Trim())
    Assert-Condition ($databaseBytes -cmatch '^[1-9][0-9]{0,14}$') `
        "The source database did not return a bounded adjacent capacity observation."

    $recoveryRoot = Join-Path $TemporaryRoot "adr047-recovery"
    [IO.Directory]::CreateDirectory($recoveryRoot) | Out-Null
    $storeIdentityPath = Join-Path $recoveryRoot ".local-store-identity"
    [IO.File]::WriteAllText(
        $storeIdentityPath,
        "schema_version=1`nnamespace=wall-street-receipts`nfilesystem_uuid=local-$RunId`n",
        [Text.UTF8Encoding]::new($false)
    )
    $storeIdentitySha256 = (
        Get-FileHash -LiteralPath $storeIdentityPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $startedUtc = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $compactStartedUtc = [DateTime]::ParseExact(
        $startedUtc,
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::AssumeUniversal
    ).ToUniversalTime().ToString("yyyyMMdd'T'HHmmss'Z'")
    $backupId = "$compactStartedUtc-$(Get-RandomLowerHex -ByteCount 4)"
    $partialPath = Join-Path $recoveryRoot ".partial-$backupId"
    $finalPath = Join-Path $recoveryRoot $backupId
    [IO.Directory]::CreateDirectory($partialPath) | Out-Null
    $orphanPartial = Join-Path $recoveryRoot ".partial-20000101T000000Z-$RunId"
    [IO.Directory]::CreateDirectory($orphanPartial) | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $orphanPartial "INTERRUPTED"),
        "intentionally incomplete staging evidence`n",
        [Text.UTF8Encoding]::new($false)
    )
    $eligibleBeforePublication = @(
        Get-ChildItem -LiteralPath $recoveryRoot -Directory |
            Where-Object { $_.Name -cmatch '^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}$' }
    )
    Assert-Condition ($eligibleBeforePublication.Count -eq 0) `
        "A partial recovery point became eligible before atomic publication."

    $dumpPath = Join-Path $partialPath "database.dump"
    Invoke-DockerBinaryToFile -Arguments @(
        "exec", $sourcePostgresId,
        "pg_dump", "--username=wsr", "--dbname=wsr", "--format=custom",
        "--compress=6", "--no-owner", "--no-privileges", "--no-password"
    ) -DestinationPath $dumpPath
    $dumpItem = Get-Item -LiteralPath $dumpPath
    Assert-Condition ($dumpItem.Length -gt 0) "pg_dump produced an empty recovery artifact."
    $dumpSha256 = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksumPath = Join-Path $partialPath "database.dump.sha256"
    [IO.File]::WriteAllText(
        $checksumPath,
        "$dumpSha256  database.dump`n",
        [Text.UTF8Encoding]::new($false)
    )

    $inventoryResult = Invoke-DockerInputFileProcess -InputPath $dumpPath -Arguments @(
        "exec", "--interactive", $sourcePostgresId, "pg_restore", "--list"
    )
    Assert-Condition ($inventoryResult.ExitCode -eq 0) `
        "pg_restore --list rejected the freshly captured custom archive: $($inventoryResult.Stderr)"
    Assert-Condition ($inventoryResult.Stdout -match 'TABLE DATA') `
        "The custom archive inventory contains no table-data entry."
    $inventoryPath = Join-Path $partialPath "database.inventory"
    [IO.File]::WriteAllText(
        $inventoryPath,
        $inventoryResult.Stdout,
        [Text.UTF8Encoding]::new($false)
    )
    $inventoryItem = Get-Item -LiteralPath $inventoryPath
    $inventorySha256 = (
        Get-FileHash -LiteralPath $inventoryPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    $inventoryEntries = @(
        [IO.File]::ReadAllLines($inventoryPath, [Text.Encoding]::UTF8) |
            Where-Object { $_ -cnotmatch '^[;\s]*$' }
    ).Count
    Assert-Condition ($inventoryEntries -gt 0) `
        "The custom archive inventory contains no counted restore entry."
    foreach ($logicalName in $runtimeFacts.Keys) {
        $observed = $runtimeFacts[$logicalName]
        $afterJson = Invoke-DockerCommand -Arguments @(
            "inspect", $observed.container_id
        ) -Capture
        $after = @(((@($afterJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
        Assert-Condition (
            $after.Id -eq $observed.container_id -and
            $after.Image -eq $observed.image_id -and
            $after.Config.Image -eq $observed.configured_image -and
            $after.State.Running -eq $true -and
            $after.State.Health.Status -eq "healthy"
        ) "The observed $logicalName runtime identity changed during backup capture."
    }

    $serverVersion = Invoke-DockerCommand -Arguments @(
        "exec", $sourcePostgresId,
        "psql", "--username=wsr", "--dbname=wsr", "--no-password", "--tuples-only", "--no-align",
        "--command", "SHOW server_version_num;"
    ) -Capture
    $serverVersionNumber = ((@($serverVersion) -join "").Trim())
    Assert-Condition ($serverVersionNumber -cmatch '^17[0-9]{4}$') `
        "The source PostgreSQL server_version_num is not version 17."
    $dumpVersion = Invoke-DockerCommand -Arguments @(
        "exec", $sourcePostgresId, "pg_dump", "--version"
    ) -Capture
    $dumpVersionText = ((@($dumpVersion) -join "").Trim())
    $dumpVersionMatch = [regex]::Match(
        $dumpVersionText,
        '(?<version>17(?:[.][0-9]+)+)$',
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    Assert-Condition $dumpVersionMatch.Success `
        "The source pg_dump version is not a canonical PostgreSQL 17 version."
    $dumpVersionNumber = $dumpVersionMatch.Groups["version"].Value
    $postgresRevision = [string] $runtimeFacts["postgres"].oci_revision
    if ($postgresRevision -cnotmatch '^[0-9a-f]{40}$') {
        $postgresRevision = "unavailable"
    }
    $completedUtc = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    $manifest = [ordered]@{
        schema_version = "1"
        backup_id = $backupId
        started_utc = $startedUtc
        completed_utc = $completedUtc
        project = $script:ProjectName
        database_name = "wsr"
        database_bytes = $databaseBytes
        archive_file = "database.dump"
        pg_dump_options = "format-custom+compress-6+no-owner+no-privileges+no-password"
        archive_bytes = [string] $dumpItem.Length
        archive_sha256 = $dumpSha256
        archive_inventory_file = "database.inventory"
        archive_inventory_bytes = [string] $inventoryItem.Length
        archive_inventory_entries = [string] $inventoryEntries
        archive_inventory_sha256 = $inventorySha256
        encryption = "none-demo-only"
        store_identity_sha256 = $storeIdentitySha256
        git_sha = $GitSha
        postgres_server_version_num = $serverVersionNumber
        pg_dump_version = $dumpVersionNumber
        postgres_volume_name = $ExpectedSourceVolume
        postgres_image_reference = [string] $runtimeFacts["postgres"].configured_image
        postgres_image_id = [string] $runtimeFacts["postgres"].image_id
        postgres_image_revision = $postgresRevision
        api_image_reference = [string] $runtimeFacts["api"].configured_image
        api_image_id = [string] $runtimeFacts["api"].image_id
        api_image_revision = [string] $runtimeFacts["api"].oci_revision
        web_image_reference = [string] $runtimeFacts["web"].configured_image
        web_image_id = [string] $runtimeFacts["web"].image_id
        web_image_revision = [string] $runtimeFacts["web"].oci_revision
        caddy_production_image_reference = [string] $runtimeFacts["caddy"].configured_image
        caddy_production_image_id = [string] $runtimeFacts["caddy"].image_id
        caddy_production_image_revision = [string] $runtimeFacts["caddy"].oci_revision
    }
    $manifestPath = Join-Path $partialPath "manifest"
    Write-RecoveryKeyValueManifest -Path $manifestPath -Manifest $manifest
    Assert-ExactRecoveryPointBundle `
        -BundlePath $partialPath `
        -ExpectedManifest $manifest `
        -AllowStagingName | Out-Null
    [IO.Directory]::Move($partialPath, $finalPath)
    $validatedManifest = Assert-ExactRecoveryPointBundle `
        -BundlePath $finalPath `
        -ExpectedManifest $manifest
    $dumpPath = Join-Path $finalPath $validatedManifest["archive_file"]
    $dumpSha256 = $validatedManifest["archive_sha256"]
    $restorePostgresImageId = $validatedManifest["postgres_image_id"]
    $manifestPath = Join-Path $finalPath "manifest"
    $manifestSha256 = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $eligibleAfterPublication = @(
        Get-ChildItem -LiteralPath $recoveryRoot -Directory |
            Where-Object { $_.Name -cmatch '^[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}$' }
    )
    Assert-Condition (
        $eligibleAfterPublication.Count -eq 1 -and
        $eligibleAfterPublication[0].Name -eq $backupId
    ) "Atomic recovery-point selection included a partial directory."

    $bundleMutationRoot = Join-Path $recoveryRoot "bundle-negative-$RunId"

    $missingMemberBundle = Join-Path $bundleMutationRoot "missing-member/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $missingMemberBundle `
        -OmitMember "database.dump.sha256"
    Assert-RecoveryBundleRejected `
        -BundlePath $missingMemberBundle `
        -ExpectedManifest $manifest `
        -CaseName "missing exact member"

    $extraMemberBundle = Join-Path $bundleMutationRoot "extra-member/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $extraMemberBundle
    [IO.File]::WriteAllText(
        (Join-Path $extraMemberBundle "unexpected.member"),
        "must be rejected`n",
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $extraMemberBundle `
        -ExpectedManifest $manifest `
        -CaseName "extra exact member"

    # Preserve the expected bytes while linking one member to the completed
    # recovery point. This proves rejection is caused by link topology rather
    # than by a checksum or manifest mismatch.
    $hardlinkBundle = Join-Path $bundleMutationRoot "hardlink-member/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $hardlinkBundle
    $hardlinkMemberPath = Join-Path $hardlinkBundle "database.inventory"
    $hardlinkPeerPath = Join-Path $bundleMutationRoot "hardlink-peer.inventory"
    New-Item `
        -ItemType HardLink `
        -Path $hardlinkPeerPath `
        -Target $hardlinkMemberPath | Out-Null
    $hardlinkMember = Get-Item -LiteralPath $hardlinkMemberPath -Force
    Assert-Condition ($hardlinkMember.LinkType -ceq "HardLink") `
        "The hardlink bundle mutation did not produce a detectable linked member."
    Assert-RecoveryBundleRejected `
        -BundlePath $hardlinkBundle `
        -ExpectedManifest $manifest `
        -CaseName "hardlinked exact member"

    $missingKeyBundle = Join-Path $bundleMutationRoot "missing-key/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $missingKeyBundle
    $missingKeyManifestPath = Join-Path $missingKeyBundle "manifest"
    $missingKeyLines = @(
        [IO.File]::ReadAllLines($missingKeyManifestPath, [Text.Encoding]::UTF8) |
            Where-Object { $_ -cnotmatch '^database_name=' }
    )
    [IO.File]::WriteAllText(
        $missingKeyManifestPath,
        ($missingKeyLines -join "`n") + "`n",
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $missingKeyBundle `
        -ExpectedManifest $manifest `
        -CaseName "missing required manifest key"

    $unknownKeyBundle = Join-Path $bundleMutationRoot "unknown-key/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $unknownKeyBundle
    $unknownManifestPath = Join-Path $unknownKeyBundle "manifest"
    $unknownManifest = [IO.File]::ReadAllText($unknownManifestPath, [Text.Encoding]::UTF8)
    $unknownManifest = $unknownManifest.Replace(
        "database_name=wsr`n",
        "unknown_database_name=wsr`n"
    )
    [IO.File]::WriteAllText(
        $unknownManifestPath,
        $unknownManifest,
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $unknownKeyBundle `
        -ExpectedManifest $manifest `
        -CaseName "unknown manifest key"

    $duplicateKeyBundle = Join-Path $bundleMutationRoot "duplicate-key/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $duplicateKeyBundle
    $duplicateManifestPath = Join-Path $duplicateKeyBundle "manifest"
    $duplicateManifest = [IO.File]::ReadAllText($duplicateManifestPath, [Text.Encoding]::UTF8)
    $duplicateManifest = $duplicateManifest.Replace(
        "database_name=wsr`n",
        "schema_version=1`n"
    )
    [IO.File]::WriteAllText(
        $duplicateManifestPath,
        $duplicateManifest,
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $duplicateKeyBundle `
        -ExpectedManifest $manifest `
        -CaseName "duplicate manifest key"

    $checksumMismatchBundle = Join-Path $bundleMutationRoot "checksum-mismatch/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $checksumMismatchBundle
    $zeroSha256 = [string]::new([char] '0', 64)
    [IO.File]::WriteAllText(
        (Join-Path $checksumMismatchBundle "database.dump.sha256"),
        "$zeroSha256  database.dump`n",
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $checksumMismatchBundle `
        -ExpectedManifest $manifest `
        -CaseName "checksum member mismatch"

    $inventoryMismatchBundle = Join-Path $bundleMutationRoot "inventory-mismatch/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $inventoryMismatchBundle
    [IO.File]::AppendAllText(
        (Join-Path $inventoryMismatchBundle "database.inventory"),
        "999999; 0 0 TABLE DATA public.invalid wsr`n",
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $inventoryMismatchBundle `
        -ExpectedManifest $manifest `
        -CaseName "inventory manifest mismatch"

    $dumpManifestMismatchBundle = Join-Path $bundleMutationRoot "dump-manifest-mismatch/$backupId"
    Copy-RecoveryBundleForMutation `
        -SourcePath $finalPath `
        -DestinationPath $dumpManifestMismatchBundle
    $mismatchedDumpPath = Join-Path $dumpManifestMismatchBundle "database.dump"
    $mismatchedDumpStream = [IO.File]::Open(
        $mismatchedDumpPath,
        [IO.FileMode]::Open,
        [IO.FileAccess]::ReadWrite,
        [IO.FileShare]::None
    )
    try {
        Assert-Condition ($mismatchedDumpStream.Length -gt 128) `
            "The recovery dump is too small for the manifest mismatch case."
        $mismatchedDumpStream.Position = [Math]::Floor($mismatchedDumpStream.Length / 2)
        $mismatchedOriginalByte = $mismatchedDumpStream.ReadByte()
        Assert-Condition ($mismatchedOriginalByte -ge 0) `
            "Unable to read the recovery dump mutation byte."
        $mismatchedDumpStream.Position = $mismatchedDumpStream.Position - 1
        $mismatchedDumpStream.WriteByte([byte] ($mismatchedOriginalByte -bxor 0xff))
        $mismatchedDumpStream.Flush($true)
    }
    finally {
        $mismatchedDumpStream.Dispose()
    }
    $mismatchedDumpSha256 = (
        Get-FileHash -LiteralPath $mismatchedDumpPath -Algorithm SHA256
    ).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText(
        (Join-Path $dumpManifestMismatchBundle "database.dump.sha256"),
        "$mismatchedDumpSha256  database.dump`n",
        [Text.UTF8Encoding]::new($false)
    )
    Assert-RecoveryBundleRejected `
        -BundlePath $dumpManifestMismatchBundle `
        -ExpectedManifest $manifest `
        -CaseName "dump manifest mismatch"

    $tamperedPath = Join-Path $recoveryRoot "tampered.dump"
    [IO.File]::Copy($dumpPath, $tamperedPath, $false)
    $tamperedStream = [IO.File]::Open($tamperedPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite)
    try {
        Assert-Condition ($tamperedStream.Length -gt 128) `
            "The recovery archive is too small for the digest mutation check."
        $tamperedStream.Position = [Math]::Floor($tamperedStream.Length / 2)
        $originalByte = $tamperedStream.ReadByte()
        Assert-Condition ($originalByte -ge 0) "Unable to read the digest mutation byte."
        $tamperedStream.Position = $tamperedStream.Position - 1
        $tamperedStream.WriteByte([byte] ($originalByte -bxor 0xff))
        $tamperedStream.Flush($true)
    }
    finally {
        $tamperedStream.Dispose()
    }
    $digestRejected = $false
    try {
        Assert-RecoveryDigest -Path $tamperedPath -ExpectedSha256 $dumpSha256
    }
    catch {
        $digestRejected = $true
    }
    Assert-Condition $digestRejected `
        "A byte-flipped recovery archive passed the recorded SHA-256 gate."

    $truncatedPath = Join-Path $recoveryRoot "truncated-rehashed.dump"
    [IO.File]::Copy($dumpPath, $truncatedPath, $false)
    $truncatedStream = [IO.File]::Open($truncatedPath, [IO.FileMode]::Open, [IO.FileAccess]::Write)
    try {
        $truncatedStream.SetLength([Math]::Max(1, [Math]::Floor($truncatedStream.Length * 0.6)))
        $truncatedStream.Flush($true)
    }
    finally {
        $truncatedStream.Dispose()
    }
    $truncatedSha256 = (Get-FileHash -LiteralPath $truncatedPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Assert-Condition ($truncatedSha256 -ne $dumpSha256) `
        "The truncated archive unexpectedly retained the original digest."
    Assert-RecoveryDigest -Path $truncatedPath -ExpectedSha256 $truncatedSha256

    $ownerMarker = "ADR-047:${RunId}:$(Get-RandomLowerHex -ByteCount 8)"
    $targetVolume = "wsr-recovery-volume-$RunId"
    $targetContainer = "wsr-recovery-postgres-$RunId"
    $targetContainerId = $null
    $volumeOwned = $false
    $volumeWasOwned = $false
    $containerOwned = $false
    $containerWasOwned = $false
    $recoveryFailure = $null
    $cleanupFailure = $null
    try {
        $existingVolume = Invoke-DockerProcess -Arguments @("volume", "inspect", $targetVolume)
        Assert-Condition ($existingVolume.ExitCode -ne 0) `
            "The random recovery volume name already exists."
        $existingContainer = Invoke-DockerProcess -Arguments @("container", "inspect", $targetContainer)
        Assert-Condition ($existingContainer.ExitCode -ne 0) `
            "The random recovery container name already exists."

        $createdVolumeOutput = Invoke-DockerCommand -Arguments @(
            "volume", "create",
            "--label", "com.wallstreetreceipts.role=recovery-rehearsal-volume",
            "--label", "com.wallstreetreceipts.run-id=$ownerMarker",
            $targetVolume
        ) -Capture
        $volumeOwned = $true
        $volumeWasOwned = $true
        Assert-Condition (((@($createdVolumeOutput) -join "").Trim()) -eq $targetVolume) `
            "Docker did not return the exact owned recovery volume name."
        $createdVolumeJson = Invoke-DockerCommand -Arguments @(
            "volume", "inspect", $targetVolume
        ) -Capture
        $createdVolume = @(((@($createdVolumeJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
        Assert-Condition (
            $createdVolume.Name -eq $targetVolume -and
            $createdVolume.Labels["com.wallstreetreceipts.run-id"] -eq $ownerMarker -and
            $createdVolume.Labels["com.wallstreetreceipts.role"] -eq
                "recovery-rehearsal-volume"
        ) "The created recovery volume lost its exact name or ownership labels."

        $containerCreate = Invoke-DockerProcess -Arguments @(
            "container", "create", "--pull", "never", "--name", $targetContainer,
            "--label", "com.wallstreetreceipts.role=recovery-rehearsal-database",
            "--label", "com.wallstreetreceipts.run-id=$ownerMarker",
            "--network", "none",
            "--mount", "type=volume,source=$targetVolume,target=/var/lib/postgresql/data",
            "--env", "POSTGRES_DB=wsr",
            "--env", "POSTGRES_USER=wsr",
            "--env", "POSTGRES_HOST_AUTH_METHOD=trust",
            "--health-cmd", "pg_isready -U wsr -d wsr",
            "--health-interval", "2s",
            "--health-timeout", "3s",
            "--health-retries", "60",
            "--health-start-period", "5s",
            "--memory", "1g", "--cpus", "1", "--pids-limit", "256",
            $restorePostgresImageId
        )
        Assert-Condition ($containerCreate.ExitCode -eq 0) `
            "Docker failed to create the isolated recovery container."
        $targetContainerId = ((@($containerCreate.Output) -join "").Trim())
        $containerOwned = $true
        $containerWasOwned = $true
        Assert-Condition ($targetContainerId -cmatch '^[0-9a-f]{64}$') `
            "Docker did not return the exact recovery container ID."
        $createdContainerJson = Invoke-DockerCommand -Arguments @(
            "container", "inspect", $targetContainerId
        ) -Capture
        $createdContainer = @(((@($createdContainerJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
        Assert-ExactRecoveryRuntime `
            -Container $createdContainer `
            -ExpectedContainerId $targetContainerId `
            -ExpectedContainerName $targetContainer `
            -ExpectedOwnerMarker $ownerMarker `
            -ExpectedImageId $restorePostgresImageId `
            -ExpectedTargetVolume $targetVolume `
            -ExpectedSourceVolume $ExpectedSourceVolume
        Invoke-DockerCommand -Arguments @(
            "container", "start", $targetContainerId
        ) | Out-Null

        $healthy = $false
        $attemptLimit = [Math]::Max(60, $StartupTimeoutSeconds)
        for ($attempt = 1; $attempt -le $attemptLimit; $attempt++) {
            $health = Invoke-DockerCommand -Arguments @(
                "inspect", "--format", "{{if .State.Health}}{{.State.Health.Status}}{{end}}",
                $targetContainerId
            ) -Capture
            $healthText = ((@($health) -join "").Trim())
            if ($healthText -eq "healthy") {
                $healthy = $true
                break
            }
            Assert-Condition ($healthText -ne "unhealthy") `
                "The fresh recovery target became unhealthy."
            Start-Sleep -Seconds 1
        }
        Assert-Condition $healthy "The fresh recovery target did not become healthy in time."

        $targetJson = Invoke-DockerCommand -Arguments @("inspect", $targetContainerId) -Capture
        $target = @(((@($targetJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
        Assert-ExactRecoveryRuntime `
            -Container $target `
            -ExpectedContainerId $targetContainerId `
            -ExpectedContainerName $targetContainer `
            -ExpectedOwnerMarker $ownerMarker `
            -ExpectedImageId $restorePostgresImageId `
            -ExpectedTargetVolume $targetVolume `
            -ExpectedSourceVolume $ExpectedSourceVolume

        $preRestoreTableCount = Invoke-DockerCommand -Arguments @(
            "exec", $targetContainerId,
            "psql", "--username=wsr", "--dbname=wsr", "--no-password", "--tuples-only", "--no-align",
            "--command", "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"
        ) -Capture
        Assert-Condition ((@($preRestoreTableCount) -join "").Trim() -eq "0") `
            "The recovery target was not empty before restore."

        $corruptRestore = Invoke-DockerInputFileProcess -InputPath $truncatedPath -Arguments @(
            "exec", "--interactive", $targetContainerId,
            "pg_restore", "--username=wsr", "--dbname=wsr", "--no-password",
            "--single-transaction", "--exit-on-error", "--no-owner", "--no-privileges"
        )
        Assert-Condition ($corruptRestore.ExitCode -ne 0) `
            "A truncated archive with a recomputed digest completed a full restore."
        $postFailureTableCount = Invoke-DockerCommand -Arguments @(
            "exec", $targetContainerId,
            "psql", "--username=wsr", "--dbname=wsr", "--no-password", "--tuples-only", "--no-align",
            "--command", "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"
        ) -Capture
        Assert-Condition ((@($postFailureTableCount) -join "").Trim() -eq "0") `
            "The failed single-transaction restore left application tables behind."

        Assert-RecoveryDigest -Path $dumpPath -ExpectedSha256 $dumpSha256
        $restore = Invoke-DockerInputFileProcess -InputPath $dumpPath -Arguments @(
            "exec", "--interactive", $targetContainerId,
            "pg_restore", "--username=wsr", "--dbname=wsr", "--no-password",
            "--single-transaction", "--exit-on-error", "--no-owner", "--no-privileges"
        )
        Assert-Condition ($restore.ExitCode -eq 0) `
            "The complete recovery archive failed to restore: $($restore.Stderr)"

        $databaseEvidence = Invoke-DockerInputFileProcess -InputPath $databaseEvidenceSql -Arguments @(
            "exec", "--interactive", $targetContainerId,
            "psql", "-X", "-q", "-A", "-t",
            "--username=wsr", "--dbname=wsr", "--no-password", "--no-psqlrc",
            "--set", "ON_ERROR_STOP=1", "--file", "-"
        )
        Assert-Condition ($databaseEvidence.ExitCode -eq 0) `
            "Restored database evidence query failed: $($databaseEvidence.Stderr)"
        Assert-Condition (-not [string]::IsNullOrWhiteSpace($databaseEvidence.Stdout)) `
            "Restored database evidence query returned no evidence."

        $databaseEvidenceLines = @(
            $databaseEvidence.Stdout -split "`r?`n" |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )
        Assert-Condition (
            $databaseEvidenceLines.Count -gt 0 -and
            $databaseEvidenceLines[0] -ceq "evidence_version|2"
        ) "Restored database evidence must use the exact ADR-048 v2 contract."
        $flywayEvidenceLines = @(
            $databaseEvidenceLines | Where-Object { $_ -clike "flyway|*" }
        )
        Assert-Condition ($flywayEvidenceLines.Count -eq 9) `
            "The v2 database evidence must contain exactly nine Flyway rows."
        foreach ($line in $flywayEvidenceLines) {
            Assert-Condition (($line -csplit '\|').Count -eq 8) `
                "Every v2 Flyway evidence row must bind rank/version/description/type/script/checksum/success."
        }

        Assert-Condition ($null -ne $apiContainerId) `
            "The exact source API container identity was not captured."
        $apiInventory = Invoke-DockerCommand -Arguments @(
            "exec", $apiContainerId,
            "env", "-u", "JAVA_TOOL_OPTIONS", "-u", "JDK_JAVA_OPTIONS", "-u", "_JAVA_OPTIONS",
            "java", "-jar", "/opt/wsr/application.jar",
            "--wsr-release-schema-inventory"
        ) -Capture
        $apiInventoryLines = @(
            ((@($apiInventory) -join "`n").Trim()) -split "`r?`n" |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )
        Assert-Condition (
            $apiInventoryLines.Count -eq 11 -and
            $apiInventoryLines[0] -ceq "inventory_version|1" -and
            $apiInventoryLines[1] -ceq "flyway_version|11.7.2"
        ) "The exact API image did not emit the canonical V1-V9 Flyway inventory."
        $apiMigrationLines = @($apiInventoryLines | Select-Object -Skip 2)
        for ($index = 0; $index -lt 9; $index++) {
            $imageFields = @($apiMigrationLines[$index] -csplit '\|')
            $databaseFields = @($flywayEvidenceLines[$index] -csplit '\|')
            Assert-Condition (
                $imageFields.Count -eq 10 -and
                $imageFields[0] -ceq "migration" -and
                $databaseFields.Count -eq 8 -and
                $databaseFields[0] -ceq "flyway" -and
                $imageFields[1] -ceq $databaseFields[1] -and
                $imageFields[2] -ceq $databaseFields[2] -and
                $imageFields[3] -ceq $databaseFields[3] -and
                $imageFields[4] -ceq $databaseFields[4] -and
                $imageFields[5] -ceq $databaseFields[5] -and
                $imageFields[6] -ceq $databaseFields[6] -and
                $databaseFields[7] -ceq "true"
            ) "The exact API image inventory differs from restored Flyway evidence at row $($index + 1)."
        }

        $restoredCounts = Invoke-DockerCommand -Arguments @(
            "exec", $targetContainerId,
            "psql", "--username=wsr", "--dbname=wsr", "--no-password", "--tuples-only", "--no-align",
            "--command", "SELECT (SELECT count(*) FROM analyst_calls) || '|' || (SELECT count(*) FROM analyst_call_revisions) || '|' || (SELECT count(*) FROM call_outcomes);"
        ) -Capture
        Assert-Condition ((@($restoredCounts) -join "").Trim() -eq "3|2|4") `
            "The restored fixture evidence must remain exactly 3|2|4."
        $restoredFlyway = Invoke-DockerCommand -Arguments @(
            "exec", $targetContainerId,
            "psql", "--username=wsr", "--dbname=wsr", "--no-password", "--tuples-only", "--no-align",
            "--command", "SELECT count(*) || '|' || max(installed_rank) || '|' || CASE WHEN bool_and(success) THEN 'true' ELSE 'false' END FROM flyway_schema_history;"
        ) -Capture
        Assert-Condition ((@($restoredFlyway) -join "").Trim() -eq "9|9|true") `
            "The restored Flyway evidence must contain nine ordered successful migrations."

        $evidenceRuntimeJson = Invoke-DockerCommand -Arguments @(
            "inspect", $targetContainerId
        ) -Capture
        $evidenceRuntime = @(
            ((@($evidenceRuntimeJson) -join [Environment]::NewLine) |
                ConvertFrom-Json -AsHashtable)
        )[0]
        Assert-ExactRecoveryRuntime `
            -Container $evidenceRuntime `
            -ExpectedContainerId $targetContainerId `
            -ExpectedContainerName $targetContainer `
            -ExpectedOwnerMarker $ownerMarker `
            -ExpectedImageId $restorePostgresImageId `
            -ExpectedTargetVolume $targetVolume `
            -ExpectedSourceVolume $ExpectedSourceVolume

        $evidenceRoot = Join-Path $recoveryRoot "restore-evidence"
        [IO.Directory]::CreateDirectory($evidenceRoot) | Out-Null
        $evidencePath = Join-Path $evidenceRoot "$backupId.restore.json"
        $databaseEvidencePath = Join-Path $evidenceRoot "$backupId.database.txt"
        [IO.File]::WriteAllText(
            $databaseEvidencePath,
            $databaseEvidence.Stdout,
            [Text.UTF8Encoding]::new($false)
        )
        $databaseEvidenceSha256 = (
            Get-FileHash -LiteralPath $databaseEvidencePath -Algorithm SHA256
        ).Hash.ToLowerInvariant()
        $restoreEvidence = [ordered]@{
            schema_version = 1
            backup_id = $backupId
            manifest_sha256 = $manifestSha256
            dump_sha256 = $dumpSha256
            source_git_sha = $validatedManifest["git_sha"]
            runtime_images = $runtimeFacts
            restore = [ordered]@{
                started_with_empty_public_table_count = 0
                options = @(
                    "--single-transaction", "--exit-on-error",
                    "--no-owner", "--no-privileges", "--no-password"
                )
                network_mode = "none"
                published_ports = 0
                target_container = $targetContainer
                target_container_id = $targetContainerId
                target_volume = $targetVolume
            }
            database_evidence_file = "$backupId.database.txt"
            database_evidence_sha256 = $databaseEvidenceSha256
            database_evidence_version = 2
            restored_fixture_counts = "3|2|4"
            restored_flyway = "9|9|true"
            image_evidence_ready = $true
            schema_compatibility = "compatible-exact-api-image-flyway-local-only"
            completed_at_utc = [DateTime]::UtcNow.ToString("o")
            pending = @("PENDING_BACKUP_DEVICE", "PENDING_OFFSITE_COPY")
        }
        [IO.File]::WriteAllText(
            $evidencePath,
            ($restoreEvidence | ConvertTo-Json -Depth 12) + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false)
        )
        Assert-Condition ((Get-Item -LiteralPath $evidencePath).Length -gt 0) `
            "Restore evidence was not written."
    }
    catch {
        $recoveryFailure = $_
    }
    finally {
        if ($containerOwned) {
            try {
                Assert-Condition (
                    $null -ne $targetContainerId -and
                    $targetContainerId -cmatch '^[0-9a-f]{64}$'
                ) "Refused to remove a recovery container without its exact recorded ID."
                $ownedContainerJson = Invoke-DockerCommand -Arguments @(
                    "container", "inspect", $targetContainerId
                ) -Capture
                $ownedContainer = @(((@($ownedContainerJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
                Assert-Condition (
                    $ownedContainer.Id -eq $targetContainerId -and
                    $ownedContainer.Name -eq "/$targetContainer" -and
                    $ownedContainer.Config.Labels["com.wallstreetreceipts.run-id"] -eq
                        $ownerMarker -and
                    $ownedContainer.Config.Labels["com.wallstreetreceipts.role"] -eq
                        "recovery-rehearsal-database"
                ) "Refused to remove a recovery container with a mismatched ID, name, run label, or role label."
                Invoke-DockerCommand -Arguments @(
                    "container", "rm", "--force", "--volumes", $targetContainerId
                ) | Out-Null
                $containerOwned = $false
            }
            catch {
                if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } else { Write-Warning $_ }
            }
        }
        if ($volumeOwned -and -not $containerOwned) {
            try {
                $ownedVolumeJson = Invoke-DockerCommand -Arguments @(
                    "volume", "inspect", $targetVolume
                ) -Capture
                $ownedVolume = @(((@($ownedVolumeJson) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable))[0]
                Assert-Condition (
                    $ownedVolume.Name -eq $targetVolume -and
                    $ownedVolume.Labels["com.wallstreetreceipts.run-id"] -eq
                        $ownerMarker -and
                    $ownedVolume.Labels["com.wallstreetreceipts.role"] -eq
                        "recovery-rehearsal-volume"
                ) "Refused to remove a recovery volume with a mismatched name, run label, or role label."
                Invoke-DockerCommand -Arguments @("volume", "rm", $targetVolume) | Out-Null
                $volumeOwned = $false
            }
            catch {
                if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } else { Write-Warning $_ }
            }
        }

        # A successful rm exit is not enough. Ask the same pinned daemon for a
        # complete current inventory and prove that neither the exact recorded
        # ID/name nor the exact volume name remains. These proofs also run when
        # restore, start, health, ownership, or cleanup itself failed.
        if ($containerWasOwned) {
            try {
                $containerRows = Invoke-DockerCommand -Arguments @(
                    "container", "ls", "--all", "--no-trunc",
                    "--format", "{{.ID}} {{.Names}}"
                ) -Capture
                $remainingContainers = @(
                    $containerRows | Where-Object {
                        $row = ([string] $_).Trim()
                        $idMatches =
                            -not [string]::IsNullOrWhiteSpace($targetContainerId) -and
                            $row.StartsWith("$targetContainerId ", [StringComparison]::Ordinal)
                        $nameMatches = $row.EndsWith(" $targetContainer", [StringComparison]::Ordinal)
                        $idMatches -or $nameMatches
                    }
                )
                Assert-Condition ($remainingContainers.Count -eq 0) `
                    "The exact owned recovery container still exists after cleanup."
            }
            catch {
                if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } else { Write-Warning $_ }
            }
        }
        if ($volumeWasOwned) {
            try {
                $volumeRows = Invoke-DockerCommand -Arguments @(
                    "volume", "ls", "--format", "{{.Name}}"
                ) -Capture
                $remainingVolumes = @(
                    $volumeRows | Where-Object { ([string] $_).Trim() -ceq $targetVolume }
                )
                Assert-Condition ($remainingVolumes.Count -eq 0) `
                    "The exact owned recovery volume still exists after cleanup."
            }
            catch {
                if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } else { Write-Warning $_ }
            }
        }
    }
    if ($null -ne $cleanupFailure) {
        if ($null -ne $recoveryFailure) {
            throw "Recovery rehearsal failed, and exact cleanup could not be proven. Recovery: $recoveryFailure Cleanup: $cleanupFailure"
        }
        throw $cleanupFailure
    }
    if ($null -ne $recoveryFailure) {
        throw $recoveryFailure
    }
    Assert-Condition (-not $containerOwned -and -not $volumeOwned) `
        "Owned recovery resources were not cleaned up."
}

function Get-RandomLowerHex {
    param([ValidateRange(4, 32)][int] $ByteCount)

    $bytes = [byte[]]::new($ByteCount)
    try {
        [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        return [Convert]::ToHexString($bytes).ToLowerInvariant()
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-MinimumComposeVersion {
    param([Parameter(Mandatory)][string] $Version)

    $match = [regex]::Match($Version.Trim(), '^v?(?<major>\d+)\.(?<minor>\d+)(?:\.(?<patch>\d+))?')
    Assert-Condition $match.Success "Docker Compose returned an unreadable version."
    $major = [int] $match.Groups['major'].Value
    $minor = [int] $match.Groups['minor'].Value
    Assert-Condition ($major -gt 2 -or ($major -eq 2 -and $minor -ge 20)) `
        "Docker Compose 2.20.0 or newer is required."
}

function Remove-OwnedTemporaryDirectory {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string] $ExpectedBase,
        [Parameter(Mandatory)][string] $ExpectedLeaf,
        [Parameter(Mandatory)][string] $MarkerPath,
        [Parameter(Mandatory)][string] $MarkerValue
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }
    $item = Get-Item -LiteralPath $Path -Force
    Assert-Condition $item.PSIsContainer "Refused to remove a temporary path that is not a directory."
    Assert-Condition (-not $item.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) `
        "Refused to remove a reparse-point rehearsal directory."
    $resolvedPath = [IO.Path]::GetFullPath($item.FullName)
    $comparison = if ($IsWindows) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    Assert-Condition ([IO.Path]::GetFileName($resolvedPath) -eq $ExpectedLeaf) `
        "Refused to remove an unexpected rehearsal directory name."
    Assert-Condition (
        [IO.Path]::GetDirectoryName($resolvedPath).TrimEnd('\', '/').Equals(
            $ExpectedBase.TrimEnd('\', '/'),
            $comparison
        )
    ) "Refused to remove a rehearsal directory outside the operating-system temp root."
    $marker = Get-Item -LiteralPath $MarkerPath -Force
    Assert-Condition (-not $marker.PSIsContainer) "The rehearsal ownership marker is not a file."
    Assert-Condition (-not $marker.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint)) `
        "Refused to trust a reparse-point rehearsal ownership marker."
    Assert-Condition ((Get-Content -LiteralPath $MarkerPath -Raw) -eq $MarkerValue) `
        "The rehearsal ownership marker does not match this run."
    [IO.Directory]::Delete($resolvedPath, $true)
}

function Get-AvailableRehearsalPort {
    foreach ($candidatePort in 18080..18179) {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $candidatePort)
        try {
            $listener.Start()
            return $candidatePort
        }
        catch [Net.Sockets.SocketException] {
            continue
        }
        finally {
            $listener.Stop()
        }
    }
    throw "No available IPv4 loopback port was found in the rehearsal range."
}

function Get-HttpResponse {
    param(
        [Parameter(Mandatory)]
        [Net.Http.HttpClient] $Client,

        [Parameter(Mandatory)]
        [Net.Http.HttpMethod] $Method,

        [Parameter(Mandatory)]
        [string] $Path
    )

    for ($attempt = 1; $attempt -le 20; $attempt++) {
        $request = [Net.Http.HttpRequestMessage]::new($Method, $Path)
        try {
            if ($Method -eq [Net.Http.HttpMethod]::Post) {
                $request.Content = [Net.Http.StringContent]::new("")
            }
            $response = $Client.Send($request)
            try {
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                $headers = @{}
                foreach ($header in $response.Headers) {
                    $headers[$header.Key] = @($header.Value)
                }
                return [pscustomobject]@{
                    StatusCode = [int] $response.StatusCode
                    ContentType = $response.Content.Headers.ContentType.MediaType
                    Body = $body
                    Headers = $headers
                }
            }
            finally {
                $response.Dispose()
            }
        }
        catch [Net.Http.HttpRequestException] {
            if ($attempt -eq 20) {
                throw
            }
            Start-Sleep -Milliseconds 250
        }
        finally {
            $request.Dispose()
        }
    }
}

Assert-Condition ($PSVersionTable.PSVersion.Major -ge 7) `
    "PowerShell 7 or newer is required."

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$script:ComposeFile = Join-Path $repoRoot "deploy/home-server/compose.yaml"
$script:ComposeOverlayFile = $null
$nextEnvPath = Join-Path $repoRoot "apps/web/next-env.d.ts"
Assert-Condition (Test-Path -LiteralPath $script:ComposeFile -PathType Leaf) `
    "Home-server Compose file is missing."

$nextEnvHashBefore = $null
if (Test-Path -LiteralPath $nextEnvPath -PathType Leaf) {
    $nextEnvHashBefore = (Get-FileHash -LiteralPath $nextEnvPath -Algorithm SHA256).Hash
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
Assert-Condition ($null -ne $dockerCommand) "Docker is required and must be on PATH."

$script:DockerCommand = $dockerCommand.Source
$selectedEndpoint = Get-SelectedDockerEndpoint -DockerCommand $script:DockerCommand
Assert-LocalDockerEndpoint -Endpoint $selectedEndpoint
$script:DockerEnvironment = New-ProcessEnvironmentMap
foreach ($pattern in @(
    '^(?i:DOCKER_)',
    '^(?i:COMPOSE_)',
    '^(?i:WSR_)',
    '^(?i:(?:HTTP|HTTPS|ALL|NO)_PROXY)$'
)) {
    Add-InheritedEnvironmentRemovals -Variables $script:DockerEnvironment -NamePattern $pattern
}
$script:DockerEnvironment['DOCKER_CONTEXT'] = $null
$script:DockerEnvironment['DOCKER_HOST'] = $selectedEndpoint
$script:DockerEnvironment['DOCKER_TLS_VERIFY'] = $null
$script:DockerEnvironment['DOCKER_CERT_PATH'] = $null

Invoke-DockerCommand -Arguments @("info") | Out-Null
$composeVersion = Invoke-DockerCommand -Arguments @("compose", "version", "--short") -Capture
Assert-MinimumComposeVersion -Version ((@($composeVersion) -join "").Trim())

$runId = Get-RandomLowerHex -ByteCount 8
$script:ProjectName = "wsr-home-$runId"
$imageTag = "rehearsal-$runId"
$apiImage = "wall-street-receipts-api:$imageTag"
$webImage = "wall-street-receipts-web:$imageTag"
$caddyImage = "wall-street-receipts-caddy:$imageTag"
$requestedPort = Get-AvailableRehearsalPort
$publishedPort = $null
$temporaryBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$temporaryLeaf = "wsr-home-rehearsal-$runId"
$temporaryRoot = [IO.Path]::Combine($temporaryBase, $temporaryLeaf)
$temporaryRootOwned = $false
$ownershipMarkerPath = Join-Path $temporaryRoot ".wsr-owner"
$ownershipMarkerValue = "ADR-046:$runId"
$secretPath = Join-Path $temporaryRoot "postgres_password"
$script:EnvFile = Join-Path $temporaryRoot "compose.env"
$recoveryGitSha = $null
$stackStarted = $false
$apiImageOwned = $false
$webImageOwned = $false
$caddyImageOwned = $false
$apiImageOwnedId = $null
$webImageOwnedId = $null
$caddyImageOwnedId = $null
$httpClient = $null
$failure = $null

try {
    New-Item -ItemType Directory -Path $temporaryRoot -ErrorAction Stop | Out-Null
    $temporaryRootOwned = $true
    [IO.File]::WriteAllText(
        $ownershipMarkerPath,
        $ownershipMarkerValue,
        [Text.UTF8Encoding]::new($false)
    )

    if ($RunRecoverySuite) {
        $recoveryGitSha = Get-RecoverySourceGitSha -RepositoryRoot $repoRoot
        $imageTag = $recoveryGitSha
        $apiImage = "wall-street-receipts-api:$imageTag"
        $webImage = "wall-street-receipts-web:$imageTag"
        $caddyImage = "wall-street-receipts-caddy:$imageTag"
        $script:ComposeOverlayFile = Join-Path $temporaryRoot "recovery-build-override.yaml"
        $overlayContents = @(
            "services:",
            "  api:",
            "    build:",
            "      args:",
            "        WSR_GIT_SHA: $recoveryGitSha",
            "  web:",
            "    build:",
            "      args:",
            "        WSR_GIT_SHA: $recoveryGitSha",
            "  caddy-rehearsal:",
            "    build:",
            "      args:",
            "        WSR_GIT_SHA: $recoveryGitSha"
        ) -join [Environment]::NewLine
        [IO.File]::WriteAllText(
            $script:ComposeOverlayFile,
            $overlayContents + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false)
        )
    }

    foreach ($candidateImage in @($apiImage, $webImage, $caddyImage)) {
        $inspection = Invoke-DockerProcess -Arguments @("image", "inspect", $candidateImage)
        Assert-Condition ($inspection.ExitCode -ne 0) `
            "The random rehearsal image tag unexpectedly exists: $candidateImage"
    }

    $secretBytes = [byte[]]::new(32)
    try {
        [Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
        $secretText = [Convert]::ToHexString($secretBytes).ToLowerInvariant()
        [IO.File]::WriteAllText(
            $secretPath,
            $secretText + [Environment]::NewLine,
            [Text.UTF8Encoding]::new($false)
        )
    }
    finally {
        [Array]::Clear($secretBytes, 0, $secretBytes.Length)
        $secretText = $null
    }

    $portableSecretPath = $secretPath.Replace('\', '/')
    $envContents = @(
        "WSR_DOMAIN=wsr.invalid",
        "WSR_ACME_EMAIL=operator@wsr.invalid",
        "WSR_IMAGE_TAG=$imageTag",
        "WSR_POSTGRES_PASSWORD_FILE=$portableSecretPath",
        "WSR_REHEARSAL_PORT=$requestedPort",
        "WSR_INGRESS_MODE=unknown",
        "WSR_PUBLIC_IP_POLICY=unknown"
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText(
        $script:EnvFile,
        $envContents + [Environment]::NewLine,
        [Text.UTF8Encoding]::new($false)
    )

    Write-Host "[1/6] Validating the rehearsal Compose model..."
    $renderedLines = Invoke-ComposeCommand -Arguments @(
        "--profile", "rehearsal", "config", "--format", "json"
    ) -Capture
    $rendered = ((@($renderedLines) -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable)
    Assert-Condition (
        @($rendered.services.Keys).Count -eq 4 -and
        @($rendered.services.Keys | Where-Object { $_ -notin @("postgres", "api", "web", "caddy-rehearsal") }).Count -eq 0
    ) "The effective rehearsal service set was overridden."
    Assert-Condition ($rendered.services.api.image -eq $apiImage) `
        "The effective API image tag was overridden."
    Assert-Condition ($rendered.services.web.image -eq $webImage) `
        "The effective web image tag was overridden."
    Assert-Condition ($rendered.services."caddy-rehearsal".image -eq $caddyImage) `
        "The effective Caddy image tag was overridden."
    Assert-Condition (
        $rendered.services."caddy-rehearsal".environment.WSR_DOMAIN -eq "https://127.0.0.1:8443" -and
        $rendered.services."caddy-rehearsal".environment.WSR_DEFAULT_SNI -eq "127.0.0.1"
    ) "The private-TLS rehearsal site or default SNI was overridden."
    $renderedPort = @($rendered.services."caddy-rehearsal".ports)[0]
    Assert-Condition (
        $renderedPort.host_ip -eq "127.0.0.1" -and
        [int] $renderedPort.published -eq $requestedPort
    ) "The effective loopback rehearsal port was overridden."
    Assert-Condition (
        [IO.Path]::GetFullPath($rendered.secrets.postgres_password.file) -eq
            [IO.Path]::GetFullPath($secretPath)
    ) "The effective PostgreSQL secret path was overridden."
    if ($RunRecoverySuite) {
        foreach ($serviceName in @("api", "web", "caddy-rehearsal")) {
            Assert-Condition (
                $rendered.services[$serviceName].build.args.WSR_GIT_SHA -eq $recoveryGitSha
            ) "The $serviceName recovery image revision was not pinned to Git HEAD."
        }
    }

    Write-Host "[2/6] Building isolated API, web, and Caddy runtime images..."
    $apiImageOwned = $true
    $webImageOwned = $true
    $caddyImageOwned = $true
    Invoke-ComposeCommand -Arguments @(
        "--profile", "rehearsal", "build", "--pull", "api", "web", "caddy-rehearsal"
    )
    $apiImageOwnedId = Get-ExactDockerImageTagId -ImageReference $apiImage
    $webImageOwnedId = Get-ExactDockerImageTagId -ImageReference $webImage
    $caddyImageOwnedId = Get-ExactDockerImageTagId -ImageReference $caddyImage
    Assert-Condition (
        -not [string]::IsNullOrWhiteSpace($apiImageOwnedId) -and
        -not [string]::IsNullOrWhiteSpace($webImageOwnedId) -and
        -not [string]::IsNullOrWhiteSpace($caddyImageOwnedId)
    ) "Docker did not publish all three exact rehearsal image tags after build."
    Invoke-DockerCommand -Arguments @(
        "run", "--rm", "--network", "none", "--read-only",
        "--security-opt", "no-new-privileges:true", "--cap-drop", "ALL",
        "--mount", "type=volume,destination=/data",
        "--mount", "type=volume,destination=/config",
        "--entrypoint", "/bin/sh", $caddyImage,
        "-c", "test -w /data && test -w /config && touch /data/.write-probe /config/.write-probe"
    ) | Out-Null

    Write-Host "[3/6] Starting the loopback-only rehearsal profile..."
    $stackStarted = $true
    Invoke-ComposeCommand -Arguments @(
        "--profile", "rehearsal",
        "up", "--detach", "--wait", "--wait-timeout", "$StartupTimeoutSeconds",
        "postgres", "api", "web", "caddy-rehearsal"
    )
    $caddyContainerId = Invoke-ComposeCommand -Arguments @("ps", "--quiet", "caddy-rehearsal") -Capture
    $caddyContainerIdText = (@($caddyContainerId) -join "").Trim()
    Assert-Condition ($caddyContainerIdText -match '^[0-9a-f]{12,64}$') `
        "Unable to resolve the exact rehearsal Caddy container."
    $portJson = Invoke-DockerCommand -Arguments @(
        "inspect", "--format", "{{json .HostConfig.PortBindings}}", $caddyContainerIdText
    ) -Capture
    $networkPorts = ((@($portJson) -join "").Trim() | ConvertFrom-Json -AsHashtable)
    $caddyBindings = @($networkPorts["8443/tcp"])
    Assert-Condition ($caddyBindings.Count -eq 1) `
        "Rehearsal Caddy must publish exactly one runtime port binding."
    $caddyBinding = $caddyBindings[0]
    Assert-Condition ($caddyBinding["HostIp"] -eq "127.0.0.1") `
        "Rehearsal Caddy runtime binding escaped IPv4 loopback."
    Assert-Condition ($caddyBinding["HostPort"] -match '^\d+$') `
        "Docker did not allocate a numeric rehearsal port."
    $publishedPort = [int] $caddyBinding["HostPort"]
    Assert-Condition ($publishedPort -ge 1 -and $publishedPort -le 65535) `
        "Docker allocated an invalid rehearsal port."
    Assert-Condition ($publishedPort -eq $requestedPort) `
        "Docker did not retain the preflighted rehearsal port."

    Write-Host "[4/6] Verifying runtime port isolation and database evidence..."
    foreach ($service in @("postgres", "api", "web")) {
        $containerId = Invoke-ComposeCommand -Arguments @("ps", "--quiet", $service) -Capture
        $containerIdText = (@($containerId) -join "").Trim()
        Assert-Condition ($containerIdText -match '^[0-9a-f]{12,64}$') `
            "Unable to resolve the exact $service container."
        $bindings = Invoke-DockerCommand -Arguments @(
            "inspect", "--format", "{{json .HostConfig.PortBindings}}", $containerIdText
        ) -Capture
        $bindingText = (@($bindings) -join "").Trim()
        Assert-Condition ($bindingText -eq "{}" -or $bindingText -eq "null") `
            "$service unexpectedly publishes a host port."
    }

    $counts = Invoke-ComposeCommand -Arguments @(
        "exec", "--no-TTY", "postgres",
        "psql", "--username", "wsr", "--dbname", "wsr", "--tuples-only", "--no-align",
        "--command", "SELECT (SELECT count(*) FROM analyst_calls) || '|' || (SELECT count(*) FROM analyst_call_revisions) || '|' || (SELECT count(*) FROM call_outcomes);"
    ) -Capture
    Assert-Condition ((@($counts) -join "").Trim() -eq "3|2|4") `
        "The fixture-backed PostgreSQL evidence must remain exactly 3|2|4."

    if ($RunRecoverySuite) {
        Write-Host "[recovery] Capturing, corrupting, restoring, and evidencing a custom PostgreSQL archive..."
        Invoke-RecoveryDatabaseRehearsal `
            -RepositoryRoot $repoRoot `
            -TemporaryRoot $temporaryRoot `
            -RunId $runId `
            -GitSha $recoveryGitSha `
            -ExpectedSourceVolume $rendered.volumes."postgres-data".name
        Write-Host "[recovery] PASS: partial publication, digest rejection, full-restore rejection, fresh-volume restore, Flyway evidence, and exact cleanup."
    }

    Write-Host "[5/6] Exercising all public routes through rehearsal Caddy..."
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $handler.AllowAutoRedirect = $false
    # The client base URI below is a fixed numeric-loopback endpoint. Its
    # ephemeral Caddy local-CA root is intentionally not installed on the host.
    $handler.ServerCertificateCustomValidationCallback =
        [Net.Http.HttpClientHandler]::DangerousAcceptAnyServerCertificateValidator
    $httpClient = [Net.Http.HttpClient]::new($handler, $true)
    $httpClient.BaseAddress = [Uri]::new("https://127.0.0.1:$publishedPort")
    $httpClient.Timeout = [TimeSpan]::FromSeconds(30)
    $routes = @(
        "/",
        "/market",
        "/calls",
        "/calls/demo-call-001",
        "/calls/demo-call-002",
        "/institutions",
        "/analysts",
        "/maps/sp500",
        "/maps/nasdaq100",
        "/markets/sp500",
        "/screener",
        "/methodology"
    )
    $homeResponse = $null
    $detailResponse = $null
    foreach ($route in $routes) {
        $response = Get-HttpResponse -Client $httpClient -Method ([Net.Http.HttpMethod]::Get) -Path $route
        Assert-Condition ($response.StatusCode -eq 200) "$route returned HTTP $($response.StatusCode)."
        Assert-Condition ($response.ContentType -eq "text/html") "$route did not return text/html."
        Assert-Condition ($response.Body.Contains("WALL STREET RECEIPTS")) `
            "$route did not render the shared product shell."
        if ($route -eq "/") {
            $homeResponse = $response
        }
        if ($route -eq "/calls/demo-call-001") {
            $detailResponse = $response
        }
    }
    Assert-Condition ($null -ne $homeResponse) "The home response was not captured."
    Assert-Condition ($null -ne $detailResponse) "The call-detail response was not captured."
    Assert-Condition ($homeResponse.Body.Contains("DEMO")) `
        "The public home page did not disclose DEMO mode."
    Assert-Condition ($detailResponse.Body.Contains("DEMO index outlook")) `
        "The API-backed call detail lost its fixture source provenance."
    Assert-Condition ($detailResponse.Body.Contains("2026")) `
        "The API-backed call detail lost its point-in-time timestamp surface."
    Assert-Condition ($homeResponse.Headers.ContainsKey("X-Content-Type-Options")) `
        "Caddy did not add X-Content-Type-Options."
    $nosniffValues = $homeResponse.Headers["X-Content-Type-Options"]
    Assert-Condition ($nosniffValues -contains "nosniff") `
        "X-Content-Type-Options did not retain nosniff."
    Assert-Condition ($homeResponse.Headers["X-Frame-Options"] -contains "DENY") `
        "X-Frame-Options did not retain DENY."
    Assert-Condition ($homeResponse.Headers["Referrer-Policy"] -contains "strict-origin-when-cross-origin") `
        "Referrer-Policy changed."
    Assert-Condition ($homeResponse.Headers["Permissions-Policy"] -contains "camera=(), microphone=(), geolocation=()") `
        "Permissions-Policy changed."
    Assert-Condition (-not $homeResponse.Headers.ContainsKey("Server")) `
        "Caddy leaked its Server response header."

    $post = Get-HttpResponse -Client $httpClient -Method ([Net.Http.HttpMethod]::Post) -Path "/"
    Assert-Condition ($post.StatusCode -ne 405 -and $post.StatusCode -lt 500) `
        "POST did not safely reach the Next application boundary."
    $put = Get-HttpResponse -Client $httpClient -Method ([Net.Http.HttpMethod]::Put) -Path "/"
    Assert-Condition ($put.StatusCode -eq 405) `
        "Unsupported methods must be rejected by Caddy with HTTP 405."

    if ($RunBrowserSuite) {
        Write-Host "[browser] Running the 3-viewport Playwright suite through rehearsal Caddy..."
        $pnpmCommand = Get-Command pnpm -ErrorAction SilentlyContinue
        Assert-Condition ($null -ne $pnpmCommand) `
            "pnpm is required only when -RunBrowserSuite is selected."
        $browserEnvironment = New-ProcessEnvironmentMap
        foreach ($pattern in @(
            '^(?i:PLAYWRIGHT_)',
            '^(?i:(?:HTTP|HTTPS|ALL|NO)_PROXY)$',
            '^(?i:CI|NODE_OPTIONS|NODE_PATH|NODE_USE_ENV_PROXY)$'
        )) {
            Add-InheritedEnvironmentRemovals -Variables $browserEnvironment -NamePattern $pattern
        }
        $browserEnvironment['CI'] = "true"
        $browserEnvironment['PLAYWRIGHT_EXTERNAL_SERVER'] = "true"
        $browserEnvironment['PLAYWRIGHT_LOCAL_PRODUCTION_HTTPS'] = "true"
        $browserEnvironment['PLAYWRIGHT_REHEARSAL_NO_RETRIES'] = "true"
        $browserEnvironment['PLAYWRIGHT_BASE_URL'] = "https://127.0.0.1:$publishedPort"
        $browserEnvironment['CALL_AUDIT_PROVIDER'] = "api"
        $browserEnvironment['API_BASE_URL'] = "http://api:8080"
        $browserOutput = Join-Path $temporaryRoot "playwright-results"
        Invoke-WithProcessEnvironment $browserEnvironment {
            & $pnpmCommand.Source `
                --dir (Join-Path $repoRoot "apps/web") `
                exec playwright test `
                --reporter=line `
                "--output=$browserOutput"
            if ($LASTEXITCODE -ne 0) {
                throw "The rehearsal Playwright suite failed with exit code $LASTEXITCODE."
            }
        }
    }

    Write-Host "[6/6] Rehearsal passed; cleaning only owned resources..."
    Write-Host "PASS: images, PostgreSQL, Spring, Next, loopback Caddy, 12 routes, headers, and 3|2|4 evidence compose correctly."
}
catch {
    $failure = $_
    if ($stackStarted) {
        Write-Warning "Rehearsal failed. Printing a bounded tail from the owned Compose project."
        $logArguments = @(
            "compose", "--env-file", $script:EnvFile,
            "--file", $script:ComposeFile
        )
        if ($null -ne $script:ComposeOverlayFile) {
            $logArguments += @("--file", $script:ComposeOverlayFile)
        }
        $logArguments += @(
            "--project-name", $script:ProjectName,
            "--profile", "rehearsal",
            "logs", "--no-color", "--tail", "120"
        )
        $logResult = Invoke-DockerProcess -Arguments $logArguments
        $logResult.Output | ForEach-Object { Write-Warning $_ }
    }
}
finally {
    if ($null -ne $httpClient) {
        $httpClient.Dispose()
    }

    if ($stackStarted) {
        try {
            Invoke-ComposeCommand -Arguments @(
                "--profile", "rehearsal", "down", "--volumes", "--remove-orphans", "--timeout", "20"
            )
        }
        catch {
            if ($null -eq $failure) {
                $failure = $_
            }
            else {
                Write-Warning $_
            }
        }
    }

    foreach ($ownedImage in @(
        @{ Name = $apiImage; Owned = $apiImageOwned; Id = $apiImageOwnedId },
        @{ Name = $webImage; Owned = $webImageOwned; Id = $webImageOwnedId },
        @{ Name = $caddyImage; Owned = $caddyImageOwned; Id = $caddyImageOwnedId }
    )) {
        if ($ownedImage.Owned) {
            try {
                Remove-ExactOwnedImageTag `
                    -ImageReference $ownedImage.Name `
                    -OwnedImageId $ownedImage.Id
            }
            catch {
                if ($null -eq $failure) {
                    $failure = $_
                }
                else {
                    Write-Warning $_
                }
            }
        }
    }

    if ($temporaryRootOwned) {
        try {
            Remove-OwnedTemporaryDirectory `
                -Path $temporaryRoot `
                -ExpectedBase $temporaryBase `
                -ExpectedLeaf $temporaryLeaf `
                -MarkerPath $ownershipMarkerPath `
                -MarkerValue $ownershipMarkerValue
            $temporaryRootOwned = $false
        }
        catch {
            if ($null -eq $failure) {
                $failure = $_
            }
            else {
                Write-Warning $_
            }
        }
        if ($temporaryRootOwned -and $null -eq $failure) {
            $failure = [InvalidOperationException]::new(
                "Unable to remove the owned rehearsal temporary directory."
            )
        }
    }

    if ($null -ne $nextEnvHashBefore) {
        $nextEnvHashAfter = (Get-FileHash -LiteralPath $nextEnvPath -Algorithm SHA256).Hash
        if ($nextEnvHashAfter -ne $nextEnvHashBefore -and $null -eq $failure) {
            $failure = [InvalidOperationException]::new(
                "The Docker rehearsal changed caller-owned apps/web/next-env.d.ts."
            )
        }
    }
}

if ($null -ne $failure) {
    throw $failure
}

#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$script:ManifestKeys = @(
    "schemaVersion",
    "project",
    "releaseStatus",
    "networkStatus",
    "sourceBranch",
    "sourceCommit",
    "sourceTree",
    "cachedOriginMain",
    "cachedOriginDevelop",
    "localDevelop",
    "featureAheadCount",
    "integrationCommit",
    "releasePreparationCommit",
    "mainReleaseCommit",
    "developReleaseCommit",
    "annotatedTag",
    "tagObject",
    "bundleRef",
    "bundleFile",
    "bundleBytes",
    "bundleSha256",
    "bundlePrerequisiteCount"
)

function Assert-Condition {
    param(
        [Parameter(Mandatory)][bool] $Condition,
        [Parameter(Mandatory)][string] $Message
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

function New-GitProcessStartInfo {
    param(
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [Parameter(Mandatory)][string[]] $Arguments
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $script:GitCommand
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    foreach ($name in @($startInfo.Environment.Keys)) {
        if (
            $name -match '^(?i:GIT|GH|SSH|GCM|GITLAB|BITBUCKET)_' -or
            $name -match '^(?i:HTTP|HTTPS|ALL|NO)_PROXY$'
        ) {
            [void] $startInfo.Environment.Remove($name)
        }
    }
    $startInfo.Environment["GIT_CONFIG_NOSYSTEM"] = "1"
    $startInfo.Environment["GIT_CONFIG_GLOBAL"] = $script:NullDevice
    $startInfo.Environment["GIT_TERMINAL_PROMPT"] = "0"
    $startInfo.Environment["GIT_ASKPASS"] = $script:NullDevice
    $startInfo.Environment["GIT_ALLOW_PROTOCOL"] = "file"
    $startInfo.Environment["GIT_NO_LAZY_FETCH"] = "1"
    $startInfo.Environment["GIT_OPTIONAL_LOCKS"] = "0"
    $startInfo.Environment["LC_ALL"] = "C"
    $startInfo.Environment["LANG"] = "C"

    $commonArguments = @(
        "-c", "gc.auto=0",
        "-c", "maintenance.auto=0",
        "-c", "core.hooksPath=$($script:NullDevice)",
        "-c", "core.fsmonitor=false",
        "-c", "credential.helper=",
        "-c", "protocol.file.allow=always",
        "-c", "protocol.http.allow=never",
        "-c", "protocol.https.allow=never",
        "-c", "protocol.ssh.allow=never",
        "-c", "protocol.git.allow=never",
        "-c", "user.name=WSR Offline Rehearsal",
        "-c", "user.email=offline-rehearsal@invalid.example"
    )
    foreach ($argument in @($commonArguments + $Arguments)) {
        [void] $startInfo.ArgumentList.Add($argument)
    }
    return $startInfo
}

function Invoke-Git {
    param(
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [Parameter(Mandatory)][string[]] $Arguments,
        [int[]] $AllowedExitCodes = @(0),
        [string] $FailureMessage = "A bounded local Git command failed"
    )

    Assert-Condition (Test-Path -LiteralPath $WorkingDirectory -PathType Container) `
        "The Git working directory is missing."
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = New-GitProcessStartInfo `
        -WorkingDirectory $WorkingDirectory `
        -Arguments $Arguments
    try {
        Assert-Condition $process.Start() "Could not start the local Git process."
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $exited = $process.WaitForExit($script:GitTimeoutMilliseconds)
        if (-not $exited) {
            try {
                $process.Kill($true)
            }
            catch {
                Assert-Condition $process.HasExited `
                    "$FailureMessage (timeout and termination failed)."
            }
            Assert-Condition ($process.WaitForExit(5000)) `
                "$FailureMessage (termination timeout)."
        }
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        Assert-Condition $exited "$FailureMessage (timeout)."
        Assert-Condition (
            $stdout.Length -le $script:GitAcceptedOutputCharacterLimit -and
            $stderr.Length -le $script:GitAcceptedOutputCharacterLimit
        ) "$FailureMessage (output limit exceeded)."
        Assert-Condition ($AllowedExitCodes -contains $process.ExitCode) `
            "$FailureMessage (exit code $($process.ExitCode))."
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Stdout   = $stdout
            Stderr   = $stderr
        }
    }
    finally {
        $process.Dispose()
    }
}

function Get-OutputLines {
    param([AllowEmptyString()][string] $Text)

    if ([string]::IsNullOrEmpty($Text)) {
        return ,@()
    }
    $normalized = $Text.Replace("`r`n", "`n").TrimEnd("`n")
    if ([string]::IsNullOrEmpty($normalized)) {
        return ,@()
    }
    return ,@($normalized.Split("`n"))
}

function Get-GitScalar {
    param(
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [Parameter(Mandatory)][string[]] $Arguments,
        [string] $FailureMessage = "Could not resolve an exact Git scalar"
    )

    $result = Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments $Arguments `
        -FailureMessage $FailureMessage
    $lines = Get-OutputLines $result.Stdout
    Assert-Condition ($lines.Count -eq 1) $FailureMessage
    return [string] $lines[0]
}

function Get-FileSha256 {
    param([Parameter(Mandatory)][string] $Path)

    Assert-Condition (Test-Path -LiteralPath $Path -PathType Leaf) `
        "A required custody file is missing."
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextSha256 {
    param([AllowEmptyString()][string] $Text)

    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    try {
        return [Convert]::ToHexString(
            [Security.Cryptography.SHA256]::HashData($bytes)
        ).ToLowerInvariant()
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-CanonicalCommit {
    param(
        [Parameter(Mandatory)][string] $Value,
        [Parameter(Mandatory)][string] $Subject
    )

    Assert-Condition ($Value -cmatch '^[0-9a-f]{40}$') `
        "$Subject is not a canonical full Git commit ID."
}

function Assert-GitAncestor {
    param(
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [Parameter(Mandatory)][string] $Ancestor,
        [Parameter(Mandatory)][string] $Descendant,
        [Parameter(Mandatory)][string] $Message
    )

    Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("merge-base", "--is-ancestor", $Ancestor, $Descendant) `
        -FailureMessage $Message | Out-Null
}

function Get-GitParents {
    param(
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [Parameter(Mandatory)][string] $Commit
    )

    $line = Get-GitScalar `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("rev-list", "--parents", "-n", "1", $Commit)
    $parts = @($line.Split(" ", [StringSplitOptions]::RemoveEmptyEntries))
    Assert-Condition ($parts.Count -ge 1 -and $parts[0] -ceq $Commit) `
        "Git returned an invalid parent record."
    if ($parts.Count -eq 1) {
        return ,@()
    }
    return ,@($parts[1..($parts.Count - 1)])
}

function Assert-GitRepositoryComplete {
    param(
        [Parameter(Mandatory)][string] $WorkingDirectory,
        [Parameter(Mandatory)][string] $Label
    )

    $shallow = Get-GitScalar `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("rev-parse", "--is-shallow-repository")
    Assert-Condition ($shallow -ceq "false") "$Label is a shallow repository."

    $partial = Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("config", "--local", "--get", "extensions.partialClone") `
        -AllowedExitCodes @(0, 1)
    Assert-Condition (
        $partial.ExitCode -eq 1 -and
        (Get-OutputLines $partial.Stdout).Count -eq 0
    ) "$Label has a partial-clone extension."

    $promisor = Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("config", "--local", "--get-regexp", '^remote\..*\.promisor$') `
        -AllowedExitCodes @(0, 1)
    Assert-Condition (
        $promisor.ExitCode -eq 1 -and
        (Get-OutputLines $promisor.Stdout).Count -eq 0
    ) "$Label has a promisor remote."

    $partialFilter = Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @(
            "config", "--local", "--get-regexp",
            '^remote\..*\.partialclonefilter$'
        ) `
        -AllowedExitCodes @(0, 1)
    Assert-Condition (
        $partialFilter.ExitCode -eq 1 -and
        (Get-OutputLines $partialFilter.Stdout).Count -eq 0
    ) "$Label has a partial-clone filter."

    $executableConfig = Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @(
            "config", "--local", "--get-regexp",
            '^(core\.fsmonitor|uploadpack\.packobjectshook|filter\..*\.(clean|smudge|process))$'
        ) `
        -AllowedExitCodes @(0, 1)
    Assert-Condition (
        $executableConfig.ExitCode -eq 1 -and
        (Get-OutputLines $executableConfig.Stdout).Count -eq 0
    ) "$Label has executable local Git configuration."

    foreach ($relativeObjectPath in @(
        "objects/info/alternates",
        "objects/info/http-alternates",
        "info/grafts"
    )) {
        $objectPathValue = Get-GitScalar `
            -WorkingDirectory $WorkingDirectory `
            -Arguments @("rev-parse", "--git-path", $relativeObjectPath)
        $objectPath = if ([IO.Path]::IsPathRooted($objectPathValue)) {
            [IO.Path]::GetFullPath($objectPathValue)
        }
        else {
            [IO.Path]::GetFullPath(
                (Join-Path $WorkingDirectory $objectPathValue)
            )
        }
        Assert-Condition (-not (Test-Path -LiteralPath $objectPath)) `
            "$Label uses forbidden alternate or graft state."
    }

    $replaceRefs = Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("for-each-ref", "--format=%(refname)", "refs/replace")
    Assert-Condition ((Get-OutputLines $replaceRefs.Stdout).Count -eq 0) `
        "$Label has replacement refs."

    Invoke-Git `
        -WorkingDirectory $WorkingDirectory `
        -Arguments @("fsck", "--full", "--strict", "--no-dangling") `
        -FailureMessage "$Label failed full strict object verification" | Out-Null
}

function Get-SourceSnapshot {
    param(
        [Parameter(Mandatory)][string] $RepositoryRoot,
        [Parameter(Mandatory)][string] $NextDeclarationPath
    )

    $head = Get-GitScalar `
        -WorkingDirectory $RepositoryRoot `
        -Arguments @("rev-parse", "--verify", "HEAD^{commit}")
    $symbolic = Get-GitScalar `
        -WorkingDirectory $RepositoryRoot `
        -Arguments @("symbolic-ref", "--quiet", "HEAD")
    $status = (Invoke-Git `
        -WorkingDirectory $RepositoryRoot `
        -Arguments @("status", "--porcelain=v1", "--untracked-files=all")).Stdout
    $refs = (Invoke-Git `
        -WorkingDirectory $RepositoryRoot `
        -Arguments @(
            "for-each-ref",
            "--format=%(refname)%00%(objectname)%00%(objecttype)",
            "refs/heads", "refs/remotes", "refs/tags", "refs/replace"
        )).Stdout
    $config = (Invoke-Git `
        -WorkingDirectory $RepositoryRoot `
        -Arguments @("config", "--local", "--null", "--list", "--show-origin")).Stdout
    $indexValue = Get-GitScalar `
        -WorkingDirectory $RepositoryRoot `
        -Arguments @("rev-parse", "--git-path", "index")
    $indexPath = if ([IO.Path]::IsPathRooted($indexValue)) {
        [IO.Path]::GetFullPath($indexValue)
    }
    else {
        [IO.Path]::GetFullPath((Join-Path $RepositoryRoot $indexValue))
    }

    return [pscustomobject]@{
        Head          = $head
        SymbolicRef   = $symbolic
        StatusSha256  = Get-TextSha256 $status
        RefsSha256    = Get-TextSha256 $refs
        ConfigSha256  = Get-TextSha256 $config
        IndexSha256   = Get-FileSha256 $indexPath
        NextSha256    = Get-FileSha256 $NextDeclarationPath
        NextByteCount = ([IO.FileInfo] $NextDeclarationPath).Length
    }
}

function Assert-SourceSnapshotUnchanged {
    param(
        [Parameter(Mandatory)][pscustomobject] $Expected,
        [Parameter(Mandatory)][string] $RepositoryRoot,
        [Parameter(Mandatory)][string] $NextDeclarationPath
    )

    $actual = Get-SourceSnapshot `
        -RepositoryRoot $RepositoryRoot `
        -NextDeclarationPath $NextDeclarationPath
    foreach ($property in @(
        "Head", "SymbolicRef", "StatusSha256", "RefsSha256",
        "ConfigSha256", "IndexSha256", "NextSha256", "NextByteCount"
    )) {
        Assert-Condition ($actual.$property -ceq $Expected.$property) `
            "The source repository custody changed at $property."
    }
}

function New-RunId {
    $bytes = [byte[]]::new(12)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    try {
        return [Convert]::ToHexString($bytes).ToLowerInvariant()
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Remove-OwnedTemporaryRoot {
    param(
        [Parameter(Mandatory)][string] $TemporaryRoot,
        [Parameter(Mandatory)][string] $ExpectedParent,
        [Parameter(Mandatory)][string] $ExpectedOwnerToken
    )

    if (-not (Test-Path -LiteralPath $TemporaryRoot)) {
        return
    }
    $resolvedRoot = [IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $TemporaryRoot).Path
    )
    $resolvedParent = [IO.Path]::GetFullPath(
        (Resolve-Path -LiteralPath $ExpectedParent).Path
    )
    $comparison = if ($IsWindows) {
        [StringComparison]::OrdinalIgnoreCase
    }
    else {
        [StringComparison]::Ordinal
    }
    Assert-Condition (
        [IO.Path]::GetDirectoryName($resolvedRoot).Equals(
            $resolvedParent,
            $comparison
        ) -and
        [IO.Path]::GetFileName($resolvedRoot) -cmatch
            '^wsr-release-handoff-[0-9a-f]{24}$'
    ) "Refused to remove an unexpected temporary path."
    $item = Get-Item -LiteralPath $resolvedRoot -Force
    Assert-Condition (
        -not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)
    ) "Refused to remove a reparse-point temporary root."
    Assert-Condition ($ExpectedOwnerToken -cmatch '^[0-9a-f]{48}$') `
        "Refused a malformed temporary owner token."
    $ownerMarker = Join-Path $resolvedRoot ".wsr-release-handoff-owner"
    Assert-Condition (Test-Path -LiteralPath $ownerMarker -PathType Leaf) `
        "Refused to remove a temporary root without its owner marker."
    $markerItem = Get-Item -LiteralPath $ownerMarker -Force
    Assert-Condition (
        -not ($markerItem.Attributes -band [IO.FileAttributes]::ReparsePoint)
    ) "Refused a reparse-point temporary owner marker."
    Assert-Condition (
        [IO.File]::ReadAllText($ownerMarker, [Text.Encoding]::UTF8) -ceq
        "$ExpectedOwnerToken`n"
    ) "Refused a temporary root whose owner marker changed."
    Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
    Assert-Condition (-not (Test-Path -LiteralPath $resolvedRoot)) `
        "The owned release-handoff temporary root remained after cleanup."
}

function ConvertTo-CanonicalManifestText {
    param([Parameter(Mandatory)][Collections.IDictionary] $Values)

    Assert-Condition ($Values.Count -eq $script:ManifestKeys.Count) `
        "The handoff manifest field count changed."
    $ordered = [ordered]@{}
    foreach ($key in $script:ManifestKeys) {
        Assert-Condition $Values.Contains($key) `
            "The handoff manifest is missing $key."
        $ordered[$key] = $Values[$key]
    }
    return (($ordered | ConvertTo-Json -Compress -Depth 4) + "`n")
}

function Write-CanonicalManifest {
    param(
        [Parameter(Mandatory)][Collections.IDictionary] $Values,
        [Parameter(Mandatory)][string] $Path
    )

    Assert-Condition (-not (Test-Path -LiteralPath $Path)) `
        "Refused to overwrite a handoff manifest."
    $text = ConvertTo-CanonicalManifestText $Values
    [IO.File]::WriteAllText(
        $Path,
        $text,
        [Text.UTF8Encoding]::new($false)
    )
}

function Write-BundleReceipt {
    param(
        [Parameter(Mandatory)][string] $BundleSha256,
        [Parameter(Mandatory)][string] $BundleName,
        [Parameter(Mandatory)][string] $Path
    )

    Assert-Condition ($BundleSha256 -cmatch '^[0-9a-f]{64}$') `
        "Refused a non-canonical bundle digest."
    Assert-Condition ($BundleName -cmatch '^[a-z0-9][a-z0-9.-]*\.bundle$') `
        "Refused a non-canonical bundle member name."
    [IO.File]::WriteAllText(
        $Path,
        "$BundleSha256 *$BundleName`n",
        [Text.UTF8Encoding]::new($false)
    )
}

function Get-ManifestValuesFromFile {
    param([Parameter(Mandatory)][string] $Path)

    $bytes = [IO.File]::ReadAllBytes($Path)
    try {
        Assert-Condition ($bytes.Length -ge 2 -and $bytes.Length -le 8192) `
            "The handoff manifest byte length is outside the closed boundary."
        Assert-Condition ($bytes[-1] -eq 10) `
            "The handoff manifest must end with LF."
        foreach ($value in $bytes) {
            Assert-Condition (
                $value -eq 10 -or ($value -ge 32 -and $value -le 126)
            ) "The handoff manifest must be printable ASCII plus final LF."
        }
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $document = [Text.Json.JsonDocument]::Parse($text)
        try {
            Assert-Condition (
                $document.RootElement.ValueKind -eq
                [Text.Json.JsonValueKind]::Object
            ) "The handoff manifest root must be an object."
            $properties = @($document.RootElement.EnumerateObject())
            Assert-Condition ($properties.Count -eq $script:ManifestKeys.Count) `
                "The handoff manifest has an unexpected field count."
            $numericKeys = @(
                "schemaVersion",
                "featureAheadCount",
                "bundleBytes",
                "bundlePrerequisiteCount"
            )
            for ($index = 0; $index -lt $script:ManifestKeys.Count; $index++) {
                $key = $script:ManifestKeys[$index]
                Assert-Condition (
                    $properties[$index].Name -ceq $key
                ) "The handoff manifest field order or uniqueness changed."
                if ($numericKeys -contains $key) {
                    Assert-Condition (
                        $properties[$index].Value.ValueKind -eq
                        [Text.Json.JsonValueKind]::Number
                    ) "The handoff manifest numeric type changed at $key."
                    [int64] $integerValue = 0
                    Assert-Condition (
                        $properties[$index].Value.TryGetInt64([ref] $integerValue)
                    ) "The handoff manifest integer syntax changed at $key."
                }
                else {
                    Assert-Condition (
                        $properties[$index].Value.ValueKind -eq
                        [Text.Json.JsonValueKind]::String
                    ) "The handoff manifest string type changed at $key."
                }
            }
        }
        finally {
            $document.Dispose()
        }
        $parsed = $text | ConvertFrom-Json
        $values = [ordered]@{}
        foreach ($key in $script:ManifestKeys) {
            $values[$key] = $parsed.$key
        }
        Assert-Condition (
            (ConvertTo-CanonicalManifestText $values) -ceq $text
        ) "The handoff manifest is not canonical JSON."
        return ,$values
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-HandoffArtifacts {
    param(
        [Parameter(Mandatory)][string] $BundlePath,
        [Parameter(Mandatory)][string] $ManifestPath,
        [Parameter(Mandatory)][string] $ReceiptPath,
        [Parameter(Mandatory)][Collections.IDictionary] $Expected,
        [Parameter(Mandatory)][string] $VerifierRoot
    )

    $values = Get-ManifestValuesFromFile $ManifestPath
    foreach ($key in $script:ManifestKeys) {
        Assert-Condition ($values[$key].ToString() -ceq $Expected[$key].ToString()) `
            "The handoff manifest value changed at $key."
    }
    Assert-Condition ([int] $values["schemaVersion"] -eq 1) `
        "The handoff manifest schema version changed."
    Assert-Condition ($values["project"] -ceq "wall-street-receipts") `
        "The handoff project identity changed."
    Assert-Condition ($values["releaseStatus"] -ceq "NOT_RELEASED") `
        "The handoff must not claim a release."
    Assert-Condition ($values["networkStatus"] -ceq "REMOTE_NOT_CONTACTED") `
        "The handoff must not claim remote contact."
    foreach ($key in @(
        "sourceCommit", "sourceTree", "cachedOriginMain",
        "cachedOriginDevelop", "localDevelop", "integrationCommit",
        "releasePreparationCommit", "mainReleaseCommit",
        "developReleaseCommit"
    )) {
        Assert-Condition ($values[$key] -cmatch '^[0-9a-f]{40}$') `
            "The handoff manifest has a non-canonical object at $key."
    }
    Assert-Condition ($values["tagObject"] -cmatch '^[0-9a-f]{40}$') `
        "The handoff tag object is not canonical."
    Assert-Condition ([int64] $values["featureAheadCount"] -gt 0) `
        "The handoff feature distance must be positive."
    Assert-Condition ([int] $values["bundlePrerequisiteCount"] -eq 0) `
        "The handoff bundle cannot have prerequisites."

    $bundleName = [IO.Path]::GetFileName($BundlePath)
    Assert-Condition ($values["bundleFile"] -ceq $bundleName) `
        "The handoff bundle member name changed."
    $bundleInfo = Get-Item -LiteralPath $BundlePath
    Assert-Condition ([int64] $values["bundleBytes"] -eq $bundleInfo.Length) `
        "The handoff bundle byte count changed."
    $bundleSha = Get-FileSha256 $BundlePath
    Assert-Condition ($values["bundleSha256"] -ceq $bundleSha) `
        "The handoff bundle SHA-256 changed."
    $expectedReceipt = "$bundleSha *$bundleName`n"
    $receipt = [IO.File]::ReadAllText($ReceiptPath, [Text.Encoding]::UTF8)
    Assert-Condition ($receipt -ceq $expectedReceipt) `
        "The handoff checksum receipt changed."

    Assert-Condition (-not (Test-Path -LiteralPath $VerifierRoot)) `
        "The bundle verifier path already exists."
    [IO.Directory]::CreateDirectory($VerifierRoot) | Out-Null
    Invoke-Git `
        -WorkingDirectory $VerifierRoot `
        -Arguments @("init", "--bare", ".") `
        -FailureMessage "Could not initialize the empty bundle verifier" | Out-Null
    Invoke-Git `
        -WorkingDirectory $VerifierRoot `
        -Arguments @("bundle", "verify", $BundlePath) `
        -FailureMessage "The handoff bundle is structurally incomplete" | Out-Null
    $heads = Invoke-Git `
        -WorkingDirectory $VerifierRoot `
        -Arguments @("bundle", "list-heads", $BundlePath)
    $headLines = Get-OutputLines $heads.Stdout
    Assert-Condition (
        $headLines.Count -eq 1 -and
        $headLines[0] -ceq "$($values['tagObject']) $($values['bundleRef'])"
    ) "The handoff bundle advertised an unexpected ref."
    Invoke-Git `
        -WorkingDirectory $VerifierRoot `
        -Arguments @("bundle", "unbundle", $BundlePath) `
        -FailureMessage "The handoff bundle could not be fully imported" | Out-Null
    Invoke-Git `
        -WorkingDirectory $VerifierRoot `
        -Arguments @(
            "update-ref", $values["bundleRef"], $values["tagObject"]
        ) | Out-Null
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $VerifierRoot `
            -Arguments @("cat-file", "-t", $values["tagObject"])) -ceq "tag" -and
        (Get-GitScalar `
            -WorkingDirectory $VerifierRoot `
            -Arguments @("rev-parse", "$($values['bundleRef'])^{commit}")) -ceq
        $values["mainReleaseCommit"] -and
        (Get-GitScalar `
            -WorkingDirectory $VerifierRoot `
            -Arguments @("rev-parse", "$($values['bundleRef'])^{tree}")) -ceq
        $values["sourceTree"]
    ) "The imported handoff bundle identity changed."
    Invoke-Git `
        -WorkingDirectory $VerifierRoot `
        -Arguments @("fsck", "--full", "--strict", "--no-dangling") `
        -FailureMessage "The imported handoff bundle failed full object verification" |
        Out-Null
}

function Assert-Rejected {
    param(
        [Parameter(Mandatory)][scriptblock] $Action,
        [Parameter(Mandatory)][string] $ExpectedMessagePattern,
        [Parameter(Mandatory)][string] $Message
    )

    $rejected = $false
    try {
        & $Action
    }
    catch {
        Assert-Condition ($_.Exception.Message -cmatch $ExpectedMessagePattern) `
            "The negative artifact failed for an unexpected reason."
        $rejected = $true
    }
    Assert-Condition $rejected $Message
}

function Copy-FlippedFile {
    param(
        [Parameter(Mandatory)][string] $Source,
        [Parameter(Mandatory)][string] $Destination
    )

    $bytes = [IO.File]::ReadAllBytes($Source)
    try {
        Assert-Condition ($bytes.Length -gt 1024) `
            "The bundle is unexpectedly small for corruption testing."
        $index = [Math]::Floor($bytes.Length / 2)
        $bytes[$index] = $bytes[$index] -bxor 1
        [IO.File]::WriteAllBytes($Destination, $bytes)
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Copy-TruncatedFile {
    param(
        [Parameter(Mandatory)][string] $Source,
        [Parameter(Mandatory)][string] $Destination
    )

    $bytes = [IO.File]::ReadAllBytes($Source)
    try {
        Assert-Condition ($bytes.Length -gt 2048) `
            "The bundle is unexpectedly small for truncation testing."
        $stream = [IO.File]::Open(
            $Destination,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        try {
            $stream.Write($bytes, 0, $bytes.Length - 64)
            $stream.Flush($true)
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

$scriptPath = [IO.Path]::GetFullPath($PSCommandPath)
$repositoryRoot = [IO.Path]::GetFullPath(
    (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
)
$gitResolutions = @(Get-Command git -CommandType Application -ErrorAction Stop)
Assert-Condition ($gitResolutions.Count -ge 1) "Could not resolve local Git."
$script:GitCommand = [IO.Path]::GetFullPath($gitResolutions[0].Source)
$script:NullDevice = if ($IsWindows) { "NUL" } else { "/dev/null" }
$script:GitTimeoutMilliseconds = 120000
$script:GitAcceptedOutputCharacterLimit = 1048576
$nextDeclarationPath = Join-Path $repositoryRoot "apps/web/next-env.d.ts"
$temporaryParent = [IO.Path]::GetDirectoryName(
    [IO.Path]::GetFullPath(
        (Join-Path ([IO.Path]::GetTempPath()) "wsr-parent-boundary-probe")
    )
)
$runId = New-RunId
$ownerToken = (New-RunId) + (New-RunId)
$temporaryRoot = Join-Path $temporaryParent "wsr-release-handoff-$runId"
$primaryFailure = $null
$cleanupFailure = $null
$sourceFailure = $null
$sourceSnapshot = $null
$evidence = $null

try {
    $pathComparison = if ($IsWindows) {
        [StringComparison]::OrdinalIgnoreCase
    }
    else {
        [StringComparison]::Ordinal
    }
    $reportedRepositoryRootValue = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "--show-toplevel")
    $reportedRepositoryRoot = [IO.Path]::GetFullPath(
        $reportedRepositoryRootValue
    )
    Assert-Condition (
        $reportedRepositoryRoot.Equals($repositoryRoot, $pathComparison)
    ) "Run the release-handoff rehearsal from its exact repository checkout."
    $expectedScriptPath = [IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot "scripts/verify-local-release-handoff.ps1")
    )
    Assert-Condition ($scriptPath.Equals($expectedScriptPath, $pathComparison)) `
        "Run the exact committed release-handoff harness path."

    $sourceBranch = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("symbolic-ref", "--quiet", "--short", "HEAD")
    Assert-Condition (
        $sourceBranch -cmatch '^feature/[a-z0-9][a-z0-9._/-]*$' -and
        -not $sourceBranch.Contains("..") -and
        -not $sourceBranch.Contains("@{")
    ) "ADR-056 accepts only a canonical symbolic feature branch."
    Invoke-Git `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("check-ref-format", "--branch", $sourceBranch) `
        -FailureMessage "The source feature branch name is invalid" | Out-Null

    $sourceRef = "refs/heads/$sourceBranch"
    $sourceCommit = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "--verify", "$sourceRef^{commit}")
    Assert-CanonicalCommit $sourceCommit "Source HEAD"
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $repositoryRoot `
            -Arguments @("rev-parse", "--verify", "HEAD^{commit}")) -ceq
        $sourceCommit
    ) "The symbolic feature ref and HEAD differ."
    $sourceTree = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "$sourceCommit^{tree}")
    Assert-CanonicalCommit $sourceTree "Source tree"

    $localDevelop = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "--verify", "refs/heads/develop^{commit}")
    $localMain = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "--verify", "refs/heads/main^{commit}")
    $cachedOriginDevelop = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "--verify", "refs/remotes/origin/develop^{commit}")
    $cachedOriginMain = Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-parse", "--verify", "refs/remotes/origin/main^{commit}")
    foreach ($pair in @(
        @($localDevelop, "Local develop"),
        @($localMain, "Local main"),
        @($cachedOriginDevelop, "Cached origin/develop"),
        @($cachedOriginMain, "Cached origin/main")
    )) {
        Assert-CanonicalCommit $pair[0] $pair[1]
    }
    Assert-Condition ($localMain -ceq $cachedOriginMain) `
        "Local main and cached origin/main are not coherent."
    Assert-GitAncestor $repositoryRoot $cachedOriginDevelop $localDevelop `
        "Cached origin/develop cannot advance to local develop by fast-forward."
    Assert-GitAncestor $repositoryRoot $cachedOriginMain $localDevelop `
        "Cached origin/main is not an ancestor of local develop."
    Assert-GitAncestor $repositoryRoot $localDevelop $sourceCommit `
        "The feature candidate is not based on local develop."
    $featureAheadCount = [int] (Get-GitScalar `
        -WorkingDirectory $repositoryRoot `
        -Arguments @("rev-list", "--count", "$localDevelop..$sourceCommit"))
    Assert-Condition ($featureAheadCount -gt 0) `
        "The feature candidate has no commit beyond local develop."

    $sourceStatusLines = Get-OutputLines (
        Invoke-Git `
            -WorkingDirectory $repositoryRoot `
            -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
    ).Stdout
    Assert-Condition (
        $sourceStatusLines.Count -eq 0 -or
        (
            $sourceStatusLines.Count -eq 1 -and
            $sourceStatusLines[0] -ceq " M apps/web/next-env.d.ts"
        )
    ) "The source must be clean except the exact unstaged Next declaration."
    Assert-GitRepositoryComplete $repositoryRoot "Source repository"
    $sourceSnapshot = Get-SourceSnapshot `
        -RepositoryRoot $repositoryRoot `
        -NextDeclarationPath $nextDeclarationPath

    Assert-Condition (-not (Test-Path -LiteralPath $temporaryRoot)) `
        "The owned release-handoff temporary root already exists."
    [IO.Directory]::CreateDirectory($temporaryRoot) | Out-Null
    $ownerMarkerPath = Join-Path $temporaryRoot ".wsr-release-handoff-owner"
    $ownerMarkerStream = [IO.File]::Open(
        $ownerMarkerPath,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None
    )
    try {
        $ownerMarkerBytes = [Text.Encoding]::UTF8.GetBytes("$ownerToken`n")
        try {
            $ownerMarkerStream.Write($ownerMarkerBytes)
            $ownerMarkerStream.Flush($true)
        }
        finally {
            [Array]::Clear($ownerMarkerBytes, 0, $ownerMarkerBytes.Length)
        }
    }
    finally {
        $ownerMarkerStream.Dispose()
    }
    $remoteRepository = Join-Path $temporaryRoot "simulated-remote.git"
    $integrationRepository = Join-Path $temporaryRoot "integration"
    $artifactDirectory = Join-Path $temporaryRoot "artifacts"
    $serverRepository = Join-Path $temporaryRoot "server-checkout"
    [IO.Directory]::CreateDirectory($artifactDirectory) | Out-Null

    Invoke-Git `
        -WorkingDirectory $temporaryRoot `
        -Arguments @("init", "--bare", $remoteRepository) `
        -FailureMessage "Could not initialize the owned simulated bare remote" | Out-Null
    Invoke-Git `
        -WorkingDirectory $temporaryRoot `
        -Arguments @(
            "--git-dir=$remoteRepository", "fetch",
            "--no-tags", "--no-write-fetch-head", "--no-recurse-submodules",
            $repositoryRoot,
            "+refs/remotes/origin/main:refs/heads/main",
            "+refs/remotes/origin/develop:refs/heads/develop"
        ) `
        -FailureMessage "Could not seed the simulated remote from the approved cached refs" | Out-Null
    Invoke-Git `
        -WorkingDirectory $temporaryRoot `
        -Arguments @(
            "--git-dir=$remoteRepository", "symbolic-ref",
            "HEAD", "refs/heads/develop"
        ) | Out-Null
    $seededRefs = Get-OutputLines (
        Invoke-Git `
            -WorkingDirectory $temporaryRoot `
            -Arguments @(
                "--git-dir=$remoteRepository", "for-each-ref",
                "--format=%(refname)%00%(objectname)"
            )
    ).Stdout
    Assert-Condition (
        $seededRefs.Count -eq 2 -and
        $seededRefs -contains "refs/heads/develop`0$cachedOriginDevelop" -and
        $seededRefs -contains "refs/heads/main`0$cachedOriginMain"
    ) "The simulated remote seed refs changed."

    Invoke-Git `
        -WorkingDirectory $temporaryRoot `
        -Arguments @(
            "clone", "--no-local", "--no-hardlinks",
            $remoteRepository, $integrationRepository
        ) `
        -FailureMessage "Could not clone the simulated remote" | Out-Null
    $candidateInputBundle = Join-Path $temporaryRoot "candidate-input.bundle"
    Invoke-Git `
        -WorkingDirectory $repositoryRoot `
        -Arguments @(
            "bundle", "create", $candidateInputBundle,
            "refs/heads/develop", $sourceRef
        ) `
        -FailureMessage "Could not create the local candidate input bundle" | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @("bundle", "verify", $candidateInputBundle) `
        -FailureMessage "The local candidate input bundle is incomplete" | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @("bundle", "unbundle", $candidateInputBundle) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "update-ref", "refs/remotes/source/develop", $localDevelop
        ) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "update-ref", "refs/remotes/source/feature", $sourceCommit
        ) | Out-Null

    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @("branch", "simulated-local-develop", $localDevelop) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "push", "--porcelain", $remoteRepository,
            "refs/heads/simulated-local-develop:refs/heads/develop"
        ) `
        -FailureMessage "The simulated develop backlog was not a fast-forward" | Out-Null
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $temporaryRoot `
            -Arguments @(
                "--git-dir=$remoteRepository", "rev-parse",
                "refs/heads/develop^{commit}"
            )) -ceq $localDevelop
    ) "The simulated develop backlog identity changed."

    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "switch", "--create", "simulated-integrated-develop", $localDevelop
        ) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @("merge", "--no-ff", "--no-edit", $sourceCommit) `
        -FailureMessage "The simulated feature integration failed" | Out-Null
    $integrationCommit = Get-GitScalar `
        -WorkingDirectory $integrationRepository `
        -Arguments @("rev-parse", "HEAD^{commit}")
    $integrationParents = Get-GitParents $integrationRepository $integrationCommit
    Assert-Condition (
        $integrationParents.Count -eq 2 -and
        $integrationParents[0] -ceq $localDevelop -and
        $integrationParents[1] -ceq $sourceCommit
    ) "The simulated feature merge parent graph changed."
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $integrationRepository `
            -Arguments @("rev-parse", "$integrationCommit^{tree}")) -ceq
        $sourceTree
    ) "The feature integration changed the candidate tree."
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "push", "--porcelain", $remoteRepository,
            "refs/heads/simulated-integrated-develop:refs/heads/develop"
        ) `
        -FailureMessage "The integrated develop ref was not a fast-forward" | Out-Null

    $releaseBranch = "release/0.0.0-rehearsal.$runId"
    $rehearsalTag = "v0.0.0-rehearsal.$runId"
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @("switch", "--create", $releaseBranch, $integrationCommit) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "commit", "--allow-empty", "--message",
            "chore(release): prepare offline rehearsal"
        ) | Out-Null
    $releasePreparationCommit = Get-GitScalar `
        -WorkingDirectory $integrationRepository `
        -Arguments @("rev-parse", "HEAD^{commit}")
    $releaseParents = Get-GitParents `
        $integrationRepository `
        $releasePreparationCommit
    Assert-Condition (
        $releaseParents.Count -eq 1 -and
        $releaseParents[0] -ceq $integrationCommit
    ) "The release-preparation parent graph changed."

    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "switch", "--create", "simulated-main", $cachedOriginMain
        ) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "merge", "--no-ff", "--no-edit", $releasePreparationCommit
        ) `
        -FailureMessage "The simulated main release merge failed" | Out-Null
    $mainReleaseCommit = Get-GitScalar `
        -WorkingDirectory $integrationRepository `
        -Arguments @("rev-parse", "HEAD^{commit}")
    $mainParents = Get-GitParents $integrationRepository $mainReleaseCommit
    Assert-Condition (
        $mainParents.Count -eq 2 -and
        $mainParents[0] -ceq $cachedOriginMain -and
        $mainParents[1] -ceq $releasePreparationCommit
    ) "The simulated main release merge graph changed."
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "tag", "--annotate", $rehearsalTag,
            "--message", "Wall Street Receipts offline rehearsal", $mainReleaseCommit
        ) | Out-Null
    $tagObject = Get-GitScalar `
        -WorkingDirectory $integrationRepository `
        -Arguments @("rev-parse", "refs/tags/$rehearsalTag^{tag}")
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $integrationRepository `
            -Arguments @("cat-file", "-t", $tagObject)) -ceq "tag"
    ) "The rehearsal tag is not annotated."
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "push", "--porcelain", $remoteRepository,
            "refs/heads/simulated-main:refs/heads/main",
            "refs/tags/$rehearsalTag`:refs/tags/$rehearsalTag"
        ) `
        -FailureMessage "The simulated main/tag publication failed" | Out-Null

    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "switch", "--create", "simulated-release-develop", $integrationCommit
        ) | Out-Null
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "merge", "--no-ff", "--no-edit", $releasePreparationCommit
        ) `
        -FailureMessage "The simulated develop release merge failed" | Out-Null
    $developReleaseCommit = Get-GitScalar `
        -WorkingDirectory $integrationRepository `
        -Arguments @("rev-parse", "HEAD^{commit}")
    $developParents = Get-GitParents $integrationRepository $developReleaseCommit
    Assert-Condition (
        $developParents.Count -eq 2 -and
        $developParents[0] -ceq $integrationCommit -and
        $developParents[1] -ceq $releasePreparationCommit
    ) "The simulated develop release merge graph changed."
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "push", "--porcelain", $remoteRepository,
            "refs/heads/simulated-release-develop:refs/heads/develop"
        ) `
        -FailureMessage "The simulated develop release publication failed" | Out-Null

    foreach ($commit in @(
        $integrationCommit,
        $releasePreparationCommit,
        $mainReleaseCommit,
        $developReleaseCommit
    )) {
        Assert-Condition (
            (Get-GitScalar `
                -WorkingDirectory $integrationRepository `
                -Arguments @("rev-parse", "$commit^{tree}")) -ceq
            $sourceTree
        ) "A simulated Git Flow commit changed the candidate tree."
    }
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $temporaryRoot `
            -Arguments @(
                "--git-dir=$remoteRepository", "rev-parse",
                "refs/heads/main^{commit}"
            )) -ceq $mainReleaseCommit -and
        (Get-GitScalar `
            -WorkingDirectory $temporaryRoot `
            -Arguments @(
                "--git-dir=$remoteRepository", "rev-parse",
                "refs/heads/develop^{commit}"
            )) -ceq $developReleaseCommit -and
        (Get-GitScalar `
            -WorkingDirectory $temporaryRoot `
            -Arguments @(
                "--git-dir=$remoteRepository", "rev-parse",
                "refs/tags/$rehearsalTag^{tag}"
            )) -ceq $tagObject
    ) "The simulated remote release refs changed."

    $bundleName = "wall-street-receipts-$mainReleaseCommit.bundle"
    $bundlePath = Join-Path $artifactDirectory $bundleName
    $manifestPath = Join-Path $artifactDirectory "manifest.json"
    $receiptPath = Join-Path $artifactDirectory "$bundleName.sha256"
    Invoke-Git `
        -WorkingDirectory $integrationRepository `
        -Arguments @(
            "bundle", "create", $bundlePath,
            "refs/tags/$rehearsalTag"
        ) `
        -FailureMessage "Could not create the tag-only release-source bundle" | Out-Null
    $bundleInfo = Get-Item -LiteralPath $bundlePath
    $bundleSha256 = Get-FileSha256 $bundlePath
    $manifestValues = [ordered]@{
        schemaVersion            = 1
        project                  = "wall-street-receipts"
        releaseStatus            = "NOT_RELEASED"
        networkStatus            = "REMOTE_NOT_CONTACTED"
        sourceBranch             = $sourceBranch
        sourceCommit             = $sourceCommit
        sourceTree               = $sourceTree
        cachedOriginMain         = $cachedOriginMain
        cachedOriginDevelop      = $cachedOriginDevelop
        localDevelop             = $localDevelop
        featureAheadCount        = $featureAheadCount
        integrationCommit        = $integrationCommit
        releasePreparationCommit = $releasePreparationCommit
        mainReleaseCommit        = $mainReleaseCommit
        developReleaseCommit     = $developReleaseCommit
        annotatedTag             = $rehearsalTag
        tagObject                = $tagObject
        bundleRef                = "refs/tags/$rehearsalTag"
        bundleFile               = $bundleName
        bundleBytes              = [int64] $bundleInfo.Length
        bundleSha256             = $bundleSha256
        bundlePrerequisiteCount  = 0
    }
    Write-CanonicalManifest $manifestValues $manifestPath
    Write-BundleReceipt $bundleSha256 $bundleName $receiptPath
    Assert-HandoffArtifacts `
        -BundlePath $bundlePath `
        -ManifestPath $manifestPath `
        -ReceiptPath $receiptPath `
        -Expected $manifestValues `
        -VerifierRoot (Join-Path $temporaryRoot "valid-bundle-verifier")

    $flippedDirectory = Join-Path $temporaryRoot "flipped"
    [IO.Directory]::CreateDirectory($flippedDirectory) | Out-Null
    $flippedBundle = Join-Path $flippedDirectory $bundleName
    Copy-FlippedFile $bundlePath $flippedBundle
    Copy-Item -LiteralPath $manifestPath -Destination $flippedDirectory
    Copy-Item -LiteralPath $receiptPath -Destination $flippedDirectory
    Assert-Rejected `
        -Message "A byte-flipped bundle was accepted." `
        -ExpectedMessagePattern '^The handoff bundle SHA-256 changed\.$' `
        -Action {
        Assert-HandoffArtifacts `
            -BundlePath $flippedBundle `
            -ManifestPath (Join-Path $flippedDirectory "manifest.json") `
            -ReceiptPath (Join-Path $flippedDirectory "$bundleName.sha256") `
            -Expected $manifestValues `
            -VerifierRoot (Join-Path $temporaryRoot "flipped-verifier")
    }

    $truncatedDirectory = Join-Path $temporaryRoot "truncated"
    [IO.Directory]::CreateDirectory($truncatedDirectory) | Out-Null
    $truncatedBundle = Join-Path $truncatedDirectory $bundleName
    Copy-TruncatedFile $bundlePath $truncatedBundle
    $truncatedValues = [ordered]@{}
    foreach ($key in $script:ManifestKeys) {
        $truncatedValues[$key] = $manifestValues[$key]
    }
    $truncatedValues["bundleBytes"] = [int64] (
        Get-Item -LiteralPath $truncatedBundle
    ).Length
    $truncatedValues["bundleSha256"] = Get-FileSha256 $truncatedBundle
    Write-CanonicalManifest `
        $truncatedValues `
        (Join-Path $truncatedDirectory "manifest.json")
    Write-BundleReceipt `
        $truncatedValues["bundleSha256"] `
        $bundleName `
        (Join-Path $truncatedDirectory "$bundleName.sha256")
    Assert-Rejected `
        -Message "A truncated, rehashed bundle was accepted." `
        -ExpectedMessagePattern '^The handoff bundle (is structurally incomplete|could not be fully imported) \(exit code [1-9][0-9]*\)\.$' `
        -Action {
        Assert-HandoffArtifacts `
            -BundlePath $truncatedBundle `
            -ManifestPath (Join-Path $truncatedDirectory "manifest.json") `
            -ReceiptPath (Join-Path $truncatedDirectory "$bundleName.sha256") `
            -Expected $truncatedValues `
            -VerifierRoot (Join-Path $temporaryRoot "truncated-verifier")
    }

    [IO.Directory]::CreateDirectory($serverRepository) | Out-Null
    Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @("init", ".") `
        -FailureMessage "Could not initialize the offline server checkout" | Out-Null
    Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @("bundle", "verify", $bundlePath) `
        -FailureMessage "The server rejected the verified bundle" | Out-Null
    Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @("bundle", "unbundle", $bundlePath) | Out-Null
    Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @(
            "update-ref", "refs/tags/$rehearsalTag", $tagObject
        ) | Out-Null
    Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @("checkout", "--detach", $mainReleaseCommit) | Out-Null
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $serverRepository `
            -Arguments @("rev-parse", "HEAD^{commit}")) -ceq
        $mainReleaseCommit -and
        (Get-GitScalar `
            -WorkingDirectory $serverRepository `
            -Arguments @("rev-parse", "refs/tags/$rehearsalTag^{commit}")) -ceq
        $mainReleaseCommit -and
        (Get-GitScalar `
            -WorkingDirectory $serverRepository `
            -Arguments @("rev-parse", "HEAD^{tree}")) -ceq
        $sourceTree
    ) "The offline server checkout identity changed."
    $serverStatus = Get-OutputLines (
        Invoke-Git `
            -WorkingDirectory $serverRepository `
            -Arguments @("status", "--porcelain=v1", "--untracked-files=all")
    ).Stdout
    Assert-Condition ($serverStatus.Count -eq 0) `
        "The offline server checkout is not clean."
    $serverSymbolicHead = Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @("symbolic-ref", "--quiet", "HEAD") `
        -AllowedExitCodes @(0, 1)
    Assert-Condition (
        $serverSymbolicHead.ExitCode -eq 1 -and
        (Get-OutputLines $serverSymbolicHead.Stdout).Count -eq 0
    ) "The offline server checkout is not detached."
    $serverRemotes = Invoke-Git `
        -WorkingDirectory $serverRepository `
        -Arguments @("remote")
    Assert-Condition ((Get-OutputLines $serverRemotes.Stdout).Count -eq 0) `
        "The offline server checkout unexpectedly has a remote."
    Assert-GitRepositoryComplete $serverRepository "Offline server checkout"

    foreach ($relativePath in @(
        "AGENTS.md",
        "README.md",
        ".env.example",
        "apps/api/pom.xml",
        "apps/api/src/main/resources/db/migration/V1__baseline.sql",
        "apps/web/package.json",
        "deploy/home-server/compose.yaml",
        "deploy/home-server/preflight.sh",
        "deploy/home-server/compose-production.sh",
        "deploy/home-server/server-facts.sh",
        "scripts/verify-home-server-deployment.py",
        "scripts/verify-local-release-handoff.ps1",
        "decisions/ADR-056-disposable-offline-git-flow-release-source-handoff-rehearsal.md"
    )) {
        Assert-Condition (
            Test-Path -LiteralPath (Join-Path $serverRepository $relativePath) `
                -PathType Leaf
        ) "The offline server checkout is missing $relativePath."
    }
    Assert-Condition (
        (Get-GitScalar `
            -WorkingDirectory $serverRepository `
            -Arguments @("rev-parse", "HEAD:apps/web/next-env.d.ts")) -ceq
        (Get-GitScalar `
            -WorkingDirectory $repositoryRoot `
            -Arguments @("rev-parse", "$sourceCommit`:apps/web/next-env.d.ts"))
    ) "The server checkout did not receive the committed Next declaration."

    $evidence = [pscustomobject]@{
        SourceCommit         = $sourceCommit
        SourceTree           = $sourceTree
        FeatureAheadCount    = $featureAheadCount
        MainReleaseCommit    = $mainReleaseCommit
        DevelopReleaseCommit = $developReleaseCommit
        TagObject            = $tagObject
        BundleBytes          = [int64] $bundleInfo.Length
        BundleSha256         = $bundleSha256
    }
}
catch {
    $primaryFailure = $_
}
finally {
    try {
        Remove-OwnedTemporaryRoot `
            -TemporaryRoot $temporaryRoot `
            -ExpectedParent $temporaryParent `
            -ExpectedOwnerToken $ownerToken
    }
    catch {
        $cleanupFailure = $_
    }
    if ($null -ne $sourceSnapshot) {
        try {
            Assert-SourceSnapshotUnchanged `
                -Expected $sourceSnapshot `
                -RepositoryRoot $repositoryRoot `
                -NextDeclarationPath $nextDeclarationPath
        }
        catch {
            $sourceFailure = $_
        }
    }
}

if ($null -ne $sourceFailure) {
    throw $sourceFailure
}
if ($null -ne $cleanupFailure) {
    throw $cleanupFailure
}
if ($null -ne $primaryFailure) {
    throw $primaryFailure
}
Assert-Condition ($null -ne $evidence) `
    "The release-handoff rehearsal produced no bounded evidence."

Write-Host "PASS: disposable Git Flow integration and release merges preserved the candidate tree."
Write-Host "PASS: annotated tag-only bundle, strict manifest, receipt, and two corruption rejections passed."
Write-Host "PASS: offline detached server checkout has complete objects, required paths, and clean status."
Write-Host "PASS: source HEAD, ref, status, config, index, refs, and user-owned Next bytes are unchanged."
Write-Host (
    "HANDOFF_EVIDENCE|source=" + $evidence.SourceCommit +
    "|tree=" + $evidence.SourceTree +
    "|ahead=" + $evidence.FeatureAheadCount +
    "|main=" + $evidence.MainReleaseCommit +
    "|develop=" + $evidence.DevelopReleaseCommit +
    "|tag=" + $evidence.TagObject +
    "|bytes=" + $evidence.BundleBytes +
    "|sha256=" + $evidence.BundleSha256
)
Write-Host "NOT_RELEASED"
Write-Host "REMOTE_NOT_CONTACTED"

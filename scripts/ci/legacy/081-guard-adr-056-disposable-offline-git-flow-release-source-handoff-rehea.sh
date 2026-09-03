python <<'PYTHON'
import re
from pathlib import Path

def require(condition, message):
    if not condition:
        raise ValueError(message)

def compact(value):
    compacted = re.sub(
        r"\s+", " ", value.replace("`", " ")
    ).strip()
    return compacted.replace("@( ", "@(").replace(" )", ")")

harness_path = Path("scripts/verify-local-release-handoff.ps1")
workflow_path = Path(".github/workflows/ci.yml")
adr_path = Path(
    "decisions/ADR-056-disposable-offline-git-flow-release-"
    "source-handoff-rehearsal.md"
)
documentation_paths = (
    Path("README.md"),
    Path("deploy/home-server/README.md"),
    Path("IMPLEMENTATION_LOG.md"),
    adr_path,
)
required_paths = (
    harness_path,
    workflow_path,
    *documentation_paths,
)
require(
    all(path.is_file() for path in required_paths),
    "ADR-056 script/workflow/document surface is incomplete",
)

harness = harness_path.read_text(encoding="utf-8")
workflow = workflow_path.read_text(encoding="utf-8")
documentation = {
    path: path.read_text(encoding="utf-8")
    for path in documentation_paths
}
compact_harness = compact(harness)

adr056_project_marker = (
    "      - name: Project exact pre-ADR-056 repository view\n"
)
adr055_project_marker = (
    "      - name: Project exact pre-ADR-055 repository view\n"
)
adr055_restore_marker = (
    "      - name: Restore exact ADR-055 repository view\n"
)
adr056_restore_marker = (
    "      - name: Restore exact ADR-056 repository view\n"
)
guard_marker = (
    "      - name: Guard ADR-056 disposable offline Git Flow "
    "release-source handoff rehearsal\n"
)
parse_marker = (
    "      - name: Parse disposable offline Git Flow "
    "release-source handoff rehearsal\n"
)
adr055_guard_marker = (
    "      - name: Guard ADR-055 offline SEC manifest API-mode "
    "full-stack acceptance\n"
)
ordered_markers = (
    adr056_project_marker,
    adr055_project_marker,
    adr055_restore_marker,
    adr056_restore_marker,
    guard_marker,
    parse_marker,
    adr055_guard_marker,
)
require(
    all(workflow.count(marker) == 1 for marker in ordered_markers)
    and all(
        workflow.index(left) < workflow.index(right)
        for left, right in zip(ordered_markers, ordered_markers[1:])
    ),
    "ADR-056 outer projection/restore/guard/parser ordering changed",
)
project_block = workflow.split(
    adr056_project_marker, 1
)[1].split(adr055_project_marker, 1)[0]
restore_block = workflow.split(
    adr056_restore_marker, 1
)[1].split(guard_marker, 1)[0]
require(
    'BASE_REVISION = "20d70d73f53668a7f1bf2b5b1d70e4c1e9fbfca2"'
    in project_block
    and 'Path("apps/web/next-env.d.ts") not in current_paths'
    in project_block
    and 'projection_root / "head-state.json"' in project_block
    and 'projection_root / "excluded-user-owned" / next_env_path'
    in project_block
    and 'shutil.copyfile(next_env_path, next_env_custody_path)'
    in project_block
    and '["git", "checkout", "--detach", BASE_REVISION]'
    in project_block
    and 'projected_head == BASE_REVISION' in project_block
    and 'len(modified_paths) == 4' in project_block
    and 'len(added_paths) == 2' in project_block
    and 'len(current_paths) == 6' in project_block
    and '"--force"' not in project_block
    and '["git", "show"' not in project_block,
    "ADR-056 outer projection lost exact base/current/Next custody",
)
require(
    "        if: always()\n" in restore_block
    and 'BASE_REVISION = "20d70d73f53668a7f1bf2b5b1d70e4c1e9fbfca2"'
    in restore_block
    and 'observed_head == BASE_REVISION' in restore_block
    and 'observed_head == original_head' in restore_block
    and '["git", "checkout", "--detach", original_head]'
    in restore_block
    and '["git", "symbolic-ref", "HEAD", original_symbolic_ref]'
    in restore_block
    and 'restored_symbolic_ref == original_symbolic_ref'
    in restore_block
    and 'shutil.copyfile(next_env_custody_path, next_env_path)'
    in restore_block
    and 'User-owned Next declaration was not restored byte-for-byte'
    in restore_block
    and 'len(raw_manifest) == 6' in restore_block
    and '"--force"' not in restore_block
    and "head_state_path.unlink()" in restore_block
    and "prepared.unlink()" in restore_block,
    "ADR-056 outer restoration lost partial-failure byte-exact recovery",
)

require(
    re.search(
        r"(?ms)^#Requires -Version 7\.0\s+"
        r"\[CmdletBinding\(\)\]\s+param\(\)\s+",
        harness,
    ) is not None
    and harness.count("[CmdletBinding()]") == 1
    and harness.count("param()") == 1
    and "Read-Host" not in harness
    and "$env:" not in harness,
    "ADR-056 harness must remain no-input and caller-environment independent",
)

manifest_match = re.search(
    r"(?ms)^\$script:ManifestKeys = @\((.*?)^\)\s*$",
    harness,
)
require(
    manifest_match is not None,
    "ADR-056 manifest key declaration is missing",
)
manifest_keys = tuple(
    re.findall(
        r'^\s+"([A-Za-z][A-Za-z0-9]*)",?\s*$',
        manifest_match.group(1),
        re.MULTILINE,
    )
)
expected_manifest_keys = (
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
    "bundlePrerequisiteCount",
)
require(
    manifest_keys == expected_manifest_keys,
    "ADR-056 closed ordered 22-field manifest schema changed",
)
get_output_lines = harness.split(
    "function Get-OutputLines {", 1
)[1].split("function Get-GitScalar {", 1)[0]
get_git_parents = harness.split(
    "function Get-GitParents {", 1
)[1].split("function Assert-GitRepositoryComplete {", 1)[0]
require(
    get_output_lines.count("return ,@()") == 2
    and 'return ,@($normalized.Split("`n"))'
    in get_output_lines
    and get_git_parents.count("return ,@()") == 1
    and 'return ,@($parts[1..($parts.Count - 1)])'
    in get_git_parents,
    "ADR-056 PowerShell collection return cardinality changed",
)
manifest_reader = harness.split(
    "function Get-ManifestValuesFromFile {", 1
)[1].split("function Assert-HandoffArtifacts {", 1)[0]
numeric_match = re.search(
    r"(?ms)^\s+\$numericKeys = @\((.*?)^\s+\)\s*$",
    manifest_reader,
)
require(
    numeric_match is not None,
    "ADR-056 manifest numeric type boundary is missing",
)
numeric_keys = tuple(
    re.findall(
        r'^\s+"([A-Za-z][A-Za-z0-9]*)",?\s*$',
        numeric_match.group(1),
        re.MULTILINE,
    )
)
require(
    numeric_keys == (
        "schemaVersion",
        "featureAheadCount",
        "bundleBytes",
        "bundlePrerequisiteCount",
    )
    and '$numericKeys -contains $key' in manifest_reader
    and '[Text.Json.JsonValueKind]::Number' in manifest_reader
    and '[Text.Json.JsonValueKind]::String' in manifest_reader
    and '$properties[$index].Value.TryGetInt64([ref] $integerValue)'
    in manifest_reader
    and 'The handoff manifest numeric type changed at $key.'
    in manifest_reader
    and 'The handoff manifest integer syntax changed at $key.'
    in manifest_reader
    and 'The handoff manifest string type changed at $key.'
    in manifest_reader,
    "ADR-056 strict 4-number/18-string manifest type contract changed",
)

process_isolation_markers = (
    '$startInfo.FileName = $script:GitCommand',
    '$startInfo.UseShellExecute = $false',
    '$startInfo.Environment["GIT_CONFIG_NOSYSTEM"] = "1"',
    '$startInfo.Environment["GIT_CONFIG_GLOBAL"] = $script:NullDevice',
    '$startInfo.Environment["GIT_TERMINAL_PROMPT"] = "0"',
    '$startInfo.Environment["GIT_NO_LAZY_FETCH"] = "1"',
    '$startInfo.Environment["GIT_OPTIONAL_LOCKS"] = "0"',
    '$startInfo.Environment["GIT_ASKPASS"] = $script:NullDevice',
    '$startInfo.Environment["GIT_ALLOW_PROTOCOL"] = "file"',
    '"-c", "maintenance.auto=0"',
    '"-c", "core.hooksPath=$($script:NullDevice)"',
    '"-c", "core.fsmonitor=false"',
    '"-c", "credential.helper="',
    '"-c", "protocol.file.allow=always"',
    '"-c", "protocol.http.allow=never"',
    '"-c", "protocol.https.allow=never"',
    '"-c", "protocol.ssh.allow=never"',
    '"-c", "protocol.git.allow=never"',
)
require(
    all(marker in harness for marker in process_isolation_markers)
    and "(?i:GIT|GH|SSH|GCM|GITLAB|BITBUCKET)_" in harness
    and "(?i:HTTP|HTTPS|ALL|NO)_PROXY" in harness
    and '$gitResolutions = @(Get-Command git -CommandType Application -ErrorAction Stop)'
    in harness
    and '$gitResolutions.Count -ge 1' in harness
    and '$gitResolutions[0].Source' in harness
    and '$script:GitTimeoutMilliseconds = 120000' in harness
    and '$script:GitAcceptedOutputCharacterLimit = 1048576'
    in harness
    and 'GitOutputCharacterLimit' not in harness
    and '$process.StandardOutput.ReadToEndAsync()' in harness
    and '$process.StandardError.ReadToEndAsync()' in harness
    and '$process.WaitForExit($script:GitTimeoutMilliseconds)'
    in harness
    and '$process.Kill($true)' in harness
    and 'Assert-Condition $process.HasExited' in harness
    and '"$FailureMessage (timeout and termination failed)."'
    in harness
    and '$process.WaitForExit(5000)' in harness
    and '"$FailureMessage (termination timeout)."' in harness
    and 'Assert-Condition $exited "$FailureMessage (timeout)."'
    in harness
    and '$stdout.Length -le $script:GitAcceptedOutputCharacterLimit'
    in harness
    and '$stderr.Length -le $script:GitAcceptedOutputCharacterLimit'
    in harness,
    "ADR-056 local-only Git process isolation changed",
)
forbidden_network_fragments = (
    '"pull"',
    '"ls-remote"',
    "Invoke-WebRequest",
    "Invoke-RestMethod",
    "System.Net.Http",
    "https://",
    "ssh://",
    "git://",
    "@github.com",
)
require(
    not any(fragment in harness for fragment in forbidden_network_fragments)
    and not re.search(
        r"(?im)^\s*(?:curl|curl\.exe|wget|gh|hub)\b",
        harness,
    ),
    "ADR-056 harness gained a network/origin contact path",
)

approved_seed_init = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"init", "--bare", $remoteRepository) '
    '-FailureMessage "Could not initialize the owned '
    'simulated bare remote"'
)
approved_seed_fetch = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"--git-dir=$remoteRepository", "fetch", "--no-tags", '
    '"--no-write-fetch-head", "--no-recurse-submodules", '
    '$repositoryRoot, '
    '"refs/remotes/origin/main:refs/heads/main", '
    '"refs/remotes/origin/develop:refs/heads/develop") '
    '-FailureMessage "Could not seed the simulated remote from '
    'the approved cached refs"'
)
approved_seed_head = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"--git-dir=$remoteRepository", "symbolic-ref", '
    '"HEAD", "refs/heads/develop")'
)
approved_seed_head_verification = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"--git-dir=$remoteRepository", "symbolic-ref", '
    '"--quiet", "HEAD")'
)
approved_seed_remotes = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"--git-dir=$remoteRepository", "remote")'
)
approved_empty_seed_inventory = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"--git-dir=$remoteRepository", "for-each-ref", '
    '"--format=%(refname)")'
)
approved_seed_inventory = (
    '-WorkingDirectory $temporaryRoot -Arguments @('
    '"--git-dir=$remoteRepository", "for-each-ref", '
    '"--format=%(refname)%00%(objectname)")'
)
forbidden_seed_fragments = (
    '"--mirror"',
    '"--all"',
    '"--tags"',
    '"--recurse-submodules"',
    '"--filter"',
    '"remote", "add"',
    '"+refs/remotes/origin/',
    '"update-ref", "-d"',
    '"update-ref", "--delete"',
    '"branch", "-D"',
    '"tag", "-d"',
)
require(
    compact_harness.count(approved_seed_init) == 1
    and compact_harness.count(approved_empty_seed_inventory) == 1
    and compact_harness.count(approved_seed_fetch) == 1
    and compact_harness.count(approved_seed_head) == 1
    and compact_harness.count(
        approved_seed_head_verification
    ) == 1
    and compact_harness.count(approved_seed_remotes) == 1
    and compact_harness.count(approved_seed_inventory) == 1
    and compact_harness.count(
        'Assert-GitRepositoryComplete $remoteRepository '
        '"Simulated remote"'
    ) == 1
    and compact_harness.count('"fetch"') == 1
    and (
        compact_harness.index(approved_seed_init)
        < compact_harness.index(approved_empty_seed_inventory)
        < compact_harness.index(approved_seed_fetch)
        < compact_harness.index(approved_seed_head)
        < compact_harness.index(approved_seed_head_verification)
        < compact_harness.index(approved_seed_remotes)
        < compact_harness.index(approved_seed_inventory)
        < compact_harness.index(
            'Assert-GitRepositoryComplete $remoteRepository '
            '"Simulated remote"'
        )
    )
    and all(
        harness.count(marker) == 1
        for marker in (
            '"--no-tags", "--no-write-fetch-head", '
            '"--no-recurse-submodules"',
            '"refs/remotes/origin/main:refs/heads/main"',
            '"refs/remotes/origin/develop:refs/heads/develop"',
        )
    )
    and '$initialRemoteRefs.Count -eq 0' in harness
    and (
        'The newly initialized simulated remote unexpectedly '
        'contains refs.'
    ) in harness
    and '-ceq "refs/heads/develop"' in harness
    and 'The simulated remote HEAD does not select develop.'
    in harness
    and '$seededRemotes.Count -eq 0' in harness
    and 'The simulated remote unexpectedly persisted a remote.'
    in harness
    and '$seededRefs.Count -eq 2' in harness
    and (
        '$seededRefs -contains '
        '"refs/heads/develop`0$cachedOriginDevelop"'
    ) in harness
    and (
        '$seededRefs -contains '
        '"refs/heads/main`0$cachedOriginMain"'
    ) in harness
    and '"--format=%(refname)%00%(objectname)"' in harness
    and not any(
        fragment in compact_harness
        for fragment in forbidden_seed_fragments
    ),
    "ADR-056 approved cached-ref-only bare seed boundary changed",
)

forbidden_git_mutation_fragments = (
    '"reset"',
    '"stash"',
    '"restore"',
    '"clean"',
    '"--force"',
    '"-f"',
)
require(
    not any(
        fragment in harness
        for fragment in forbidden_git_mutation_fragments
    )
    and harness.count('"switch"') == 4
    and harness.count('"checkout"') == 1
    and harness.count('"push"') == 4
    and harness.count('$remoteRepository,') >= 5
    and compact_harness.count(
        '-WorkingDirectory $integrationRepository -Arguments @('
    ) >= 20
    and (
        '-WorkingDirectory $serverRepository -Arguments @('
        '"checkout", "--detach", $mainReleaseCommit)'
    ) in compact_harness
    and not re.search(
        r'(?i)(?:ReadAllText|ReadAllBytes|Get-Content|Copy-Item|'
        r'Get-Item|Test-Path)[^\n]*["\']\.env["\']',
        harness,
    ),
    "ADR-056 source reset/switch/stash/ref/file safety changed",
)

source_custody_markers = (
    'Head          = $head',
    'SymbolicRef   = $symbolic',
    'StatusSha256  = Get-TextSha256 $status',
    'RefsSha256    = Get-TextSha256 $refs',
    'ConfigSha256  = Get-TextSha256 $config',
    'IndexSha256   = Get-FileSha256 $indexPath',
    'NextSha256    = Get-FileSha256 $NextDeclarationPath',
    'NextByteCount = ([IO.FileInfo] $NextDeclarationPath).Length',
    '" M apps/web/next-env.d.ts"',
    'Assert-SourceSnapshotUnchanged',
    'Remove-OwnedTemporaryRoot',
    '^wsr-release-handoff-[0-9a-f]{24}$',
    'Refused to remove a reparse-point temporary root.',
    '$ownerToken = (New-RunId) + (New-RunId)',
    '^[0-9a-f]{48}$',
    '.wsr-release-handoff-owner',
    '[IO.FileMode]::CreateNew',
    '$ownerMarkerStream.Flush($true)',
    'Refused to remove a temporary root without its owner marker.',
    'Refused a reparse-point temporary owner marker.',
    'Refused a temporary root whose owner marker changed.',
    '-ExpectedOwnerToken $ownerToken',
)
require(
    all(marker in harness for marker in source_custody_markers)
    and 'Assert-GitRepositoryComplete $repositoryRoot "Source repository"'
    in harness
    and 'Assert-GitRepositoryComplete $serverRepository "Offline server checkout"'
    in harness
    and '"fsck", "--full", "--strict", "--no-dangling"'
    in harness
    and '"extensions.partialClone"' in harness
    and "'^remote\\..*\\.promisor$'" in harness
    and "'^remote\\..*\\.partialclonefilter$'" in harness
    and 'objects/info/alternates' in harness
    and 'objects/info/http-alternates' in harness
    and 'info/grafts' in harness
    and '"rev-parse", "--git-path", $relativeObjectPath'
    in harness
    and (
        "'^(core\\.fsmonitor|uploadpack\\.packobjectshook|"
        "filter\\..*\\.(clean|smudge|process))$'"
    ) in compact_harness
    and '$executableConfig.ExitCode -eq 1' in harness
    and '(Get-OutputLines $executableConfig.Stdout).Count -eq 0'
    in harness
    and 'has executable local Git configuration' in harness
    and '"refs/replace"' in harness,
    "ADR-056 source/clone custody or full-object guard changed",
)
require(
    '$pathComparison = if ($IsWindows)' in harness
    and '[StringComparison]::OrdinalIgnoreCase' in harness
    and '[StringComparison]::Ordinal' in harness
    and '$reportedRepositoryRoot.Equals($repositoryRoot, $pathComparison)'
    in harness
    and '$scriptPath.Equals($expectedScriptPath, $pathComparison)'
    in harness
    and 'Run the exact committed release-handoff harness path.'
    in harness
    and '[IO.Path]::GetDirectoryName($resolvedRoot).Equals('
    in harness
    and '$resolvedParent,' in harness
    and '$comparison' in harness,
    "ADR-056 OS-aware exact repository/script/cleanup path boundary changed",
)

git_flow_markers = (
    "^feature/[a-z0-9][a-z0-9._/-]*$",
    '"refs/heads/main^{commit}"',
    '"refs/heads/develop^{commit}"',
    '"refs/remotes/origin/main^{commit}"',
    '"refs/remotes/origin/develop^{commit}"',
    '$localMain -ceq $cachedOriginMain',
    'Assert-GitAncestor $repositoryRoot $cachedOriginDevelop $localDevelop',
    'Assert-GitAncestor $repositoryRoot $cachedOriginMain $localDevelop',
    'Assert-GitAncestor $repositoryRoot $localDevelop $sourceCommit',
    '"rev-list", "--count", "$localDevelop..$sourceCommit"',
    '"init", "--bare", $remoteRepository',
    '"--git-dir=$remoteRepository", "fetch"',
    '"--no-tags", "--no-write-fetch-head", "--no-recurse-submodules"',
    '"refs/remotes/origin/main:refs/heads/main"',
    '"refs/remotes/origin/develop:refs/heads/develop"',
    '"clone", "--no-local", "--no-hardlinks", '
    '$remoteRepository, $integrationRepository',
    '"bundle", "create", $candidateInputBundle',
    '"refs/heads/develop", $sourceRef',
    '"merge", "--no-ff", "--no-edit", $sourceCommit',
    '$integrationParents[0] -ceq $localDevelop',
    '$integrationParents[1] -ceq $sourceCommit',
    '$releaseBranch = "release/0.0.0-rehearsal.$runId"',
    '$rehearsalTag = "v0.0.0-rehearsal.$runId"',
    '"commit", "--allow-empty", "--message"',
    '$releaseParents[0] -ceq $integrationCommit',
    '$mainParents[0] -ceq $cachedOriginMain',
    '$mainParents[1] -ceq $releasePreparationCommit',
    '$developParents[0] -ceq $integrationCommit',
    '$developParents[1] -ceq $releasePreparationCommit',
    '"tag", "--annotate", $rehearsalTag',
    '"cat-file", "-t", $tagObject',
    'A simulated Git Flow commit changed the candidate tree.',
)
require(
    all(marker in compact_harness for marker in git_flow_markers)
    and compact_harness.count(
        '"merge", "--no-ff", "--no-edit"'
    ) == 3
    and compact_harness.count(
        '"push", "--porcelain", $remoteRepository'
    ) == 4,
    "ADR-056 exact temporary Git Flow graph contract changed",
)

artifact_markers = (
    '$values["schemaVersion"] -eq 1',
    '$values["project"] -ceq "wall-street-receipts"',
    '$values["releaseStatus"] -ceq "NOT_RELEASED"',
    '$values["networkStatus"] -ceq "REMOTE_NOT_CONTACTED"',
    '$values["bundlePrerequisiteCount"] -eq 0',
    '"wall-street-receipts-$mainReleaseCommit.bundle"',
    '"manifest.json"',
    '"refs/tags/$rehearsalTag"',
    '"bundle", "verify", $BundlePath',
    '"bundle", "list-heads", $BundlePath',
    '$headLines.Count -eq 1',
    '-WorkingDirectory $VerifierRoot -Arguments @('
    '"bundle", "unbundle", $BundlePath)',
    '"update-ref", $values["bundleRef"], $values["tagObject"]',
    '"cat-file", "-t", $values["tagObject"]',
    '$values["mainReleaseCommit"]',
    '$values["sourceTree"]',
    'The imported handoff bundle identity changed.',
    'The imported handoff bundle failed full object verification',
    'Get-ManifestValuesFromFile',
    'ConvertTo-CanonicalManifestText',
    '$bytes.Length -ge 2 -and $bytes.Length -le 8192',
    '$bytes[-1] -eq 10',
    'The handoff manifest must be printable ASCII plus final LF.',
    'Copy-FlippedFile $bundlePath $flippedBundle',
    'A byte-flipped bundle was accepted.',
    'Copy-TruncatedFile $bundlePath $truncatedBundle',
    '$truncatedValues["bundleSha256"] = Get-FileSha256 $truncatedBundle',
    'A truncated, rehashed bundle was accepted.',
)
require(
    all(marker in compact_harness for marker in artifact_markers)
    and '"$BundleSha256 *$BundleName`n"' in harness
    and '"bundle", "create", $bundlePath, "refs/tags/$rehearsalTag"'
    in compact_harness
    and '[Parameter(Mandatory)][string] $ExpectedMessagePattern'
    in harness
    and '$_.Exception.Message -cmatch $ExpectedMessagePattern'
    in harness
    and 'The negative artifact failed for an unexpected reason.'
    in harness
    and (
        "-ExpectedMessagePattern '^The handoff bundle SHA-256 "
        "changed\\.$'"
    ) in compact_harness
    and (
        "-ExpectedMessagePattern '^The handoff bundle "
        "(is structurally incomplete|could not be fully imported) "
        "\\(exit code [1-9][0-9]*\\)\\.$'"
    ) in compact_harness,
    "ADR-056 bundle/manifest/receipt/corruption contract changed",
)

offline_markers = (
    '[IO.Directory]::CreateDirectory($serverRepository)',
    '-WorkingDirectory $serverRepository -Arguments @("init", ".")',
    '-WorkingDirectory $serverRepository -Arguments @('
    '"bundle", "verify", $bundlePath)',
    '-WorkingDirectory $serverRepository -Arguments @('
    '"bundle", "unbundle", $bundlePath)',
    '"update-ref", "refs/tags/$rehearsalTag", $tagObject',
    '"checkout", "--detach", $mainReleaseCommit',
    '"status", "--porcelain=v1", "--untracked-files=all"',
    '"symbolic-ref", "--quiet", "HEAD"',
    '$serverSymbolicHead.ExitCode -eq 1',
    '"remote"',
    '$serverRemotes.Stdout',
    'The offline server checkout unexpectedly has a remote.',
    'HEAD:apps/web/next-env.d.ts',
    '"scripts/verify-local-release-handoff.ps1"',
    '"decisions/ADR-056-disposable-offline-git-flow-release-source-handoff-rehearsal.md"',
)
require(
    all(marker in compact_harness for marker in offline_markers)
    and '$sourceCommit`:apps/web/next-env.d.ts' in harness
    and 'Write-Host "NOT_RELEASED"' in harness
    and 'Write-Host "REMOTE_NOT_CONTACTED"' in harness
    and '"HANDOFF_EVIDENCE|source="' in harness,
    "ADR-056 offline clone/committed-Next/evidence boundary changed",
)

required_document_markers = {
    Path("README.md"): (
        "ADR-056 adds a disposable offline rehearsal",
        "pwsh -NoProfile -File ./scripts/verify-local-release-handoff.ps1",
        "complete no-prerequisite tag-only Git bundle",
        "apps/web/next-env.d.ts",
        "disable lazy fetch",
        "optional locks",
        "bounded time/output",
        "unpredictable owner marker",
        "checksum receipt covers only the named bundle bytes",
        "complete three-file set",
        "manifest-only Git Flow",
        "featureAheadCount",
        "can pass a later retained-artifact import",
        "independently reviewed manifest, commit/digest record, or signature",
        "No API key, domain, server, Docker daemon, GitHub login, or network permission",
        "Actual remote work later requires explicit",
        "repository visibility",
        "release-version",
        "authentication configured locally",
    ),
    Path("deploy/home-server/README.md"): (
        "## Release-source handoff before server work",
        "pwsh -NoProfile -File ./scripts/verify-local-release-handoff.ps1",
        "complete no-prerequisite tag-only bundle",
        "apps/web/next-env.d.ts",
        "disables lazy fetch and optional locks",
        "maintenance.auto=0",
        "core.fsmonitor=false",
        "uploadpack.packobjectshook",
        "filter.*.(clean|smudge|process)",
        "Each child has a two-minute deadline",
        "terminate within five more seconds",
        "rejected above 1,048,576 characters",
        "flushed",
        "owner marker",
        "checksum receipt covers only the named bundle bytes",
        "complete three-file set",
        "manifest-only Git",
        "featureAheadCount",
        "can pass",
        "independently reviewed manifest",
        "commit/digest",
        "signature",
        "No API key, domain, server, Docker daemon, GitHub login, network authorization",
        "### Later GitHub alternative",
        "public or private",
        "v0.1.0-rc.1",
        "configure GitHub authentication locally",
    ),
    Path("IMPLEMENTATION_LOG.md"): (
        "## 2026-08-31 — ADR-056 disposable offline Git Flow release-source handoff rehearsal",
        "scripts/verify-local-release-handoff.ps1",
        "apps/web/next-env.d.ts",
        "manifest.json",
        "canonical JSON",
        "git fsck",
        "GIT_NO_LAZY_FETCH=1",
        "GIT_OPTIONAL_LOCKS=0",
        "maintenance.auto=0",
        "core.fsmonitor=false",
        "Give every Git child 120 seconds",
        "require exit within five more seconds",
        "above 1,048,576 characters",
        "partial-clone-filter",
        "HTTP-alternate",
        "uploadpack.packobjectshook",
        "filter.*.(clean|smudge|process)",
        "flushed 48-hex owner marker",
        "`clone --mirror`",
        "initialize an empty bare repository",
        "zero-ref inventory",
        "fetching only cached",
        "two non-forcing explicit refspecs",
        "non-forcing explicit refspecs with tags",
        "`FETCH_HEAD`, and submodules disabled",
        "exactly those two refs",
        "rechecks `HEAD` as `develop`",
        "zero persisted remotes",
        "complete-repository/strict-`fsck` gate",
        "receipt names and hashes only",
        "complete three-file set",
        "manifest-only Git Flow identity",
        "featureAheadCount",
        "syntactically valid change",
        "independently",
        "manifest/commit digest or signature",
        "### External inputs and next work",
        "repository-visibility decision",
        "v0.1.0-rc.1",
        "GitHub authentication configured",
    ),
    adr_path: (
        "# ADR-056: Disposable offline Git Flow release-source handoff rehearsal",
        "accepts no GitHub credential, remote ref, release version",
        "complete, no-prerequisite, tag-only Git bundle",
        "exactly these 22",
        "apps/web/next-env.d.ts",
        "GIT_NO_LAZY_FETCH=1",
        "GIT_OPTIONAL_LOCKS=0",
        "maintenance.auto=0",
        "core.fsmonitor=false",
        "120-second deadline",
        "terminate within another five",
        "longer than 1,048,576 characters",
        "uploadpack.packobjectshook",
        "filter.*.(clean|smudge|process)",
        "non-HTTP-alternate",
        "non-graft",
        "checksum receipt hashes only the named bundle bytes",
        "complete three-file set",
        "manifest-only cached/local/generated Git Flow identity",
        "featureAheadCount",
        "syntactically valid change",
        "Preserve an independently",
        "reviewed manifest and commit/digest",
        "separately designed signature",
        "### Later GitHub alternative",
        "repository visibility",
        "v0.1.0-rc.1",
        "GitHub authentication is configured locally",
    ),
}
for path, markers in required_document_markers.items():
    require(
        all(marker in documentation[path] for marker in markers)
        and "NOT_RELEASED" in documentation[path]
        and "REMOTE_NOT_CONTACTED" in documentation[path],
        f"ADR-056 documentation/external-input parity changed: {path}",
    )
require(
    all(
        "reproducible" in documentation[path].lower()
        and (
            "does not" in documentation[path].lower()
            or "not " in documentation[path].lower()
            or "outside" in documentation[path].lower()
        )
        for path in documentation_paths
    ),
    "ADR-056 docs must not imply reproducible binary/image custody",
)

parse_block = workflow.split(parse_marker, 1)[1].split(
    "\n      - name:", 1
)[0]
require(
    parse_block.count(harness_path.as_posix()) == 1
    and parse_block.count(
        "[System.Management.Automation.Language.Parser]::ParseFile("
    ) == 1
    and "-File" not in parse_block
    and "& " not in parse_block,
    "ADR-056 CI must parse the exact harness once without executing it",
)
workflow_behavior = workflow
for marker in (guard_marker, parse_marker):
    start = workflow_behavior.index(marker)
    next_step = workflow_behavior.find("\n      - name:", start + len(marker))
    require(next_step != -1, "ADR-056 CI step boundary changed")
    workflow_behavior = (
        workflow_behavior[:start] + workflow_behavior[next_step + 1:]
    )
execution_patterns = (
    r"(?m)^\s+run:\s*pwsh(?:\.exe)?\s+.*-File\s+"
    r"\.?/?scripts/verify-local-release-handoff\.ps1\s*$",
    r"(?m)^\s+pwsh(?:\.exe)?\s+.*-File\s+"
    r"\.?/?scripts/verify-local-release-handoff\.ps1\s*$",
)
require(
    all(
        re.search(pattern, workflow_behavior) is None
        for pattern in execution_patterns
    ),
    "Hosted CI must not execute the branch-dependent ADR-056 harness",
)

print(
    "Validated ADR-056 no-input local-only source custody, exact "
    "approved-ref-only bare seed, temporary Git Flow parent/tree "
    "graph, canonical 22-field "
    "tag-only bundle handoff, two corruption rejections, offline "
    "complete checkout, docs/external-input honesty, and parse-only CI"
)
PYTHON

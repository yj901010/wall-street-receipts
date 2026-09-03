$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path "scripts/verify-local-release-handoff.ps1").Path,
  [ref] $tokens,
  [ref] $parseErrors
) | Out-Null
if ($parseErrors.Count -ne 0) {
  throw "PowerShell parser reported $($parseErrors.Count) error(s) in the disposable offline release-source handoff rehearsal."
}

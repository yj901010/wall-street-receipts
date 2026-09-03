$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path "scripts/verify-local-full-stack.ps1").Path,
  [ref] $tokens,
  [ref] $parseErrors
) | Out-Null
if ($parseErrors.Count -ne 0) {
  throw "PowerShell parser reported $($parseErrors.Count) error(s) in the local full-stack acceptance harness."
}

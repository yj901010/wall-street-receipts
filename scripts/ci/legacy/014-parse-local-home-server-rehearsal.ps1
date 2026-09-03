$allErrors = @()
foreach ($path in @(
  "scripts/verify-home-server-deployment.ps1",
  "scripts/verify-home-server-recovery.ps1"
)) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile(
    (Resolve-Path $path),
    [ref] $tokens,
    [ref] $errors
  ) | Out-Null
  $allErrors += $errors
}
if ($allErrors.Count -gt 0) {
  $allErrors | ForEach-Object { Write-Error $_.Message }
  exit 1
}

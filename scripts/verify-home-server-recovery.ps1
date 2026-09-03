[CmdletBinding()]
param(
    [ValidateRange(120, 1800)]
    [int] $StartupTimeoutSeconds = 600,

    [switch] $RunBrowserSuite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$deploymentRehearsal = Join-Path $PSScriptRoot "verify-home-server-deployment.ps1"
if (-not (Test-Path -LiteralPath $deploymentRehearsal -PathType Leaf)) {
    throw "The ADR-046 deployment rehearsal is missing."
}

& $deploymentRehearsal `
    -StartupTimeoutSeconds $StartupTimeoutSeconds `
    -RunBrowserSuite:$RunBrowserSuite `
    -RunRecoverySuite

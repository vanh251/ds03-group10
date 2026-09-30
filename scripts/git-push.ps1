param([Parameter(Mandatory=$true,Position=0)][string]$Message)
$ErrorActionPreference="Stop"
function Fail($Message){Write-Host "[ERROR] $Message" -ForegroundColor Red; exit 1}
$current=(git branch --show-current).Trim()
if(!$current){Fail "Could not determine current branch."}
if($current -eq "main" -or $current -eq "develop"){Fail "Direct commits to $current are disabled. Use a feature branch + PR."}
if(!$Message){Fail "Commit message cannot be empty."}
git status
$confirm=Read-Host "Commit all changes on '$current'? (y/N)"
if($confirm -notmatch "^[Yy]$"){Write-Host "Cancelled."; exit 0}
git add .
git commit -m $Message
git push -u origin $current
Write-Host "[OK] Committed and pushed $current." -ForegroundColor Green

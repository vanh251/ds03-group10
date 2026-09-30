param(
    [Parameter(Mandatory=$true,Position=0)][ValidateSet("m1","m2","m3")][string]$Member,
    [Parameter(Mandatory=$true,Position=1)][string]$Task
)
$ErrorActionPreference="Stop"
function Fail($Message){Write-Host "[ERROR] $Message" -ForegroundColor Red; exit 1}
if((git status --porcelain)){Fail "Working tree is not clean. Commit or stash changes first."}
git checkout develop
git pull --rebase origin develop
$branch="feature/$Member/$Task"
if(git branch --list $branch){Fail "Local branch '$branch' already exists."}
if(git ls-remote --exit-code --heads origin $branch 2>$null){Fail "Remote branch '$branch' already exists."}
git checkout -b $branch
git push -u origin $branch
Write-Host "[OK] Created and pushed $branch" -ForegroundColor Green

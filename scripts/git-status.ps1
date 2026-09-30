$ErrorActionPreference="Stop"
Write-Host "== Repository status ==" -ForegroundColor Cyan
git status
Write-Host "`n== Recent commits ==" -ForegroundColor Cyan
git log --oneline --decorate -8
Write-Host "`n== Branches ==" -ForegroundColor Cyan
git branch -vv

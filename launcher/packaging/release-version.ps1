function Resolve-ReleaseVersion {
    param(
        [AllowNull()]
        [string]$RequestedVersion,
        [Parameter(Mandatory)]
        [string]$RepositoryRoot
    )

    $version = $RequestedVersion
    if ([string]::IsNullOrWhiteSpace($version)) {
        [xml]$pom = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'pom.xml') -Raw
        $namespaceManager = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
        $namespaceManager.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
        $revision = $pom.SelectSingleNode('/m:project/m:properties/m:revision', $namespaceManager)
        if ($null -eq $revision -or [string]::IsNullOrWhiteSpace($revision.InnerText)) {
            throw 'Die Maven-Property revision im Root-POM muss eine Releaseversion bereitstellen.'
        }
        $version = $revision.InnerText.Trim()
    }

    if ($version -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
        throw "Die Releaseversion '$version' muss dem Format X.Y.Z entsprechen."
    }
    return $version
}

function Get-SyntheticUpgradeVersion {
    param([Parameter(Mandatory)][string]$Version)

    $segments = $Version.Split('.')
    try {
        $patch = [UInt64]::Parse($segments[2], [System.Globalization.CultureInfo]::InvariantCulture)
        if ($patch -eq [UInt64]::MaxValue) {
            throw 'Die Patch-Version kann nicht für den synthetischen Upgrade-Smoke erhöht werden.'
        }
        return "$($segments[0]).$($segments[1]).$($patch + 1)"
    } catch [System.OverflowException] {
        throw "Die Patch-Version '$($segments[2])' ist für den synthetischen Upgrade-Smoke zu groß."
    }
}

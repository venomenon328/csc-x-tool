[CmdletBinding()]
param(
    [string]$SourcePath
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($SourcePath)) {
    $SourcePath = Join-Path $repositoryRoot 'branding\source\csc-x-tool-original.png'
}

$expectedHash = '231712672d01890bbaccf30f92f93096ba141f14680a3f44ce34914fecb1be2e'
if (-not (Test-Path -LiteralPath $SourcePath -PathType Leaf)) {
    throw "Die freigegebene Branding-Quelle fehlt: $SourcePath"
}

$actualHash = (Get-FileHash -LiteralPath $SourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $expectedHash) {
    throw "Die Branding-Quelle hat nicht die erwartete SHA-256-Prüfsumme. Erwartet: $expectedHash; gefunden: $actualHash"
}

Add-Type -AssemblyName PresentationCore

function Save-Png {
    param(
        [System.Windows.Media.Imaging.BitmapSource]$Bitmap,
        [string]$Path
    )

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($Bitmap))
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $encoder.Save($stream)
    } finally {
        $stream.Dispose()
    }
}

function Convert-ToPngBytes {
    param([System.Windows.Media.Imaging.BitmapSource]$Bitmap)

    $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($Bitmap))
    $stream = New-Object System.IO.MemoryStream
    try {
        $encoder.Save($stream)
        Write-Output -NoEnumerate $stream.ToArray()
    } finally {
        $stream.Dispose()
    }
}

function Resize-AndCenter {
    param(
        [System.Windows.Media.Imaging.BitmapSource]$Bitmap,
        [int]$Size
    )

    $scale = [Math]::Min($Size / $Bitmap.PixelWidth, $Size / $Bitmap.PixelHeight)
    $width = $Bitmap.PixelWidth * $scale
    $height = $Bitmap.PixelHeight * $scale
    $visual = New-Object System.Windows.Media.DrawingVisual
    $context = $visual.RenderOpen()
    try {
        $destination = [System.Windows.Rect]::new(
            (([double]$Size - [double]$width) / 2),
            (([double]$Size - [double]$height) / 2),
            [double]$width,
            [double]$height
        )
        $context.DrawImage($Bitmap, $destination)
    } finally {
        $context.Close()
    }

    $target = [System.Windows.Media.Imaging.RenderTargetBitmap]::new($Size, $Size, 96, 96, [System.Windows.Media.PixelFormats]::Pbgra32)
    $target.Render($visual)
    $target.Freeze()
    return $target
}

function Write-MultiResolutionIco {
    param(
        [System.Windows.Media.Imaging.BitmapSource]$Bitmap,
        [int[]]$Sizes,
        [string]$Path
    )

    $images = [System.Collections.Generic.List[byte[]]]::new()
    foreach ($size in $Sizes) {
        [void]$images.Add((Convert-ToPngBytes -Bitmap (Resize-AndCenter -Bitmap $Bitmap -Size $size)))
    }
    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    $writer = New-Object System.IO.BinaryWriter($stream)
    try {
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]$images.Count)
        $offset = 6 + (16 * $images.Count)
        for ($index = 0; $index -lt $images.Count; $index++) {
            $size = $Sizes[$index]
            $dimension = if ($size -eq 256) { [byte]0 } else { [byte]$size }
            $writer.Write($dimension)
            $writer.Write($dimension)
            $writer.Write([byte]0)
            $writer.Write([byte]0)
            $writer.Write([UInt16]1)
            $writer.Write([UInt16]32)
            $writer.Write([UInt32]$images[$index].Length)
            $writer.Write([UInt32]$offset)
            $offset += $images[$index].Length
        }
        foreach ($image in $images) {
            $writer.Write($image)
        }
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

$decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create(
    [Uri]$SourcePath,
    [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,
    [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad
)
$source = $decoder.Frames[0]
if ($source.PixelWidth -ne 1254 -or $source.PixelHeight -ne 1254) {
    throw "Die Branding-Quelle muss 1254 × 1254 px groß sein, gefunden: $($source.PixelWidth) × $($source.PixelHeight) px."
}

$bitmap = New-Object System.Windows.Media.Imaging.FormatConvertedBitmap(
    $source,
    [System.Windows.Media.PixelFormats]::Bgra32,
    $null,
    0
)
$stride = $bitmap.PixelWidth * 4
$pixels = New-Object byte[] ($stride * $bitmap.PixelHeight)
$bitmap.CopyPixels($pixels, $stride, 0)
$minX = $bitmap.PixelWidth
$minY = $bitmap.PixelHeight
$maxX = -1
$maxY = -1
for ($y = 0; $y -lt $bitmap.PixelHeight; $y++) {
    for ($x = 0; $x -lt $bitmap.PixelWidth; $x++) {
        if ($pixels[($y * $stride) + ($x * 4) + 3] -ne 0) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
if ($maxX -lt 0) {
    throw 'Die Branding-Quelle enthält keine sichtbaren Pixel.'
}

$cropRectangle = [System.Windows.Int32Rect]::new(
    [int]$minX,
    [int]$minY,
    ([int]$maxX - [int]$minX + 1),
    ([int]$maxY - [int]$minY + 1)
)
$cropped = [System.Windows.Media.Imaging.CroppedBitmap]::new($bitmap, $cropRectangle)
$cropped.Freeze()

$webLogo = Join-Path $repositoryRoot 'frontend\src\assets\csc-x-tool-logo.png'
$favicon = Join-Path $repositoryRoot 'frontend\public\csc-x-tool.ico'
$launcherIcon = Join-Path $repositoryRoot 'launcher\assets\csc-x-tool.ico'

Save-Png -Bitmap $cropped -Path $webLogo
Write-MultiResolutionIco -Bitmap $cropped -Sizes @(16, 32, 48, 64, 128, 256) -Path $favicon
Copy-Item -LiteralPath $favicon -Destination $launcherIcon -Force

Write-Host "Branding-Assets aktualisiert aus der verifizierten Quelle: $SourcePath"

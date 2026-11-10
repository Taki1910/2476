[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string] $Username = 'manager.demo',
    [string] $Password = $env:S1_DEMO_PASSWORD,
    [switch] $ConfirmDisposableDemo
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

if (-not $ConfirmDisposableDemo) { throw 'Refusing to change data without -ConfirmDisposableDemo.' }
if ($Username -ne 'manager.demo') { throw 'This fixture is restricted to manager.demo.' }
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Set S1_DEMO_PASSWORD or pass -Password.' }

$session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()

function Invoke-Api {
    param(
        [ValidateSet('GET', 'POST', 'PUT')] [string] $Method,
        [string] $Path,
        [object] $Body,
        [switch] $Form,
        [switch] $Csrf
    )
    $headers = @{}
    if ($Csrf) {
        $token = Invoke-RestMethod -Uri "$BaseUrl/auth/csrf" -Method GET -WebSession $session
        $headers[$token.headerName] = $token.token
    }
    $request = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        WebSession = $session
        Headers = $headers
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $request.Body = if ($Form) { $Body } else { $Body | ConvertTo-Json -Depth 12 }
        $request.ContentType = if ($Form) { 'application/x-www-form-urlencoded' } else { 'application/json' }
    }
    Invoke-RestMethod @request
}

function Require-One {
    param([object[]] $Items, [string] $Name)
    $matches = @($Items | Where-Object name -eq $Name)
    if ($matches.Count -ne 1) { throw "Expected exactly one demo product '$Name'; found $($matches.Count)." }
    $matches[0]
}

function Promotion-Shape {
    param($Promotion)
    [ordered]@{
        name = $Promotion.name
        status = $Promotion.status
        acquisitionMode = $Promotion.acquisitionMode
        publiclyDiscoverable = [bool]$Promotion.publiclyDiscoverable
        layer = $Promotion.layer
        effectType = $Promotion.effectType
        percentageValue = if ($null -eq $Promotion.percentageValue) { $null } else { [decimal]$Promotion.percentageValue }
        fixedAmount = $Promotion.fixedAmount
        minimumSpendAmount = $Promotion.minimumSpendAmount
        buyQuantity = $Promotion.buyQuantity
        globalUsageLimit = $Promotion.globalUsageLimit
        perCustomerUsageLimit = $Promotion.perCustomerUsageLimit
        priority = $Promotion.priority
        validFrom = $Promotion.validFrom
        validTo = $Promotion.validTo
        productIds = @($Promotion.productIds | Sort-Object)
        customerSummary = $Promotion.customerSummary
        customerTerms = $Promotion.customerTerms
        normalizedCode = $Promotion.normalizedCode
    }
}

function Section-Shape {
    param($Section)
    [ordered]@{
        type = $Section.type
        enabled = [bool]$Section.enabled
        titleVi = $Section.titleVi
        titleEn = $Section.titleEn
        supportingTextVi = $Section.supportingTextVi
        supportingTextEn = $Section.supportingTextEn
        ctaLabelVi = $Section.ctaLabelVi
        ctaLabelEn = $Section.ctaLabelEn
        ctaKind = $Section.ctaKind
        ctaTargetPublicId = $Section.ctaTargetPublicId
        ctaSearchTerm = $Section.ctaSearchTerm
        promotionFamilyId = $Section.promotionFamilyId
        mediaKey = $Section.mediaKey
        productIds = @($Section.productIds)
    }
}

function Fingerprint {
    param($Value)
    $Value | ConvertTo-Json -Depth 12 -Compress
}

$csrf = Invoke-RestMethod -Uri "$BaseUrl/auth/csrf" -Method GET -WebSession $session
$account = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -WebSession $session `
    -Headers @{ $csrf.headerName = $csrf.token } -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ username = $Username; password = $Password }
if ($account.login -ne 'manager.demo' -or
    $account.permissions -notcontains 'PROMOTION_MANAGE' -or
    $account.permissions -notcontains 'STOREFRONT_MANAGE') {
    throw 'Authenticated account is not the expected S1 demo manager.'
}

$products = [object[]](Invoke-Api GET '/storefront/products')
if ($products.Count -ne 18) { throw "Expected the disposable 18-product demo catalog; found $($products.Count)." }
$byName = @{}
@('After Dark', 'Court Classic', 'Court High', 'Basket Retro', 'Trail Form', 'Urban Hiker', 'Trail Edge', 'City Loafer') |
    ForEach-Object { $byName[$_] = Require-One $products $_ }
$expectedMedia = [ordered]@{
    'Basket Retro' = '/products/basket-retro.png'
    'Urban Hiker' = '/products/urban-hiker.png'
    'Trail Edge' = '/products/trail-edge.png'
    'City Loafer' = '/products/city-loafer.png'
}
foreach ($entry in $expectedMedia.GetEnumerator()) {
    $product = $byName[$entry.Key]
    if ($product.heroImage -ne $entry.Value -or $product.primaryImage -ne $entry.Value) {
        throw "Product media mismatch for '$($entry.Key)'. This is not the expected S1 demo catalog."
    }
}
Write-Host 'Verified disposable demo identity and 18-product S1 catalog.'

$promotionDraft = [ordered]@{
    familyId = $null
    revisionNumber = 1
    name = 'City Edit 15'
    layer = 'ORDER_AUTOMATIC'
    effectType = 'ORDER_PERCENT'
    percentageValue = 15
    fixedAmount = $null
    minimumSpendAmount = 1000000
    buyQuantity = $null
    globalUsageLimit = $null
    perCustomerUsageLimit = 1
    priority = 0
    validFrom = '2026-01-01T00:00:00Z'
    validTo = $null
    productIds = @()
    customerSummary = 'Save 15% on one eligible order.'
    customerTerms = 'Claim once per customer. Minimum spend ₫1,000,000. Final eligibility and savings are confirmed in cart.'
    acquisitionMode = 'CLAIMABLE'
    normalizedCode = $null
    publiclyDiscoverable = $true
}
$expectedPromotion = Promotion-Shape ([pscustomobject]($promotionDraft + @{ status = 'PUBLISHED' }))
$allPromotions = [object[]](Invoke-Api GET '/operations/promotions')
$namedPromotions = @($allPromotions | Where-Object name -eq 'City Edit 15')
if ($namedPromotions.Count -gt 1) { throw "Found $($namedPromotions.Count) promotions named 'City Edit 15'; refusing to choose or replace one." }
if ($namedPromotions.Count -eq 1) {
    $promotion = $namedPromotions[0]
    $actual = Promotion-Shape $promotion
    if ((Fingerprint $actual) -ne (Fingerprint $expectedPromotion)) {
        $differences = @($expectedPromotion.Keys | Where-Object {
            (Fingerprint $expectedPromotion[$_]) -ne (Fingerprint $actual[$_])
        })
        $details = $differences | ForEach-Object {
            "$_ (expected $(Fingerprint $expectedPromotion[$_]), actual $(Fingerprint $actual[$_]))"
        }
        throw "Promotion 'City Edit 15' conflicts: $($details -join '; '). Refusing to edit, retire, or replace it."
    }
    Write-Host "Reused canonical City Edit 15 family $($promotion.familyId)."
} else {
    $promotion = Invoke-Api POST '/operations/promotions' $promotionDraft -Csrf
    $promotion = Invoke-Api POST "/operations/promotions/$($promotion.id)/publish" @{ expectedPublishedRevisionId = $null } -Csrf
    Write-Host "Created and published canonical City Edit 15 family $($promotion.familyId)."
}

$sections = @(
    [ordered]@{
        type = 'HERO_BANNER'; enabled = $true
        titleVi = 'Bước vào nhịp phố đêm.'; titleEn = 'Step into the after-hours.'
        supportingTextVi = 'Sắc đen, đường chỉ hồng và phom thấp cho những giờ thành phố lên đèn.'
        supportingTextEn = "Black tones, pink stitching and a low profile for the city’s later hours."
        ctaLabelVi = 'Xem After Dark'; ctaLabelEn = 'View After Dark'; ctaKind = 'PRODUCT_DETAIL'
        ctaTargetPublicId = $byName['After Dark'].id; ctaSearchTerm = $null; promotionFamilyId = $null
        mediaKey = 'AFTER_DARK'; productIds = @()
    },
    [ordered]@{
        type = 'PRODUCT_COLLECTION'; enabled = $true; titleVi = 'Court Originals'; titleEn = 'Court Originals'
        supportingTextVi = 'Ba phom Court, từ cổ thấp tối giản đến dáng cổ cao nổi bật.'
        supportingTextEn = 'Three Court silhouettes, from a minimal low-top to a standout high-top.'
        ctaLabelVi = 'Xem dòng Court'; ctaLabelEn = 'Explore Court'; ctaKind = 'CATALOG_SEARCH'
        ctaTargetPublicId = $null; ctaSearchTerm = 'Court'; promotionFamilyId = $null; mediaKey = $null
        productIds = @($byName['Court Classic'].id, $byName['Court High'].id, $byName['Basket Retro'].id)
    },
    [ordered]@{
        type = 'PROMOTION_SPOTLIGHT'; enabled = $true
        titleVi = 'Ưu đãi cho đôi tiếp theo.'; titleEn = 'An offer for your next pair.'
        supportingTextVi = 'Lưu City Edit 15 rồi áp dụng trong giỏ hàng để hệ thống kiểm tra điều kiện.'
        supportingTextEn = 'Save City Edit 15 and apply it in cart for an eligibility check.'
        ctaLabelVi = 'Xem điều kiện'; ctaLabelEn = 'View terms'; ctaKind = 'PROMOTION_DETAIL'
        ctaTargetPublicId = $promotion.familyId; ctaSearchTerm = $null; promotionFamilyId = $promotion.familyId
        mediaKey = $null; productIds = @()
    },
    [ordered]@{
        type = 'PRODUCT_COLLECTION'; enabled = $true; titleVi = 'Beyond the pavement'; titleEn = 'Beyond the pavement'
        supportingTextVi = 'Ba phom Trail và ba câu chuyện màu sắc cho hành trình cuối tuần.'
        supportingTextEn = 'Three Trail silhouettes and three color stories for the weekend.'
        ctaLabelVi = 'Khám phá Trail'; ctaLabelEn = 'Explore Trail'; ctaKind = 'CATALOG_SEARCH'
        ctaTargetPublicId = $null; ctaSearchTerm = 'Trail'; promotionFamilyId = $null; mediaKey = $null
        productIds = @($byName['Trail Form'].id, $byName['Urban Hiker'].id, $byName['Trail Edge'].id)
    }
)

$state = Invoke-Api GET '/operations/storefront/homepage'
$expectedFingerprint = Fingerprint @($sections | ForEach-Object { Section-Shape ([pscustomobject]$_) })
$draftFingerprint = if ($null -eq $state.draft) { $null } else {
    Fingerprint @($state.draft.sections | Sort-Object position | ForEach-Object { Section-Shape $_ })
}
$publishedFingerprint = if ($null -eq $state.published) { $null } else {
    Fingerprint @($state.published.sections | Sort-Object position | ForEach-Object { Section-Shape $_ })
}

if ($draftFingerprint -eq $expectedFingerprint) {
    Write-Host "Canonical HOME draft revision $($state.draft.revisionId) already matches; no write performed."
} elseif ($null -eq $state.draft -and $publishedFingerprint -eq $expectedFingerprint) {
    Write-Host "Canonical HOME is already published as revision $($state.published.revisionId); no draft created."
} else {
    if ($null -ne $state.draft) {
        $differentPositions = 1..([Math]::Max($sections.Count, $state.draft.sections.Count)) | Where-Object {
            $expected = if ($_ -le $sections.Count) { Fingerprint (Section-Shape ([pscustomobject]$sections[$_ - 1])) } else { '<missing>' }
            $actualSection = @($state.draft.sections | Sort-Object position)[$_ - 1]
            $actual = if ($null -ne $actualSection) { Fingerprint (Section-Shape $actualSection) } else { '<missing>' }
            $expected -ne $actual
        }
        Write-Host "Reconciling disposable HOME draft; differing section positions: $($differentPositions -join ', ')."
    }
    $saved = Invoke-Api PUT '/operations/storefront/homepage/draft' @{
        expectedDraftRevisionId = $state.draft.revisionId
        expectedEntityVersion = $state.draft.entityVersion
        sections = $sections
    } -Csrf
    Write-Host "Prepared canonical HOME draft revision $($saved.revisionId), version $($saved.entityVersion)."
}

Write-Host 'Stopped before UI publication as required.'

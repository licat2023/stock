# Exercises the deployed REST API against MySQL. Use a dedicated Compose project.
#Requires -Version 7.0
[CmdletBinding()]
param(
    [uri]$BaseUrl = 'http://127.0.0.1:18081',
    [string]$ComposeProject
)

$ErrorActionPreference = 'Stop'
$api = $BaseUrl.AbsoluteUri.TrimEnd('/') + '/api'

function Invoke-StockApi {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Data,
        [int]$Expected = 200
    )

    $request = @{
        Uri = "$api/$Path"
        Method = $Method
        SkipHttpErrorCheck = $true
        TimeoutSec = 15
    }
    if ($null -ne $Data) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = $Data | ConvertTo-Json -Compress
    }
    $response = Invoke-WebRequest @request
    if ($response.StatusCode -ne $Expected) {
        throw "$Method $Path expected $Expected, got $($response.StatusCode). $($response.Content)"
    }
    if ($Expected -ge 400 -and -not $response.Content) {
        throw "$Method $Path returned an error without a message."
    }
    if ($response.Content) {
        $payload = ConvertFrom-Json -InputObject $response.Content -NoEnumerate
        if ($Expected -ge 400 -and [string]::IsNullOrWhiteSpace($payload.message)) {
            throw "$Method $Path returned an error without a message."
        }
        return ,$payload
    }
}

function Assert-Value {
    param($Actual, $Expected, [string]$Label)
    if ($null -eq $Actual -or $Actual -cne $Expected) {
        throw "$Label expected [$Expected], got [$Actual]"
    }
}

function Get-StockRow {
    param([long]$CommodityId, [long]$WarehouseId)
    $rows = Invoke-StockApi GET stocks
    $rows | Where-Object { $_.commodityId -eq $CommodityId -and $_.warehouseId -eq $WarehouseId }
}

function Assert-StockRow {
    param([long]$CommodityId, [long]$WarehouseId, [int]$Quantity, [decimal]$Amount, [decimal]$UnitPrice)
    $row = Get-StockRow $CommodityId $WarehouseId
    Assert-Value $row.quantity $Quantity 'Stock quantity'
    Assert-Value ([decimal]$row.amount) $Amount 'Stock amount'
    Assert-Value ([decimal]$row.unitPrice) $UnitPrice 'Stock average price'
    if ($row.amount -isnot [string] -or $row.unitPrice -isnot [string]) {
        throw 'Monetary fields must be returned as decimal strings.'
    }
}

$run = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$item = Invoke-StockApi POST commodities @{ name = "Item $run"; spec = 'Case' } 201
$other = Invoke-StockApi POST commodities @{ name = "Alternate item $run"; spec = 'Unit' } 201
$warehouse = Invoke-StockApi POST warehouses @{ name = "Warehouse $run"; address = 'Zone A' } 201
$otherWarehouse = Invoke-StockApi POST warehouses @{ name = "Alternate warehouse $run"; address = 'Zone B' } 201
$temporary = Invoke-StockApi POST warehouses @{ name = "Unused warehouse $run" } 201
$renamed = Invoke-StockApi PUT "commodities/$($item.id)" @{ name = "Renamed item $run"; spec = 'Carton' }
Assert-Value $renamed.spec 'Carton' 'Updated commodity specification'
$renamedWarehouse = Invoke-StockApi PUT "warehouses/$($warehouse.id)" @{ name = "Renamed warehouse $run"; address = 'Zone C' }
Assert-Value $renamedWarehouse.address 'Zone C' 'Updated warehouse address'
Invoke-StockApi DELETE "warehouses/$($temporary.id)" -Expected 204
Invoke-StockApi PUT "warehouses/$($temporary.id)" @{ name = 'Deleted' } 404 | Out-Null
Write-Output 'PASS master data creation, edit, and deletion'

$pair = @{ commodityId = $item.id; warehouseId = $warehouse.id }
$emptyQuote = Invoke-StockApi GET "outbounds/quote?commodityId=$($item.id)&warehouseId=$($warehouse.id)"
Assert-Value $emptyQuote.availableQuantity 0 'Outgoing quote for empty stock'
Invoke-StockApi POST inbounds ($pair + @{ quantity = 10 }) 400 | Out-Null
Invoke-StockApi POST inbounds ($pair + @{ quantity = 10; unitPrice = '10'; amount = '99' }) 400 | Out-Null
Invoke-StockApi POST inbounds ($pair + @{ unitPrice = '3'; amount = '10' }) 400 | Out-Null
Invoke-StockApi POST inbounds ($pair + @{ unitPrice = '0.004'; amount = '0.00' }) 400 | Out-Null
Invoke-StockApi POST inbounds ($pair + @{ unitPrice = '0'; amount = '0.00' }) 400 | Out-Null
$first = Invoke-StockApi POST inbounds ($pair + @{ quantity = 10; unitPrice = '10' }) 201
Assert-Value ([decimal]$first.amount) 100 'Derived receipt amount'
Assert-Value $first.previousQuantity 0 'First receipt snapshot quantity'
$second = Invoke-StockApi POST inbounds ($pair + @{ quantity = 10; amount = '200' }) 201
Assert-Value ([decimal]$second.unitPrice) 20 'Derived receipt unit price'
Assert-StockRow $item.id $warehouse.id 20 300 15
$quotePath = "outbounds/quote?commodityId=$($item.id)&warehouseId=$($warehouse.id)"
$quote = Invoke-StockApi GET "$quotePath&quantity=5"
Assert-Value $quote.availableQuantity 20 'Available quantity'
Assert-Value ([decimal]$quote.unitPrice) 15 'Quoted average price'
Assert-Value ([decimal]$quote.amount) 75 'Quoted outgoing amount'
Invoke-StockApi GET "$quotePath&quantity=21" -Expected 409 | Out-Null
$shipment = Invoke-StockApi POST outbounds ($pair + @{ quantity = 5 }) 201
Assert-Value $shipment.previousQuantity 20 'Outgoing snapshot quantity'
Assert-Value ([decimal]$shipment.amount) 75 'Saved outgoing amount'
Assert-StockRow $item.id $warehouse.id 15 225 15
Write-Output 'PASS two-field receipt calculation, stock average, and outgoing quote'

$edit = Invoke-StockApi PUT "inbounds/$($first.id)" ($pair + @{ unitPrice = '14'; amount = '140' })
Assert-Value $edit.quantity 10 'Derived receipt quantity'
Assert-Value $edit.createdAt $first.createdAt 'Original receipt position'
$outbounds = Invoke-StockApi GET outbounds
$replayed = $outbounds | Where-Object id -EQ $shipment.id
Assert-Value ([decimal]$replayed.previousAmount) 340 'Replayed outgoing snapshot amount'
Assert-Value ([decimal]$replayed.amount) 85 'Replayed outgoing amount'
Assert-StockRow $item.id $warehouse.id 15 255 17

$newPair = @{ commodityId = $other.id; warehouseId = $otherWarehouse.id }
Invoke-StockApi PUT "inbounds/$($first.id)" ($newPair + @{ quantity = 7; amount = '140' }) | Out-Null
Assert-StockRow $other.id $otherWarehouse.id 7 140 20
Assert-StockRow $item.id $warehouse.id 5 100 20
$outbounds = Invoke-StockApi GET outbounds
$replayed = $outbounds | Where-Object id -EQ $shipment.id
Assert-Value $replayed.previousQuantity 10 'Replayed outgoing snapshot quantity after move'
Assert-Value ([decimal]$replayed.amount) 100 'Replayed outgoing cost after move'
Invoke-StockApi DELETE "inbounds/$($first.id)" -Expected 204
Assert-Value @(Get-StockRow $other.id $otherWarehouse.id).Count 0 'Stock row after deleting only receipt'
Invoke-StockApi DELETE "inbounds/$($second.id)" -Expected 409 | Out-Null
Invoke-StockApi PUT "inbounds/$($second.id)" ($pair + @{ quantity = 4; unitPrice = '20' }) 409 | Out-Null
Invoke-StockApi PUT "inbounds/$($second.id)" ($newPair + @{ quantity = 10; unitPrice = '20' }) 409 | Out-Null
Assert-StockRow $item.id $warehouse.id 5 100 20
Assert-Value @(Get-StockRow $other.id $otherWarehouse.id).Count 0 'Destination stock after rollback'
$inbounds = Invoke-StockApi GET inbounds
$preserved = $inbounds | Where-Object id -EQ $second.id
Assert-Value $preserved.quantity 10 'Receipt after rollback'
Invoke-StockApi DELETE "commodities/$($item.id)" -Expected 409 | Out-Null
Invoke-StockApi DELETE "warehouses/$($warehouse.id)" -Expected 409 | Out-Null
Invoke-StockApi DELETE "commodities/$($other.id)" -Expected 204
Write-Output 'PASS historical cost and snapshot replay, moving a receipt, rollback, and reference guards'

$replayItem = Invoke-StockApi POST commodities @{ name = "Receipt deletion item $run" } 201
$replayPair = @{ commodityId = $replayItem.id; warehouseId = $warehouse.id }
$deletedReceipt = Invoke-StockApi POST inbounds ($replayPair + @{ quantity = 10; unitPrice = '4' }) 201
$keptReceipt = Invoke-StockApi POST inbounds ($replayPair + @{ quantity = 10; unitPrice = '6' }) 201
$affectedShipment = Invoke-StockApi POST outbounds ($replayPair + @{ quantity = 8 }) 201
Assert-Value ([decimal]$affectedShipment.amount) 40 'Outgoing cost before historical deletion'
$lastReceipt = Invoke-StockApi POST inbounds ($replayPair + @{ quantity = 8; unitPrice = '7' }) 201
Invoke-StockApi DELETE "inbounds/$($deletedReceipt.id)" -Expected 204
Assert-StockRow $replayItem.id $warehouse.id 10 68 6.8
$outbounds = Invoke-StockApi GET outbounds
$replayed = $outbounds | Where-Object id -EQ $affectedShipment.id
Assert-Value ([decimal]$replayed.amount) 48 'Outgoing cost after historical deletion'
$inbounds = Invoke-StockApi GET inbounds
$replayedReceipt = $inbounds | Where-Object id -EQ $lastReceipt.id
Assert-Value $replayedReceipt.previousQuantity 2 'Later receipt snapshot quantity'
Assert-Value ([decimal]$replayedReceipt.previousAmount) 12 'Later receipt snapshot amount'
Invoke-StockApi DELETE "inbounds/$($keptReceipt.id)" -Expected 409 | Out-Null
Assert-StockRow $replayItem.id $warehouse.id 10 68 6.8
Write-Output 'PASS historical deletion reprices outgoing and cannot use later receipts to cover a shortage'

$roundingItem = Invoke-StockApi POST commodities @{ name = "Rounding item $run" } 201
$roundingPair = @{ commodityId = $roundingItem.id; warehouseId = $warehouse.id }
Invoke-StockApi POST inbounds ($roundingPair + @{ quantity = 3; amount = '100' }) 201 | Out-Null
$partial = Invoke-StockApi POST outbounds ($roundingPair + @{ quantity = 1 }) 201
Assert-Value ([decimal]$partial.amount) 33.33 'Rounded partial outgoing amount'
$clearance = Invoke-StockApi POST outbounds ($roundingPair + @{ quantity = 2 }) 201
Assert-Value ([decimal]$clearance.amount) 66.67 'Outgoing amount when clearing stock'
Assert-Value @(Get-StockRow $roundingItem.id $warehouse.id).Count 0 'Stock row after full clearance'
Invoke-StockApi DELETE "commodities/$($roundingItem.id)" -Expected 409 | Out-Null
Write-Output 'PASS rounding and remaining-amount clearance'

$raceItem = Invoke-StockApi POST commodities @{ name = "Concurrent item $run" } 201
$racePair = @{ commodityId = $raceItem.id; warehouseId = $warehouse.id }
Invoke-StockApi POST inbounds ($racePair + @{ quantity = 5; unitPrice = '10' }) 201 | Out-Null
$outgoing = $racePair + @{ quantity = 4 } | ConvertTo-Json -Compress
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(15)
try {
    $requests = foreach ($attempt in 1..2) {
        $body = [System.Net.Http.StringContent]::new($outgoing, [Text.Encoding]::UTF8, 'application/json')
        $client.PostAsync("$api/outbounds", $body)
    }
    $statuses = foreach ($request in $requests) {
        $response = $request.GetAwaiter().GetResult()
        [int]$response.StatusCode
        $response.Dispose()
    }
    Assert-Value (($statuses | Sort-Object) -join ',') '201,409' 'Concurrent outgoing status codes'
    Assert-StockRow $raceItem.id $warehouse.id 1 10 10
} finally {
    $client.Dispose()
}
Write-Output 'PASS concurrent outgoing requests cannot oversell'

if ($ComposeProject) {
    docker compose -p $ComposeProject down
    if ($LASTEXITCODE -ne 0) { throw 'Compose could not stop the isolated project.' }
    docker compose -p $ComposeProject up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Compose could not restart the isolated project.' }
    Assert-StockRow $item.id $warehouse.id 5 100 20
    Assert-StockRow $raceItem.id $warehouse.id 1 10 10
    Write-Output 'PASS inventory persists across container recreation'
}

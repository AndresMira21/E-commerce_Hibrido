[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][PSCredential]$Credential,
    [string]$ManagementUrl = 'http://127.0.0.1:15672',
    [string]$VHost = '/',
    [switch]$Apply
)
# Inspeccion por defecto. Solo -Apply escribe destinos y politicas; nunca borra ni consume.
$ErrorActionPreference = 'Stop'
$vhostPath = [Uri]::EscapeDataString($VHost)
$baseUri = $ManagementUrl.TrimEnd('/') + '/api'
$networkCredential = $Credential.GetNetworkCredential()
$authorization = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    $networkCredential.UserName + ':' + $networkCredential.Password))
$headers = @{ Authorization = 'Basic ' + $authorization }

function Get-Optional([string]$Path) {
    try { return Invoke-RestMethod -Uri ($baseUri + $Path) -Headers $headers -Method Get }
    catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 404) { return $null }
        throw
    }
}
function Put-Json([string]$Path, $Body) {
    Invoke-RestMethod -Uri ($baseUri + $Path) -Headers $headers -Method Put `
        -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 8 -Compress) | Out-Null
}

# Preflight completo ANTES de escribir. Rechazar politicas ajenas coincidentes, incluso
# de prioridad inferior: sobrescribirlas podria desactivar otros ajustes de la cola.
$policies = @(Get-Optional "/policies/$vhostPath" | Where-Object { $null -ne $_ })
$plans = @()
foreach ($prefix in @('billing', 'inventory', 'notification')) {
    $queueName = $prefix + '_queue'
    $policyName = $prefix + '-dlq'
    $exchangeName = $prefix + '_dlx'
    $deadQueueName = $prefix + '_dead_queue'
    $routingKey = $prefix + '.failed'
    $pattern = '^' + $queueName + '$'
    $queue = Get-Optional "/queues/$vhostPath/$queueName"
    if (-not $queue) { throw "No existe $queueName. Preparar primero la topologia sin consumir mensajes." }
    foreach ($policy in $policies) {
        if ($policy.name -eq $policyName) {
            $keys = @($policy.definition.PSObject.Properties.Name)
            if ($policy.pattern -ne $pattern -or $policy.'apply-to' -ne 'queues' -or $policy.priority -ne 10 `
                -or $policy.definition.'dead-letter-exchange' -ne $exchangeName `
                -or $policy.definition.'dead-letter-routing-key' -ne $routingKey `
                -or $keys.Count -ne 2) {
                throw "La politica $policyName existe con otra configuracion. No se modifica automaticamente."
            }
        } elseif ($policy.'apply-to' -ne 'exchanges' -and $queueName -match $policy.pattern) {
            throw "La politica ajena $($policy.name) coincide con $queueName. Revisar y combinar manualmente."
        }
    }
    if ($queue.arguments.'x-dead-letter-exchange' -and $queue.arguments.'x-dead-letter-exchange' -ne $exchangeName) {
        throw "Argumentos DLX incompatibles en $queueName. No se redeclara la cola."
    }
    if ($queue.arguments.'x-dead-letter-routing-key' -and $queue.arguments.'x-dead-letter-routing-key' -ne $routingKey) {
        throw "Routing key DLX incompatible en $queueName."
    }
    $exchange = Get-Optional "/exchanges/$vhostPath/$exchangeName"
    $deadQueue = Get-Optional "/queues/$vhostPath/$deadQueueName"
    if ($exchange -and ($exchange.type -ne 'direct' -or -not $exchange.durable -or $exchange.auto_delete `
        -or $exchange.internal -or @($exchange.arguments.PSObject.Properties).Count -ne 0)) {
        throw "Exchange $exchangeName incompatible; no se sobrescribe."
    }
    if ($deadQueue -and (-not $deadQueue.durable -or $deadQueue.auto_delete `
        -or @($deadQueue.arguments.PSObject.Properties).Count -ne 0)) {
        throw "DLQ $deadQueueName incompatible; no se sobrescribe."
    }
    if ($queue.effective_policy_definition.'dead-letter-exchange' `
        -and $queue.effective_policy_definition.'dead-letter-exchange' -ne $exchangeName) {
        throw "Politica efectiva/operador incompatible en $queueName."
    }
    $bindings = @(Get-Optional "/bindings/$vhostPath/e/$exchangeName/q/$deadQueueName")
    $plans += [PSCustomObject]@{ Queue=$queueName; Policy=$policyName; Exchange=$exchangeName; DeadQueue=$deadQueueName;
        Key=$routingKey; Pattern=$pattern; Bound=@($bindings | Where-Object { $_.routing_key -eq $routingKey }).Count -gt 0 }
}

foreach ($plan in $plans) {
    if ($Apply) {
        Put-Json "/exchanges/$vhostPath/$($plan.Exchange)" @{type='direct';durable=$true;auto_delete=$false;internal=$false;arguments=@{}}
        Put-Json "/queues/$vhostPath/$($plan.DeadQueue)" @{durable=$true;auto_delete=$false;arguments=@{}}
        if (-not $plan.Bound) {
            Invoke-RestMethod -Uri ($baseUri + "/bindings/$vhostPath/e/$($plan.Exchange)/q/$($plan.DeadQueue)") `
                -Headers $headers -Method Post -ContentType 'application/json' `
                -Body (@{routing_key=$plan.Key;arguments=@{}} | ConvertTo-Json -Compress) | Out-Null
        }
        Put-Json "/policies/$vhostPath/$($plan.Policy)" @{pattern=$plan.Pattern;'apply-to'='queues';priority=10;
            definition=@{'dead-letter-exchange'=$plan.Exchange;'dead-letter-routing-key'=$plan.Key}}
    }
    $effective = Get-Optional "/queues/$vhostPath/$($plan.Queue)"
    $binding = @(Get-Optional "/bindings/$vhostPath/e/$($plan.Exchange)/q/$($plan.DeadQueue)")
    $valid = $effective.policy -eq $plan.Policy `
        -and $effective.effective_policy_definition.'dead-letter-exchange' -eq $plan.Exchange `
        -and $effective.effective_policy_definition.'dead-letter-routing-key' -eq $plan.Key `
        -and @($binding | Where-Object { $_.routing_key -eq $plan.Key }).Count -gt 0
    Write-Output "$($plan.Queue): politica=$($effective.policy) destino=$($plan.DeadQueue) verificada=$valid"
    if ($Apply -and -not $valid) { throw "No se pudo confirmar la politica efectiva de $($plan.Queue)." }
}
# No registrar Credential, headers ni cuerpos de mensajes.

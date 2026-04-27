# Performance / Battery / Thermal Budget – Lumos v1.0

## Startup
- Cold start target: < 2.5s (mid-tier)
- Warm start: < 1.2s

## Discovery (Venue Session)
- Scan/advertise duty cycle must be bounded (configurable within safe range)
- UI updates debounced to avoid flicker; avoid recomposition storms

## Chat
- Text send path non-blocking; ack within local Wi-Fi target < 300ms (lab)
- Retries: exponential backoff with jitter; max retry budgets bounded

## Media
- Chunk size tuned by transport MTU; avoid memory spikes
- Progressive progress UI; encryption on background threads/actors

## Battery/Thermal Guardrails
- Disable aggressive background scanning outside active session
- Reduce duty cycle under low power mode
- If thermal throttling detected, reduce media throughput and show message

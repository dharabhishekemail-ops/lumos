# iOS Profiling Notes (Lumos)

Suggested tools:
- Instruments: Time Profiler (startup + navigation)
- Instruments: Energy Log (venue session)
- Instruments: Network (Wi-Fi fallback)
- OSLog signposts for: start, onboarding complete, advertise start, session established

Procedure:
1. Run on device (not simulator) for BLE + local network
2. Capture:
   - cold start
   - discovery duty cycle
   - chat send latency
   - media throughput and CPU usage
3. Record results against PERF_BUDGET.md

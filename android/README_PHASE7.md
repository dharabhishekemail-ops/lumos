Lumos Phase 7 (UI/UX) – v0.7
===========================

This phase adds production-grade UI scaffolding for Android (Jetpack Compose) and iOS (SwiftUI),
including design system, navigation, screen flows, state plumbing seams, and diagnostics export UI.

Key principles implemented:
- Single source of truth UI state (UDF/Reducer style) in ViewModels
- Clean architecture seam: UI -> UseCases -> Repos -> Session Orchestrator
- Accessibility: semantics labels, minimum touch targets, dynamic type support (iOS), Compose semantics
- Professional look: consistent spacing, typography, surfaces, motion, empty/error states

NOTE: This is UI + plumbing scaffolding. Business services are wired behind interfaces so Phase 8+ can
fully integrate IAP/ads/config remote operations without rewriting UI.

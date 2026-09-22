# Shoe Commerce Design System

This file is a compatibility pointer for tools that look for a design-system
master file. The current visual source of truth is [`DESIGN.md`](../../DESIGN.md).

## Current implementation profile

- Creative direction: **The Stockroom Proof Sheet**.
- Typography: **Archivo Variable** only, loaded locally.
- Surfaces: warm paper, clean paper, near-black ink, thin rules.
- Shape: square controls and containers; no ornamental rounding.
- Accent: signal pink for current price and active actions; green for success.
- Layout: evidence-led customer and staff surfaces that collapse by reading
  order on small screens without page-level horizontal overflow.
- Interaction: native semantic controls, visible focus, 44px minimum targets,
  actionable errors, polite status announcements, and reduced-motion support.

Do not use the former Calistoga/Inter, Glassmorphism, rounded-card, gradient,
or remote-font specifications that previously lived in this file. They do not
describe the current application.

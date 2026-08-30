---
name: Shoe Commerce
description: A direct, evidence-led commerce interface for customers and store operators.
colors:
  signal-pink: "#e94770"
  signal-pink-deep: "#c72855"
  paper: "#f2eee5"
  clean-paper: "#fffdf8"
  ink: "#191915"
  success-green: "#1c6b43"
  muted-ink: "#625d55"
  rule: "#bcb5a8"
  focus-blue: "#0c61ff"
  danger-red: "#a72424"
typography:
  display:
    fontFamily: "Archivo Variable, sans-serif"
    fontSize: "clamp(3.4rem, 8vw, 7rem)"
    fontWeight: 800
    lineHeight: 0.88
    letterSpacing: "-0.04em"
  headline:
    fontFamily: "Archivo Variable, sans-serif"
    fontSize: "clamp(1.75rem, 4vw, 3.5rem)"
    fontWeight: 750
    lineHeight: 1
    letterSpacing: "-0.03em"
  body:
    fontFamily: "Archivo Variable, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Archivo Variable, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 800
    letterSpacing: "0.07em"
rounded:
  square: "0px"
spacing:
  xs: "8px"
  sm: "16px"
  md: "24px"
  lg: "32px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.clean-paper}"
    rounded: "{rounded.square}"
    padding: "0.8rem 1.2rem"
    height: "3.25rem"
  input:
    backgroundColor: "{colors.clean-paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.square}"
    padding: "0.8rem 0.9rem"
    height: "3.25rem"
---

# Design System: Shoe Commerce

## Overview

**Creative North Star: "The Stockroom Proof Sheet"**

Shoe Commerce treats commercial facts like a precise proof sheet: bold identity
at the entrance, then ruled rows, explicit states, and unambiguous actions. The
system is warm without being soft and expressive without obscuring operational
truth. Customer and staff surfaces share one visual language while changing
density to match the task.

**Key Characteristics:**

- Warm paper fields with near-black structure.
- Square, confident controls and thin ruled evidence.
- Signal color is reserved for price/action; green is reserved for success.
- Responsive layouts collapse by reading order without horizontal scrolling.

## Colors

Signal pink carries current-price and active-action emphasis. Paper and ink do
most of the work; semantic green, red, and blue remain narrowly scoped.

**The Evidence Color Rule.** Accent color marks a commercial fact or active
route, never decorative chrome. Green means a completed operation, not generic
positive decoration.

## Typography

Archivo Variable is the only type family. Heavy, tightly tracked display text
creates the identity; compact uppercase labels organize facts; body copy stays
at a readable line height and avoids long measures.

**The Numeral Rule.** Monetary amounts, quantities, identifiers, and timestamps
use tabular numerals where alignment aids scanning.

## Layout

Page gutters use `clamp(1rem, 2.5vw, 2rem)`. Desktop operational surfaces use a
wide task station with a narrower sticky evidence rail. At 760px and below the
rail follows the task in one column; duplicated summary facts disappear. At
420px and below compound controls stack to full width. The document supports a
320px minimum viewport without horizontal overflow.

## Elevation & Depth

The system is flat by default. Thin ink or muted rules establish hierarchy;
wide soft shadows are limited to interrupted states such as dialogs and inline
errors. Focus uses a high-contrast blue outline rather than shadow decoration.

**The Ruled Surface Rule.** Prefer borders and tonal fields to generic cards.
Elevation must communicate state or protected focus.

## Shapes

Controls, data fields, marks, and containers are square. Pills and ornamental
rounding are absent; small circles are limited to status indicators and loading
spinners whose geometry carries meaning.

## Components

Primary buttons are near-black with clean-paper text, at least 44px tall, and
invert on hover without moving layout. Inputs use clean paper, a one-pixel ink
border, visible labels, and the global focus outline. Navigation uses compact
bold links with a two-pixel active underline. Operational facts appear in ruled
definition lists rather than card grids. Terminal receipts use one saturated
semantic field, a text-and-icon outcome, immutable facts, and one clear next
action.

## Do's and Don'ts

### Do:

- **Do** expose server-owned price, availability, scope, and state as labeled facts.
- **Do** use native controls, visible focus, actionable errors, and 44px minimum targets.
- **Do** keep mobile reading order task-first and remove duplicated summaries.

### Don't:

- **Don't** invent product imagery, claims, stock, prices, or financial state.
- **Don't** use rounded dashboard cards, gradients, glass, or decorative icon tiles.
- **Don't** use accent or success colors as ambient decoration.
- **Don't** turn small uppercase labels into ornamental section kickers.

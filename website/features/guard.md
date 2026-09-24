# OriginGuard & SuSFS

## OriginGuard

Static pre-install audit of every module zip (`Settings > Origin Lab`):

- Critical findings **block** the install.
- High-severity findings ask first.
- Audit logs explain what a blocked zip tripped on — useful when diagnosing a rescued bootloop.

Toggle it in `Settings > Origin Lab`.

## SuSFS manager

Built-in SuSFS manager with a **one-tap strong-hiding preset** (kernel 4.3+ backport). Combine with
[Origin Veil](/features/veil) for the full hiding stack: Veil cloaks per-app probes, SuSFS hides
the filesystem traces.

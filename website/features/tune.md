# OriginTune

Kernel tuning from a home hero card → tuning lab. Every knob applies immediately and can persist
across boot — and only knobs the running kernel exposes are shown.

- **CPU frequency**: per-cluster governor and min/max frequencies, plus schedutil rate-limit tunables
- **GPU**: governor and min/max clocks across Adreno/kGSL and devfreq (Mali) paths
- **I/O**: per-device scheduler selection and read-ahead size
- **Memory preset profiles**: curated VM presets (swappiness, dirty ratios, cache pressure) in the
  style of the BORE profiles
- **Scheduler extras**: uclamp and energy-aware scheduling tunables where the kernel exposes them
- **LMK levels**: lmkd minfree table and kernel-driver params with per-level presets and stock restore
- **Profile sharing**: export/import tuning setups as JSON, with automatic backup before applying
- **Diagnostics**: visible effect per tuning (live clocks, load average, ZRAM ratio and PSI stalls)
- **TCP** congestion control
- **BORE scheduler presets** (Balanced / Responsive / Throughput / Battery saver) plus manual knobs
- **ZRAM** size / algorithm / streams / swappiness with live compression stats
- **Generic sysctl editor**

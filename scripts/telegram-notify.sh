#!/usr/bin/env bash
# Telegram announcements for CI: the "main updated" cross-topic notice and the
# release changelog.
#
# This is the legacy ksubot.py behaviour (topics, changelog grouping, main-updated
# notice) rewritten against the HTTP Bot API. It does not need Telethon, API
# credentials or a locally compiled Bot API server.
set -euo pipefail

mode="${1:-}"
case "$mode" in
  main-updated | changelog) ;;
  *)
    echo "usage: $0 main-updated|changelog" >&2
    exit 2
    ;;
esac

: "${BOT_TOKEN:?BOT_TOKEN is required}"
: "${CHAT_ID:?CHAT_ID is required}"

api="https://api.telegram.org/bot${BOT_TOKEN}"

html_escape() {
  printf '%s' "$1" | jq -Rsr '@html'
}

send_message() {
  local text="$1"
  local response
  response="$(mktemp)"

  local args=(
    --silent --show-error --fail-with-body
    --retry 5 --retry-all-errors --retry-delay 2
    --connect-timeout 30 --max-time 120
    --http1.1 -H "Expect:"
    -o "$response"
    # --form-string: `@channel` chat ids and the leading "<b>" of the text would
    # otherwise be read by curl as local file names.
    --form-string "chat_id=${CHAT_ID}"
    --form-string "text=${text}"
    -F "parse_mode=HTML"
    -F "disable_web_page_preview=true"
  )
  if [[ -n "${MESSAGE_THREAD_ID:-}" ]]; then
    args+=(-F "message_thread_id=${MESSAGE_THREAD_ID}")
  fi

  if ! curl "${args[@]}" "${api}/sendMessage"; then
    echo "[-] Telegram sendMessage failed:" >&2
    jq . "$response" >&2 2>/dev/null || cat "$response" >&2
    rm -f "$response"
    return 1
  fi
  if ! jq -e '.ok == true' "$response" >/dev/null 2>&1; then
    echo "[-] Telegram rejected the message:" >&2
    jq . "$response" >&2 || cat "$response" >&2
    rm -f "$response"
    return 1
  fi
  rm -f "$response"
}

# ---------------------------------------------------------------------------
# Main-updated notice: a main or tag build landed, so the development topic is
# now behind. Posted to the development topic only.
# ---------------------------------------------------------------------------
if [[ "$mode" == "main-updated" ]]; then
  if [[ -z "${MESSAGE_THREAD_ID:-}" ]]; then
    echo "[!] No development topic configured, skipping the main-updated notice"
    exit 0
  fi

  send_message "<b>KazeSU Manager · main updated</b>
Builds in this development topic may be out of date now. The latest main build is in the normal manager topic."
  echo "[+] main-updated notice sent to topic ${MESSAGE_THREAD_ID}"
  exit 0
fi

# ---------------------------------------------------------------------------
# Changelog: grouped from the commits between the previous tag and HEAD.
# Posted to its own topic, only for a release tag.
# ---------------------------------------------------------------------------
if [[ -z "${MESSAGE_THREAD_ID:-}" ]]; then
  echo "[!] No changelog topic configured, skipping the changelog"
  exit 0
fi

base=""
base="$(git describe --tags --abbrev=0 HEAD~1 2>/dev/null || true)"
if [[ -z "$base" ]]; then
  # Older tag whose commit is not HEAD~1 (release cut from a merge commit):
  # take the newest v* tag that is not the tag being pushed.
  base="$(git tag --list 'v*' --sort=-v:refname |
    grep -vxF "${BRANCH:-}" | head -n 1 || true)"
fi

if [[ -n "$base" ]]; then
  range="$base..HEAD"
else
  range="HEAD"
fi

raw="$(git log "$range" --no-merges --reverse --pretty=format:%s || true)"

commit_re='^([A-Za-z]+)(\(([^)]*)\))?!?:[[:space:]]*(.+)$'
declare -A group_lines=()
group_order=("New" "Fixes" "Other")

entry_count=0
skipped=0
while IFS= read -r subject; do
  subject="$(printf '%s' "$subject" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  [[ -n "$subject" ]] || continue

  if (( entry_count >= 120 )); then
    skipped=$((skipped + 1))
    continue
  fi

  scope=""
  text="$subject"
  if [[ "$subject" =~ $commit_re ]]; then
    type="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
    scope="${BASH_REMATCH[3]}"
    text="${BASH_REMATCH[4]}"
    case "$type" in
      feat) group="New" ;;
      fix) group="Fixes" ;;
      *) group="Other" ;;
    esac
  else
    group="Other"
  fi

  if (( ${#text} > 200 )); then
    text="${text:0:197}..."
  fi
  prefix=""
  if [[ -n "$scope" ]]; then
    prefix="<b>$(html_escape "$scope")</b> — "
  fi
  group_lines[$group]+="• ${prefix}$(html_escape "$text")"$'\n'
  entry_count=$((entry_count + 1))
done <<<"$raw"

if (( entry_count == 0 )); then
  echo "[!] No commits to announce in the changelog"
  exit 0
fi

body=()
for group in "${group_order[@]}"; do
  [[ -n "${group_lines[$group]:-}" ]] || continue
  if (( ${#body[@]} > 0 )); then
    body+=("")
  fi
  body+=("<b>${group}</b>")
  while IFS= read -r line; do
    body+=("$line")
  done <<<"${group_lines[$group]%$'\n'}"
done

if (( skipped > 0 )); then
  body+=("")
  body+=("…and ${skipped} more commits")
fi

header=("<b>KazeSU Manager · changelog</b>")
if [[ -n "$base" ]]; then
  header+=("Since <code>$(html_escape "$base")</code>")
else
  header+=("Full history")
fi
if [[ -n "${RUN_URL:-}" ]]; then
  header[-1]+=" · <a href=\"$(html_escape "$RUN_URL")\">Workflow run</a>"
fi

# Telegram caps a message at 4096 characters; 3800 leaves room for the HTML
# entities and the continuation header.
chunk=("${header[@]}" "")
keep="${#chunk[@]}"
messages=()
for line in "${body[@]}"; do
  candidate="$(printf '%s\n' "${chunk[@]}" "$line")"
  if (( ${#candidate} > 3800 && ${#chunk[@]} > keep )); then
    messages+=("$(printf '%s\n' "${chunk[@]}")")
    chunk=("<b>KazeSU Manager · changelog · continued</b>" "")
    keep="${#chunk[@]}"
  fi
  chunk+=("$line")
done
messages+=("$(printf '%s\n' "${chunk[@]}")")

index=0
for message in "${messages[@]}"; do
  index=$((index + 1))
  echo "[+] Sending changelog part ${index}/${#messages[@]}"
  send_message "$message"
  if (( index < ${#messages[@]} )); then
    sleep 1
  fi
done

echo "[+] Changelog sent to topic ${MESSAGE_THREAD_ID}"

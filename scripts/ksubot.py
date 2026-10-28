import asyncio
import os,re
import random
import subprocess
import sys,json
from telegram import Bot
from telegram.error import RetryAfter
from telegram.constants import ParseMode

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
DEVELOPING_THREAD_ID = os.environ.get("DEVELOPING_THREAD_ID")
CHANGELOG_THREAD_ID = os.environ.get("CHANGELOG_THREAD_ID")
RUN_URL = os.environ.get("RUN_URL")
TITLE = os.environ.get("TITLE")
VERSION = os.environ.get("VERSION")
BRANCH = os.environ.get("BRANCH")

API_ID = 21724
API_HASH = "3e0cb5efcd52300aec5994fdfc5bdc16"

GITHUB_EVENT = json.loads(open(os.environ.get("GITHUB_EVENT_PATH"), "r").read())
GITHUB_REF_TYPE = os.environ.get("GITHUB_REF_TYPE")

commit_message = ''
commit_line = ''
try:
    if 'commits' in GITHUB_EVENT:
        commits = GITHUB_EVENT['commits']
        commit_message = ''
        i = len(commits)
        for commit in commits[::-1]:
            msg_line = commit['message'].split('\n')
            msg = commit['message'].strip()
            if len(msg) > 200:
                msg = msg[:197] + '...'
            msg += ' by ' + commit['author']['username']
            if i > 1:
                msg += '\n------'
            if len(msg) + 1 + len(commit_message) > 600:
                commit_message = f'{commit_message}\n(other {i} commits)'
                break
            else:
                commit_message = f'{msg}\n{commit_message}\n'
            i -= 1
        commit_message = f'{commit_message.strip()}'
        last_commit = commits[-1]

    elif 'head_commit' in GITHUB_EVENT:
        msg = GITHUB_EVENT["head_commit"]["message"]
        if len(msg) > 200:
            msg = msg[:197] + '...'
        commit_message = f'{msg.strip()}'
    else:
        commit_message = f'(no commit message)'
except IndexError:
    print("[!] IndexError Found! try another way to get commit...")
    try:
        if 'head_commit' in GITHUB_EVENT:
            msg = GITHUB_EVENT["head_commit"]["message"]
            if len(msg) > 200:
                msg = msg[:197] + '...'
            commit_message = f'{msg.strip()}'
    except:
        from traceback import print_exc
        print_exc()
        commit_message = f'(no commit message)'
except:
    from traceback import print_exc
    print_exc()
    commit_message = f'(Unexpected Error! So no commit message)'

if 'compare' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['compare']
    commit_line = '<a href="' + commit_url + '">Compare</a>'
elif 'head_commit' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['head_commit']['url']
    commit_line = '<a href="' + commit_url + '">Commit</a>'
else:
    commit_line = ''

DEBUG_LINK = "https://nightly.link/samakshkambxj/OriginSU/workflows/build-manager/main/Manager-debug.zip"

DISPLAY_TITLES = {
    "manager": "OriginSU Manager",
    "spoofed-manager": "OriginSU Manager · Spoofed",
}

DEV_WARNING = (
    "⚠️ <b>Development build</b>\n"
    "Back up before installing. This is not a stable main or tag build."
)

MAIN_UPDATED_MSG = """
<b>OriginSU Manager · main updated</b>
Builds in this development topic may be out of date now. The latest main build is in the normal manager topic.
""".strip()

def escape_telegram_html(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&#39;")

async def start_bot_api():
    print("[+] Starting Telegram Bot API")
    import subprocess
    global process
    process = subprocess.Popen(["./telegram-bot-api-binary", f"--api-id={API_ID}", f"--api-hash={API_HASH}", "--local"])
    

async def wait_for_bot_api():
    import httpx
    await start_bot_api()
    print("[+] Waiting for Telegram Bot API to start...")
    async with httpx.AsyncClient() as client:
        for count in range(30):
            try:
                response = await client.get(f"http://127.0.0.1:8081/bot{BOT_TOKEN}/getMe")
                if response.status_code < 500:
                    print("[+] Telegram Bot API started")
                    break
            except:
                #print(process.stdout.readline())
                print("Still wait..." + str(count))
                await asyncio.sleep(1)
                pass
        else:
            process.kill()
            print("[-] Failed to start Telegram Bot API")
            exit(1)
        return 
        

def is_development_build() -> bool:
    return BRANCH != "main" and GITHUB_REF_TYPE != "tag"


def display_title(debug: bool = False) -> str:
    base = DISPLAY_TITLES.get((TITLE or "").lower(), TITLE or "OriginSU Manager")
    return f"{base} · Debug" if debug else base


def build_caption(debug: bool = False) -> str:
    lines = [
        f"<b>{display_title(debug)}</b>",
        f'Branch: <code>{escape_telegram_html(BRANCH or "")}</code> · '
        f'Build <code>#ci_{VERSION}</code>',
    ]
    if is_development_build():
        lines.append("")
        lines.append(DEV_WARNING)
    lines.append("")
    lines.append(f"<pre>{escape_telegram_html(commit_message)}</pre>")
    lines.append("")
    links = [part for part in (commit_line, f'<a href="{RUN_URL}">Workflow run</a>') if part]
    lines.append(" · ".join(links))
    lines.append(f'<a href="{DEBUG_LINK}">Latest main debug build</a>')
    return "\n".join(lines)


def get_caption():
    return build_caption()


def get_caption_for_debug():
    return build_caption(debug=True)


COMMIT_RE = re.compile(r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]*)\))?!?:\s*(?P<text>.+)$")
CHANGELOG_GROUPS = (
    ("feat", "New"),
    ("fix", "Fixes"),
)
CHANGELOG_MAX_ENTRIES = 120
CHANGELOG_CHUNK_LIMIT = 3800
CHANGELOG_CONTINUED = "<b>OriginSU Manager · changelog · continued</b>"


def _git(*args):
    return subprocess.run(
        ["git", *args],
        capture_output=True,
        text=True,
        check=True,
    ).stdout


def changelog_since_tag():
    """Return (base_tag, [(group, scope, text), ...]) for commits in the pushed tag.

    The changelog is only posted for a release tag push, where HEAD *is* the
    new tag, so the base must be the previous tag rather than the one being
    released (otherwise the range is empty).
    """
    try:
        base = _git("describe", "--tags", "--abbrev=0", "HEAD~1").strip()
    except Exception as e:
        print(f"[!] Could not resolve the previous tag: {e}")
        base = ""
    if not base:
        # Older tag whose commit is not HEAD~1 (e.g. release from a merge
        # commit): take the newest tag that is not the one being pushed.
        try:
            tags = _git("tag", "--list", "v*", "--sort=-v:refname").splitlines()
            base = next((tag for tag in tags if tag and tag != BRANCH), "")
        except Exception as e:
            print(f"[!] Could not list tags for the changelog: {e}")
            base = ""
    revision_range = f"{base}..HEAD" if base else "HEAD"
    try:
        raw = _git(
            "log", revision_range, "--no-merges", "--reverse",
            "--pretty=format:%s",
        )
    except Exception as e:
        print(f"[-] Could not read git history for the changelog: {e}")
        return base, []

    groups = {prefix: name for prefix, name in CHANGELOG_GROUPS}
    entries = []
    for subject in raw.splitlines():
        subject = subject.strip()
        if not subject:
            continue
        match = COMMIT_RE.match(subject)
        if match:
            group = groups.get(match.group("type").lower(), "Other")
            entries.append((group, match.group("scope") or "", match.group("text").strip()))
        else:
            entries.append(("Other", "", subject))
    return base, entries


def build_changelog_messages():
    base, entries = changelog_since_tag()
    if not entries:
        print("[!] No commits to announce in the changelog")
        return []

    total = len(entries)
    entries = entries[:CHANGELOG_MAX_ENTRIES]

    header = [
        "<b>OriginSU Manager · changelog</b>",
        (f"Since <code>{escape_telegram_html(base)}</code>" if base else "Full history")
        + f" · <a href=\"{RUN_URL}\">Workflow run</a>",
    ]

    body = []
    group_order = [name for _, name in CHANGELOG_GROUPS] + ["Other"]
    grouped = {name: [] for name in group_order}
    for group, scope, text in entries:
        grouped.setdefault(group, []).append((scope, text))

    for group in group_order:
        items = grouped.get(group) or []
        if not items:
            continue
        if body:
            body.append("")
        body.append(f"<b>{group}</b>")
        for scope, text in items:
            prefix = f"<b>{escape_telegram_html(scope)}</b> — " if scope else ""
            body.append(f"• {prefix}{escape_telegram_html(text)}")

    if total > len(entries):
        body.append("")
        body.append(f"…and {total - len(entries)} more commits")

    messages = []
    header_lines = header + [""]
    chunk = list(header_lines)
    keep = len(header_lines)
    for line in body:
        candidate = "\n".join(chunk + [line])
        if len(candidate) > CHANGELOG_CHUNK_LIMIT and len(chunk) > keep:
            messages.append("\n".join(chunk))
            chunk = [CHANGELOG_CONTINUED, ""]
            keep = len(chunk)
        chunk.append(line)
    if chunk:
        messages.append("\n".join(chunk))
    return [message.strip() for message in messages if message.strip()]


async def send_changelog(bot: Bot):
    if not CHANGELOG_THREAD_ID:
        print("[!] CHANGELOG_THREAD_ID is not set, skipping the changelog post")
        return
    messages = build_changelog_messages()
    for index, message in enumerate(messages):
        print(f"[+] Sending changelog part {index + 1}/{len(messages)}")
        await send_message(bot, CHAT_ID, message, CHANGELOG_THREAD_ID)


def check_environ():
    global CHAT_ID, MESSAGE_THREAD_ID, DEVELOPING_THREAD_ID, CHANGELOG_THREAD_ID
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if CHAT_ID is None:
        print("[-] Invalid CHAT_ID")
        exit(1)
    else:
        try:
            CHAT_ID = int(CHAT_ID)
        except:
            pass
    if RUN_URL is None:
        print("[-] Invalid RUN_URL")
        exit(1)
    if TITLE is None:
        print("[-] Invalid TITLE")
        exit(1)
    if VERSION is None:
        print("[-] Invalid VERSION")
        exit(1)
    if BRANCH is None:
        print("[-] Invalid BRANCH")
        exit(1)
    if MESSAGE_THREAD_ID and MESSAGE_THREAD_ID != "":
        try:
            MESSAGE_THREAD_ID = int(MESSAGE_THREAD_ID)
        except:
            print("[-] Invalid MESSAGE_THREAD_ID")
            exit(1)
    else:
        MESSAGE_THREAD_ID = None
    if DEVELOPING_THREAD_ID and DEVELOPING_THREAD_ID != "":
        try:
            DEVELOPING_THREAD_ID = int(DEVELOPING_THREAD_ID)
        except:
            print("[-] Invalid DEVELOPING_THREAD_ID")
            exit(1)
    else:
        DEVELOPING_THREAD_ID = None

    if CHANGELOG_THREAD_ID and CHANGELOG_THREAD_ID != "":
        try:
            CHANGELOG_THREAD_ID = int(CHANGELOG_THREAD_ID)
        except:
            print("[-] Invalid CHANGELOG_THREAD_ID")
            exit(1)
    else:
        CHANGELOG_THREAD_ID = None

async def send_message(bot: Bot, chat_id: int, text: str, message_thread_id=None):
    try:
        await asyncio.sleep(random.uniform(0.2, 0.8))
        return await bot.send_message(chat_id=chat_id, text=text, parse_mode=ParseMode.HTML, message_thread_id=message_thread_id,
                                       read_timeout=350,write_timeout=350,connect_timeout=350,pool_timeout=350,disable_web_page_preview=True)
    except RetryAfter as e:
        print(f"[-] Hit Telegram flood limit, retrying after {e.retry_after} seconds...")
        await asyncio.sleep(e.retry_after)
        return await send_message(bot, chat_id, text, message_thread_id)
    except:
        raise

async def send_document(bot: Bot, chat_id: int, file: str, filename: str, caption=None, message_thread_id=None):
    try:
        await asyncio.sleep(random.uniform(0.2, 0.8))
        with open(file, "rb") as f:
            return await bot.send_document(chat_id=chat_id, document=f, filename=filename,
                                       caption=caption, parse_mode=ParseMode.HTML if caption else None,
                                       message_thread_id=message_thread_id,
                                       read_timeout=350,write_timeout=350,connect_timeout=350,pool_timeout=350)
    except RetryAfter as e:
        print(f"[-] Hit Telegram flood limit, retrying after {e.retry_after} seconds...")
        await asyncio.sleep(e.retry_after)
        return await send_document(bot, chat_id, file, filename, caption, message_thread_id)
    except:
        raise

async def main():
    print("[+] Uploading to telegram")
    check_environ()
    await wait_for_bot_api()
    files = sys.argv[1:]
    print("[+] Files:", files)
    if len(files) <= 0:
        print("[-] No files to upload")
        exit(1)
    print("[+] Logging in Telegram with bot")
    no_caption=False
    bot = Bot(token=BOT_TOKEN,base_url="http://127.0.0.1:8081/bot",base_file_url="http://127.0.0.1:8081/file/bot")
    caption = get_caption()
    caption_debug = get_caption_for_debug()
    if len(caption) > 1024 or len(caption_debug) > 1024:
        print("[-] Caption is too long,so it will be sent as a separate message without caption for files")
        no_caption = True
    upload_release_files = []

    for index, file in enumerate(files):
        if os.path.basename(file).find("debug") != -1:
            # If the filename contains "debug", skip it.
            continue
        upload_release_files.append(file)

    print("[+] Caption: ")
    print("---")
    print(caption)
    print("---")
    if TITLE.lower() == "spoofed-manager" and (BRANCH != "main" and GITHUB_REF_TYPE != "tag"):
        print("[!] Spoofed Manager is not allowed to be uploaded for non-main branch")
    else:
        print("[+] Sending")
        if no_caption:
            await send_message(bot=bot, chat_id=CHAT_ID, text=caption, message_thread_id=MESSAGE_THREAD_ID)
        # One document per request: a media group with all APKs trips
        # Telegram's request-size limit ("Request Entity Too Large") now
        # that each APK embeds the full LKM matrix.
        for index, file in enumerate(upload_release_files):
            name = os.path.basename(file)
            # Caption rides on the first file so it is delivered even if a
            # later upload fails.
            file_caption = caption if (not no_caption and index == 0) else None
            try:
                await send_document(bot=bot, chat_id=CHAT_ID, file=file, filename=name,
                                    caption=file_caption, message_thread_id=MESSAGE_THREAD_ID)
                print(f"[+] Sent {name}")
            except Exception as e:
                print(f"[-] Failed to upload {name}: {e}")
                fallback = (f"⚠️ Could not upload <b>{escape_telegram_html(name)}</b> ({escape_telegram_html(str(e))}). "
                            f"Get it from the <a href=\"{RUN_URL}\">workflow run</a>.")
                await send_message(bot=bot, chat_id=CHAT_ID, text=fallback, message_thread_id=MESSAGE_THREAD_ID)
        if TITLE.lower() == "manager" and (BRANCH == "main" or GITHUB_REF_TYPE == "tag"):
            print("[+] Sending main branch updated message")
            await send_message(bot=bot,chat_id=CHAT_ID, text=MAIN_UPDATED_MSG, message_thread_id=DEVELOPING_THREAD_ID)
        # Announce the changelog only for a pushed release tag. Branch builds
        # (main or otherwise) update the APK topic but stay out of the
        # changelog topic.
        if TITLE.lower() == "manager" and GITHUB_REF_TYPE == "tag":
            try:
                await send_changelog(bot)
            except Exception as e:
                # The APKs are already delivered; a changelog failure should not
                # fail the whole upload job.
                print(f"[-] Failed to post the changelog: {e}")
    print("[+] Done!")
    process.kill()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")

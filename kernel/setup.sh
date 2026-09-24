#!/bin/sh
set -eu

GKI_ROOT=$(pwd)
REPO_URL="https://github.com/samakshkambxj/OriginSU"
DEFAULT_BRANCH="main"

display_usage() {
	echo "Usage: $0 [--cleanup | <commit-or-tag>]"
	echo "  --cleanup:			  Cleans up previous modifications made by the script."
	echo "  <commit-or-tag>:		Sets up or updates OriginSU to the specified tag, branch or commit."
	echo "  -h, --help:			 Displays this usage information."
	echo "  --submodule:		  Registers the downloaded OriginSU copy as a git submodule."
	echo "  (no args):			  Sets up or updates OriginSU to the latest tagged version."
}

initialize_variables() {
	if test -d "$GKI_ROOT/common/drivers"; then
		 DRIVER_DIR="$GKI_ROOT/common/drivers"
	elif test -d "$GKI_ROOT/drivers"; then
		 DRIVER_DIR="$GKI_ROOT/drivers"
	else
		 echo '[ERROR] "drivers/" directory not found.'
		 exit 127
	fi

	DRIVER_MAKEFILE=$DRIVER_DIR/Makefile
	DRIVER_KCONFIG=$DRIVER_DIR/Kconfig
}

link_driver() {
	# realpath --relative-to is GNU-only; fall back to an absolute symlink on macOS/BSD.
	if ln_target=$(realpath --relative-to="$DRIVER_DIR" "$GKI_ROOT/KernelSU/kernel" 2>/dev/null); then
		ln -sf "$ln_target" "$DRIVER_DIR/kernelsu"
	else
		ln -sf "$GKI_ROOT/KernelSU/kernel" "$DRIVER_DIR/kernelsu"
	fi
	echo "[+] Symlink created."
}

patch_makefile() {
	if grep -q "kernelsu" "$DRIVER_MAKEFILE"; then
		echo "[-] Makefile already patched, skipping."
	else
		printf "\nobj-\$(CONFIG_KSU) += kernelsu/\n" >> "$DRIVER_MAKEFILE"
		echo "[+] Modified Makefile."
	fi
}

patch_kconfig() {
	if grep -q 'source "drivers/kernelsu/Kconfig"' "$DRIVER_KCONFIG"; then
		echo "[-] Kconfig already patched, skipping."
	else
		# shellcheck disable=SC2016
		if grep -q "^endmenu" "$DRIVER_KCONFIG"; then
			sed -i '/^endmenu/i\source "drivers/kernelsu/Kconfig"' "$DRIVER_KCONFIG"
		else
			printf '\nsource "drivers/kernelsu/Kconfig"\n' >> "$DRIVER_KCONFIG"
		fi
		echo "[+] Modified Kconfig."
	fi
}

# Reverts modifications made by this script
perform_cleanup() {
	echo "[+] Cleaning up..."
	if [ -L "$DRIVER_DIR/kernelsu" ]; then
		rm "$DRIVER_DIR/kernelsu"
		echo "[-] Symlink removed."
	fi
	if grep -q "kernelsu" "$DRIVER_MAKEFILE" 2>/dev/null; then
		sed -i '/kernelsu/d' "$DRIVER_MAKEFILE"
		echo "[-] Makefile reverted."
	fi
	if grep -q "drivers/kernelsu/Kconfig" "$DRIVER_KCONFIG" 2>/dev/null; then
		sed -i '/drivers\/kernelsu\/Kconfig/d' "$DRIVER_KCONFIG"
		echo "[-] Kconfig reverted."
	fi
	if [ -d "$GKI_ROOT/KernelSU" ]; then
		rm -rf "$GKI_ROOT/KernelSU"
		echo "[-] KernelSU directory deleted."
	fi
	if [ -f "$GKI_ROOT/.gitmodules" ] && grep -q 'KernelSU' "$GKI_ROOT/.gitmodules"; then
		echo "[!] KernelSU has been added as a submodule."
		echo "[!] Please remove it manually."
		echo "[!] You can run the following commands:"
		echo "--- git submodule deinit -f KernelSU"
		echo "--- git rm -f KernelSU"
		echo "--- git commit -m 'Remove KernelSU submodule'"
	fi
}

checkout_ref() {
	# $1: optional ref (tag / branch / commit). Empty = latest tag, fallback to DEFAULT_BRANCH.
	ref="${1-}"
	if [ -z "$ref" ]; then
		if latest_tag=$(git describe --abbrev=0 --tags 2>/dev/null); then
			ref="$latest_tag"
		else
			echo "[!] No tags found, falling back to $DEFAULT_BRANCH."
			ref="$DEFAULT_BRANCH"
		fi
	fi
	if git checkout "$ref"; then
		echo "[-] Checked out $ref."
	else
		echo "[ERROR] Failed to check out '$ref'." >&2
		exit 1
	fi
}

# Sets up or update OriginSU environment
setup_kernelsu() {
	echo "[+] Setting up OriginSU..."
	# Clone the repository and rename it to KernelSU
	if [ ! -d "$GKI_ROOT/KernelSU" ]; then
		git clone "$REPO_URL" "$GKI_ROOT/KernelSU"
		echo "[+] Repository cloned."
	fi
	cd "$GKI_ROOT/KernelSU"
	# Don't die when there is nothing to stash (set -e is on).
	git stash -u || true
	# If a previous run left us on a detached tag, go back to the main
	# branch before pulling so `git pull` has an upstream to work with.
	# (grep -E: -P/--perl-regexp is not portable to macOS/BSD grep.)
	if git status 2>/dev/null | grep -Eqo 'v[0-9]+(\.[0-9]+)*' >/dev/null 2>&1; then
		git checkout "$DEFAULT_BRANCH" && echo "[-] Switched to $DEFAULT_BRANCH branch."
	fi
	# Only pull when on a branch; on a detached HEAD there is nothing to pull.
	if git symbolic-ref -q HEAD >/dev/null 2>&1; then
		git fetch origin --tags
		git pull --ff-only || echo "[!] 'git pull --ff-only' failed, continuing with local copy."
		echo "[+] Repository updated."
	else
		git fetch origin --tags || true
	fi
	checkout_ref "${1-}"
	cd "$DRIVER_DIR"
	link_driver

	# Add entries in Makefile and Kconfig if not already existing
	patch_makefile
	patch_kconfig
	echo '[+] Done.'
	echo '[!] If you want to add OriginSU as a submodule of your kernel source, run this script with the --submodule argument.'
}

# Setup OriginSU as submodule
setup_submodule() {
	cd "$GKI_ROOT"

	if [ ! -d "$GKI_ROOT/KernelSU" ]; then
		echo '[!] KernelSU directory does not exist. Please run the script without --submodule first.'
		exit 127
	fi

	if [ ! -d "$GKI_ROOT/.git" ]; then
		echo '[!] GKI_ROOT is not a git repository. Skipping submodule setup.'
		return 0
	fi

	if [ "${CI:-false}" = "true" ] || [ "${GITHUB_ACTIONS:-false}" = "true" ]; then
		echo '[!] Running in CI. Skipping submodule setup.'
		return 0
	fi

	if [ -f "$GKI_ROOT/.gitmodules" ] && grep -q 'KernelSU' "$GKI_ROOT/.gitmodules"; then
		echo '[!] KernelSU is already a submodule. Skipping submodule setup.'
		return 0
	fi

	echo '[+] Setting up OriginSU as submodule...'
	git submodule add "$REPO_URL" KernelSU || echo '[!] Failed to add OriginSU as a submodule.'
	echo '[+] Done.'
}

# Process command-line arguments
if [ "$#" -eq 0 ]; then
	initialize_variables
	setup_kernelsu
elif [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
	display_usage
elif [ "$1" = "--submodule" ]; then
	initialize_variables
	setup_submodule
elif [ "$1" = "--cleanup" ]; then
	initialize_variables
	perform_cleanup
else
	initialize_variables
	setup_kernelsu "$@"
fi

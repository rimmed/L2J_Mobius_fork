#!/bin/bash

###############################################################################
# L2J Mobius CT 0 Interlude - Rollback Script
# Restores the previous JARs and config files from backup
###############################################################################

set -e

SERVER_DIR="${HOME}/server"
SERVER_LIBS_DIR="${SERVER_DIR}/libs"
SERVER_GAME_DIR="${SERVER_DIR}/game"
BACKUP_DIR="${SERVER_DIR}/backup"

# ============================================================================
# Colors
# ============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ============================================================================
# Functions
# ============================================================================
print_header() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
}

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# ============================================================================
# Main
# ============================================================================

print_header "L2J Mobius CT 0 Interlude - Rollback"

# Show available backups
print_info "Available backups:"
if [ ! -d "${BACKUP_DIR}" ]; then
    print_error "Backup directory not found: ${BACKUP_DIR}"
    exit 1
fi

# List backups sorted by date (newest first)
BACKUPS=($(ls -1d "${BACKUP_DIR}"/backup_* 2>/dev/null | sort -r))

if [ ${#BACKUPS[@]} -eq 0 ]; then
    print_error "No backups found in ${BACKUP_DIR}"
    exit 1
fi

echo ""
for i in "${!BACKUPS[@]}"; do
    BACKUP_NAME=$(basename "${BACKUPS[$i]}")
    BACKUP_TIME=$(stat -c %y "${BACKUPS[$i]}" | cut -d' ' -f1,2)
    echo "  [$((i+1))] $BACKUP_NAME ($BACKUP_TIME)"
done

echo ""
echo "Which backup to restore? (1-${#BACKUPS[@]}) or 'q' to cancel:"
read -r CHOICE

if [ "$CHOICE" == "q" ] || [ "$CHOICE" == "Q" ]; then
    print_info "Rollback cancelled"
    exit 0
fi

# Validate choice
if ! [[ "$CHOICE" =~ ^[0-9]+$ ]] || [ "$CHOICE" -lt 1 ] || [ "$CHOICE" -gt ${#BACKUPS[@]} ]; then
    print_error "Invalid choice"
    exit 1
fi

SELECTED_BACKUP="${BACKUPS[$((CHOICE-1))]}"
BACKUP_NAME=$(basename "${SELECTED_BACKUP}")

print_info "Selected backup: $BACKUP_NAME"
echo ""
echo "This will STOP the server and restore files from the backup."
echo "Continue? (yes/no):"
read -r CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    print_info "Rollback cancelled"
    exit 0
fi

print_header "Starting Rollback"

# Stop server
print_info "Stopping server..."
if sudo systemctl is-active --quiet l2game; then
    sudo systemctl stop l2game
    sleep 2
    print_info "Server stopped ✓"
else
    print_warning "Server is not running"
fi

echo ""

# Restore JARs
print_info "Restoring GameServer.jar..."
cp "${SELECTED_BACKUP}/GameServer.jar" "${SERVER_LIBS_DIR}/"

print_info "Restoring LoginServer.jar..."
cp "${SELECTED_BACKUP}/LoginServer.jar" "${SERVER_LIBS_DIR}/"

# Restore config if available
if [ -f "${SELECTED_BACKUP}/log.cfg" ]; then
    print_info "Restoring log.cfg..."
    cp "${SELECTED_BACKUP}/log.cfg" "${SERVER_GAME_DIR}/"
fi

echo ""

# Verify
print_info "Verifying restored files..."
for file in GameServer.jar LoginServer.jar; do
    if diff "${SELECTED_BACKUP}/${file}" "${SERVER_LIBS_DIR}/${file}" > /dev/null 2>&1; then
        print_info "  ${file} ✓"
    else
        print_error "  ${file} verification failed!"
    fi
done

echo ""

# Start server
print_info "Starting server..."
sudo systemctl start l2game

for i in {1..30}; do
    if sudo systemctl is-active --quiet l2game; then
        print_info "Server started ✓"
        sleep 2
        break
    fi
    echo -n "."
    sleep 1
done

echo ""
print_header "Rollback Complete ✓"
print_info "Restored from: $BACKUP_NAME"
print_info "Server is running"

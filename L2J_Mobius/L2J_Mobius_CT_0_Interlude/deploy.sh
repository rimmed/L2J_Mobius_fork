#!/bin/bash

###############################################################################
# L2J Mobius CT 0 Interlude - Deployment Script
# This script deploys newly built JARs and config files to the running server
###############################################################################

set -e

# ============================================================================
# Configuration
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_BUILD_DIR="${HOME}/workspace/L2J_Mobius_fork/L2J_Mobius/build/dist"
SERVER_DIR="${HOME}/server"
SERVER_GAME_DIR="${SERVER_DIR}/game"
SERVER_LIBS_DIR="${SERVER_DIR}/libs"
BACKUP_DIR="${SERVER_DIR}/backup"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_NAME="backup_${TIMESTAMP}"

# Source paths
SOURCE_LIBS_DIR="${SOURCE_BUILD_DIR}/libs"
SOURCE_GAME_CONFIG="${HOME}/workspace/L2J_Mobius_fork/L2J_Mobius/L2J_Mobius_CT_0_Interlude/dist/game"

# ============================================================================
# Colors for output
# ============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_step() {
    echo -e "\n${BLUE}▶${NC} $1"
}

# Check if files exist
check_files() {
    if [ ! -f "${SOURCE_LIBS_DIR}/GameServer.jar" ]; then
        print_error "GameServer.jar not found in ${SOURCE_LIBS_DIR}"
        exit 1
    fi

    if [ ! -f "${SOURCE_LIBS_DIR}/LoginServer.jar" ]; then
        print_error "LoginServer.jar not found in ${SOURCE_LIBS_DIR}"
        exit 1
    fi

    print_info "Build artifacts verified ✓"
}

# Stop the server
stop_server() {
    print_step "Stopping L2 Game Server..."

    if sudo systemctl is-active --quiet l2game; then
        print_info "Stopping service..."
        sudo systemctl stop l2game
        sudo systemctl stop l2login

        # Wait for server to stop
        for i in {1..30}; do
            if ! sudo systemctl is-active --quiet l2game; then
                print_info "Server stopped ✓"
                sleep 2
                return 0
            fi
            echo -n "."
            sleep 1
        done

        print_error "Server failed to stop within 30 seconds"
        exit 1
    else
        print_info "Server is not running"
    fi
}

# Create backup
create_backup() {
    print_step "Creating backup..."

    mkdir -p "${BACKUP_DIR}/${BACKUP_NAME}"

    cp "${SERVER_LIBS_DIR}/GameServer.jar" "${BACKUP_DIR}/${BACKUP_NAME}/"
    cp "${SERVER_LIBS_DIR}/LoginServer.jar" "${BACKUP_DIR}/${BACKUP_NAME}/"
    cp "${SERVER_GAME_DIR}/log.cfg" "${BACKUP_DIR}/${BACKUP_NAME}/" 2>/dev/null || true

    print_info "Backup created: ${BACKUP_DIR}/${BACKUP_NAME} ✓"
}

# Deploy JARs
deploy_jars() {
    print_step "Deploying new JARs..."

    print_info "Copying GameServer.jar..."
    cp "${SOURCE_LIBS_DIR}/GameServer.jar" "${SERVER_LIBS_DIR}/"

    print_info "Copying LoginServer.jar..."
    cp "${SOURCE_LIBS_DIR}/LoginServer.jar" "${SERVER_LIBS_DIR}/"

    print_info "JARs deployed ✓"
}

# Deploy config files
deploy_configs() {
    print_step "Updating configuration files..."

    if [ -f "${SOURCE_GAME_CONFIG}/log.cfg" ]; then
        print_info "Updating log.cfg..."
        cp "${SOURCE_GAME_CONFIG}/log.cfg" "${SERVER_GAME_DIR}/"
        print_info "log.cfg updated ✓"
    fi
}

# Verify deployment
verify_deployment() {
    print_step "Verifying deployment..."

    # Check GameServer.jar timestamp
    SOURCE_TIME=$(stat -c %Y "${SOURCE_LIBS_DIR}/GameServer.jar")
    DEPLOYED_TIME=$(stat -c %Y "${SERVER_LIBS_DIR}/GameServer.jar")

    if [ "$SOURCE_TIME" -eq "$DEPLOYED_TIME" ]; then
        print_info "GameServer.jar verified ✓"
    else
        print_warning "GameServer.jar timestamps differ"
    fi

    # Check LoginServer.jar timestamp
    SOURCE_TIME=$(stat -c %Y "${SOURCE_LIBS_DIR}/LoginServer.jar")
    DEPLOYED_TIME=$(stat -c %Y "${SERVER_LIBS_DIR}/LoginServer.jar")

    if [ "$SOURCE_TIME" -eq "$DEPLOYED_TIME" ]; then
        print_info "LoginServer.jar verified ✓"
    else
        print_warning "LoginServer.jar timestamps differ"
    fi
}

# Start the server
start_server() {
    print_step "Starting L2 Game Server..."

    sudo systemctl start l2login
    sleep 10
    sudo systemctl start l2game

    # Wait for server to start
    for i in {1..30}; do
        if sudo systemctl is-active --quiet l2game; then
            print_info "Server started ✓"
            sleep 2
            return 0
        fi
        echo -n "."
        sleep 1
    done

    print_error "Server failed to start within 30 seconds"
    exit 1
}

# Show server status
show_status() {
    print_step "Server Status"

    if sudo systemctl is-active --quiet l2game; then
        print_info "Service status: RUNNING"

        # Show process info if available
        if pgrep -f "GameServer" > /dev/null; then
            PID=$(pgrep -f "GameServer" | head -1)
            print_info "Process ID: $PID"
        fi
    else
        print_error "Service status: STOPPED"
    fi
}

# Show recent logs
show_logs() {
    print_step "Recent Game Server Logs"

    if [ -f "${SERVER_GAME_DIR}/log/game.log" ]; then
        print_info "Last 20 lines of game.log:"
        tail -20 "${SERVER_GAME_DIR}/log/game.log"
    else
        print_warning "game.log not found"
    fi
}

# ============================================================================
# Main Execution
# ============================================================================

print_header "L2J Mobius CT 0 Interlude - Deployment"

echo "Configuration:"
echo "  Source Build: ${SOURCE_BUILD_DIR}"
echo "  Server Dir:   ${SERVER_DIR}"
echo "  Backup Dir:   ${BACKUP_DIR}/${BACKUP_NAME}"
echo ""

# Check for help flag
if [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --verify-only    Only verify files without deployment"
    echo "  --no-restart     Deploy without restarting server"
    echo "  --help, -h       Show this help message"
    exit 0
fi

# Pre-deployment checks
print_header "Pre-Deployment Checks"
check_files

# Backup current state
create_backup

# Stop server
stop_server

# Deploy new files
deploy_jars
deploy_configs

# Verify
verify_deployment

# Start server
start_server

# Post-deployment status
print_header "Deployment Complete"
show_status
echo ""
print_info "New backup location: ${BACKUP_DIR}/${BACKUP_NAME}"
print_info "To rollback: cp ${BACKUP_DIR}/${BACKUP_NAME}/*.jar ${SERVER_LIBS_DIR}/"

echo ""
show_logs

print_header "Deployment Successful ✓"

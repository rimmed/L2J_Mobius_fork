#!/bin/bash

###############################################################################
# L2J Mobius CT 0 Interlude - Build & Deploy Script
# Compiles the project and deploys to the running server
###############################################################################

set -e

PROJECT_ROOT="${HOME}/workspace/L2J_Mobius_fork/L2J_Mobius/L2J_Mobius_CT_0_Interlude"
DEPLOY_SCRIPT="${PROJECT_ROOT}/deploy.sh"

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

# ============================================================================
# Main
# ============================================================================

print_header "L2J Mobius CT 0 Interlude - Build & Deploy"

# Step 1: Compile
print_header "Step 1: Compiling Project"
cd "${PROJECT_ROOT}"
/usr/bin/ant compile
if [ $? -ne 0 ]; then
    print_error "Compilation failed!"
    exit 1
fi
print_info "Compilation successful ✓"

# Step 2: Build JARs
print_header "Step 2: Building JARs"
/usr/bin/ant jar
if [ $? -ne 0 ]; then
    print_error "JAR build failed!"
    exit 1
fi
print_info "JAR build successful ✓"

# Step 3: Deploy
print_header "Step 3: Deploying to Server"
echo ""
"${DEPLOY_SCRIPT}"

print_header "Build & Deploy Complete ✓"
print_info "Total time: ~30-60 seconds"

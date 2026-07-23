#!/usr/bin/env bash
#
# End-to-end API smoke test for the Order Processing System.
# Run it while the app is up (mvn spring-boot:run):
#
#     bash scripts/test-api.sh
#
# It walks every endpoint and prints labelled, pretty-printed responses.
# Requires: curl + python3 (both preinstalled on macOS).

set -euo pipefail
BASE="${BASE:-http://localhost:8080}"

# ---- helpers ---------------------------------------------------------------
pp() { python3 -m json.tool 2>/dev/null || cat; }        # pretty-print JSON
banner() { printf "\n\033[1;36m========== %s ==========\033[0m\n" "$1"; }
req() {  # method path [json-body]
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -s -X "$method" "$BASE$path" \
         -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -X "$method" "$BASE$path"
  fi
}
code() {  # print just the HTTP status for a call
  local method="$1" path="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$path" \
         -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$path"
  fi
}

ORDER='{
  "customerId": 1001,
  "customerEmail": "jane@example.com",
  "customerName": "Jane Doe",
  "shippingAddress": "123 Main St",
  "notes": "Leave at the door",
  "items": [
    { "productId": 101, "productSku": "SKU-1", "productName": "Widget", "quantity": 2, "unitPrice": 19.99 },
    { "productId": 102, "productSku": "SKU-2", "productName": "Gadget", "quantity": 1, "unitPrice": 49.50 }
  ]
}'

# ---- 1. create ------------------------------------------------------------
banner "1. CREATE ORDER (expect 201)"
CREATED="$(req POST /api/v1/orders "$ORDER")"
echo "$CREATED" | pp
ID="$(echo "$CREATED"   | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')"
NUM="$(echo "$CREATED"  | python3 -c 'import sys,json;print(json.load(sys.stdin)["orderNumber"])')"
echo "-> id=$ID  orderNumber=$NUM"

# ---- 2. validation --------------------------------------------------------
banner "2. CREATE INVALID (quantity 0 -> expect 400)"
req POST /api/v1/orders '{ "customerId": 1001, "items": [ { "productId": 1, "productSku": "X", "productName": "X", "quantity": 0, "unitPrice": 1 } ] }' | pp

# ---- 3. retrieve ----------------------------------------------------------
banner "3a. GET BY ID (expect 200)"
req GET "/api/v1/orders/$ID" | pp
banner "3b. GET BY ORDER NUMBER (expect 200)"
req GET "/api/v1/orders/number/$NUM" | pp
banner "3c. GET UNKNOWN ID (expect 404)"
req GET "/api/v1/orders/999999" | pp

# ---- 4. status ------------------------------------------------------------
banner "4a. UPDATE STATUS -> PROCESSING (expect 200)"
req PATCH "/api/v1/orders/$ID/status" '{ "status": "PROCESSING", "reason": "verified" }' | pp
banner "4b. ILLEGAL JUMP -> DELIVERED (expect 409)"
req PATCH "/api/v1/orders/$ID/status" '{ "status": "DELIVERED" }' | pp

# ---- 5. list --------------------------------------------------------------
banner "5a. LIST ALL"
req GET "/api/v1/orders?page=0&size=5&sortBy=createdAt&sortDir=desc" | pp
banner "5b. LIST FILTER status=PROCESSING & customerId=1001"
req GET "/api/v1/orders?status=PROCESSING&customerId=1001" | pp

# ---- 6. cancel rules ------------------------------------------------------
banner "6a. CANCEL PROCESSING ORDER (expect 409)"
req POST "/api/v1/orders/$ID/cancel" | pp
banner "6b. CREATE FRESH + CANCEL WHILE PENDING (expect 200 CANCELLED)"
FRESH_ID="$(req POST /api/v1/orders "$ORDER" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')"
req POST "/api/v1/orders/$FRESH_ID/cancel" | pp

# ---- 7. statistics --------------------------------------------------------
banner "7. STATISTICS"
req GET "/api/v1/orders/statistics" | pp

# ---- summary --------------------------------------------------------------
banner "STATUS-CODE SUMMARY"
printf "create ............... %s (want 201)\n" "$(code POST /api/v1/orders "$ORDER")"
printf "invalid create ...... %s (want 400)\n" "$(code POST /api/v1/orders '{"customerId":1,"items":[]}')"
printf "get by id ........... %s (want 200)\n" "$(code GET "/api/v1/orders/$ID")"
printf "get unknown ......... %s (want 404)\n" "$(code GET /api/v1/orders/999999)"
printf "illegal transition .. %s (want 409)\n" "$(code PATCH "/api/v1/orders/$ID/status" '{"status":"DELIVERED"}')"
printf "statistics .......... %s (want 200)\n" "$(code GET /api/v1/orders/statistics)"
echo
echo "Done."

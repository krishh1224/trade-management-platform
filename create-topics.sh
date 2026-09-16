#!/usr/bin/env bash
set -euo pipefail

CONTAINER="kafka-sprint7"
BROKER="localhost:29092"
BIN="/opt/kafka/bin/kafka-topics.sh"

create_topic() {
  local topic="$1" partitions="$2" retention_ms="$3"
  echo "  Creating: ${topic}  (partitions=${partitions}, retention=${retention_ms}ms)"
  docker exec "${CONTAINER}" "${BIN}" \
    --bootstrap-server "${BROKER}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${partitions}" \
    --replication-factor 1 \
    --config retention.ms="${retention_ms}" \
    --config cleanup.policy=delete
}

echo ""
echo "====== Creating main topics ======"
create_topic "orders"        3  604800000
create_topic "trade-events"  3  2592000000
create_topic "market-data"   6  86400000

echo ""
echo "====== Creating dead-letter topics ======"
create_topic "orders.DLT"        1  1209600000
create_topic "trade-events.DLT"  1  1209600000
create_topic "market-data.DLT"   1  1209600000

echo ""
echo "====== Verifying ======"
docker exec "${CONTAINER}" "${BIN}" --bootstrap-server "${BROKER}" --list

echo ""
echo "---- orders ----"
docker exec "${CONTAINER}" "${BIN}" --bootstrap-server "${BROKER}" --describe --topic orders

echo ""
echo "---- trade-events ----"
docker exec "${CONTAINER}" "${BIN}" --bootstrap-server "${BROKER}" --describe --topic trade-events

echo ""
echo "---- market-data ----"
docker exec "${CONTAINER}" "${BIN}" --bootstrap-server "${BROKER}" --describe --topic market-data

echo ""
echo "Done. All 6 topics created and verified."
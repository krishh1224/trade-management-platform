# Team 5 - Historical Trade Data Design

## Data Modeling & Retention Grain

The historical storage retains data at the **execution (fill) grain**, rather than the order grain. A single parent order can result in multiple partial fills at different times, prices, and venues. 

An event consumer service subscribes to the execution stream (e.g., Kafka or RabbitMQ) published by the trading matching engine or OMS. This consumer parses the events and persists them into the relational database. The schema explicitly captures execution-specific details not present in the original order intent:

| Field | Type | Description |
|---|---|---|
| `execution_id` | UUID | Primary key; uniquely identifies the specific fill. |
| `parent_order_id` | String | Foreign key linking back to the original order intent. |
| `fill_price` | Decimal | The actual executed price (often different from the limit order price). |
| `fill_quantity` | Integer | The volume executed in this specific transaction. |
| `venue` | String | The exchange or dark pool where the matching occurred. |
| `commission` | Decimal | Execution-specific fees and trading costs. |
| `updated_at` | Timestamp | System write time, strictly used for ETL synchronization. |

## Incremental Extraction (Hourly Micro-Batch)

To support the Sprint 7 extract without executing exhaustive full table scans on historical data, the system utilizes a **High-Water Mark (HWM)** pattern.

* The downstream ETL orchestrator initiates an hourly pull.
* The query filters exclusively using the database timestamp: `WHERE updated_at > :last_batch_timestamp`.
* A B-tree index on the `updated_at` column ensures the database only reads and returns rows created or modified within the last 60 minutes.
* Upon a successful sync, the downstream warehouse logs the maximum `updated_at` value received to use as the parameter for the next hourly run.

## Scalability, Growth, and Cost

The current baseline volume of 10,000 to 50,000 trades per day translates to less than 1 write per second. The architectural choice to decouple ingestion via an event stream ensures stability.

If volume scales 100x to 5 million trades per day:
* **Write Performance:** The event stream acts as a shock absorber during intense market open/close spikes. The consumer service writes to the relational database at a smoothed rate of roughly 60 transactions per second (TPS). Any modern PostgreSQL or MySQL instance handles 60 TPS with minimal compute allocation.
* **Cost:** Storage remains inexpensive. The primary write cost is maintaining the index on `updated_at` and `parent_order_id`, which imposes a negligible CPU overhead at this scale. Operational complexity is strictly capped because we avoid introducing specialized time-series databases or Change Data Capture (CDC) pipelines.

## Partitioning & Archival Position

**Position:** Database partitioning and aggressive data archiving are **not required** at the current scale or the projected 100x volume.

**Defense:** At 50,000 trades per day, a full year of history generates roughly 18 million rows. Even if volume suddenly jumps 100x, an indexed table of that size fits comfortably within the memory and performance thresholds of standard relational database tiering. Introducing table partitioning (e.g., by month or day) introduces immediate operational complexity—such as partition management and query routing overhead—without yielding measurable query performance benefits for an hourly indexed extract. All historical data will be retained in a single unified table. We will reconsider partitioning only when index size exceeds available working memory.

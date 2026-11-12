# Architecture And Current Limits

## Data Ownership

PostgreSQL owns companies, jobs, users, application tracking, scraper runs, outbox rows, and processed-event records. Liquibase manages the schema. Elasticsearch's `jobs` index is a projection that can be repopulated using the admin reindex endpoint. This endpoint is not an alias-based, zero-downtime migration.

## Event-Driven Enrichment

1. A scraper normalizes and saves a career-site listing.
2. It saves outbox requests for baseline indexing, geocoding, and visa detection.
3. The scheduled publisher sends pending envelopes to Kafka, keyed by aggregate ID, then marks the rows published.
4. Geocoding and visa consumers update PostgreSQL and save follow-up indexing requests through the outbox.
5. The indexing consumer reloads the job from PostgreSQL and saves an Elasticsearch document.

Implementation references:

- [BaseScraper](../src/main/java/com/jobportal/service/scraper/BaseScraper.java)
- [OutboxEventService](../src/main/java/com/jobportal/service/outbox/OutboxEventService.java)
- [GeocodingConsumer](../src/main/java/com/jobportal/kafka/consumer/GeocodingConsumer.java)
- [VisaDetectionConsumer](../src/main/java/com/jobportal/kafka/consumer/VisaDetectionConsumer.java)
- [JobIndexingConsumer](../src/main/java/com/jobportal/kafka/consumer/JobIndexingConsumer.java)
- [IdempotencyService](../src/main/java/com/jobportal/service/idempotency/IdempotencyService.java)

## Reliability Boundaries

The Outbox implementation has gaps to address before scaling:

- Scraper job saves and outbox writes lack one encompassing transaction. A saved job can miss an event if outbox persistence fails. Both operations should share a transactional service boundary.
- Relay polling has no exclusive claim or `FOR UPDATE SKIP LOCKED`, so multiple instances may publish the same rows. Reads are unbounded and sends hold the database transaction open. Batching, claiming, backoff, and replay tooling are follow-up work.
- A Kafka send can succeed before the publisher's database commit fails. Duplicates remain possible; producer settings alone cannot make the two commits atomic.
- Processed-event markers commit before the work completes and use aggregate ID plus action. Failures may be skipped on retry, and later legitimate events can be suppressed. Deduplication should use event identity and commit with successful database work; index writes also need safe replay and ordering.
- Consumers catch and log exceptions. When a handler returns successfully after an error, container retries and dead-letter routing cannot be relied on. Recovery needs explicit failure tests.
- Manual visa reprocessing still publishes its index request directly, bypassing the outbox. A failed publish can leave search stale.

This packaging pass documents those limits; it does not redesign the delivery guarantees.

## Product Boundaries

External sites may change or rate-limit access. Structured-data support requires accessible JobPosting entries; it is not a universal browser crawler. Visa classification interprets a description and is not a promise of sponsorship.

Caching and asynchronous indexing can briefly show stale data. Search has an Elasticsearch execution fallback, but startup index initializers still expect Elasticsearch availability. Local Compose uses exposed, single-node services and is intended for development, not high availability.

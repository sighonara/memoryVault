#!/usr/bin/env bash
# seed-dev-data.sh
#
# Inserts realistic development data into the MemoryVault database.
# Run from the project root directory after the database is up and migrations have run.
#
# Usage:
#   ./scripts/seed-dev-data.sh
#
# Idempotent: uses ON CONFLICT DO NOTHING for tags, and deletes/re-inserts
# seed bookmarks, feeds, and feed items (identified by 'seed-' prefixed GUIDs
# and deterministic UUIDs).

set -euo pipefail

PSQL="docker compose exec -T postgres psql -U memoryvault -d memoryvault"

echo "Seeding development data..."

$PSQL <<'SQL'
BEGIN;

-- ============================================================
-- Deterministic UUIDs for seed data
-- ============================================================
-- Tags
-- (generated via ON CONFLICT, so we use name-based lookup later)

-- Bookmarks: seed-bm-01 through seed-bm-10
-- Feeds:     seed-feed-01 through seed-feed-04

-- ============================================================
-- Constants
-- ============================================================
DO $$
DECLARE
    uid UUID := '00000000-0000-0000-0000-000000000001';

    -- Tag IDs (deterministic so we can reference them)
    tag_programming UUID := 'a0000000-0000-0000-0000-000000000001';
    tag_kotlin      UUID := 'a0000000-0000-0000-0000-000000000002';
    tag_web         UUID := 'a0000000-0000-0000-0000-000000000003';
    tag_devops      UUID := 'a0000000-0000-0000-0000-000000000004';
    tag_news        UUID := 'a0000000-0000-0000-0000-000000000005';
    tag_design      UUID := 'a0000000-0000-0000-0000-000000000006';

    -- Bookmark IDs
    bm01 UUID := 'b0000000-0000-0000-0000-000000000001';
    bm02 UUID := 'b0000000-0000-0000-0000-000000000002';
    bm03 UUID := 'b0000000-0000-0000-0000-000000000003';
    bm04 UUID := 'b0000000-0000-0000-0000-000000000004';
    bm05 UUID := 'b0000000-0000-0000-0000-000000000005';
    bm06 UUID := 'b0000000-0000-0000-0000-000000000006';
    bm07 UUID := 'b0000000-0000-0000-0000-000000000007';
    bm08 UUID := 'b0000000-0000-0000-0000-000000000008';
    bm09 UUID := 'b0000000-0000-0000-0000-000000000009';
    bm10 UUID := 'b0000000-0000-0000-0000-000000000010';

    -- Feed IDs
    feed01 UUID := 'f0000000-0000-0000-0000-000000000001';
    feed02 UUID := 'f0000000-0000-0000-0000-000000000002';
    feed03 UUID := 'f0000000-0000-0000-0000-000000000003';
    feed04 UUID := 'f0000000-0000-0000-0000-000000000004';

    -- Feed Item IDs
    fi UUID;

BEGIN

    -- ============================================================
    -- Clean up previous seed data (idempotent re-run)
    -- ============================================================
    DELETE FROM bookmark_tags WHERE bookmark_id IN (bm01,bm02,bm03,bm04,bm05,bm06,bm07,bm08,bm09,bm10);
    DELETE FROM feed_item_tags WHERE feed_item_id IN (
        SELECT id FROM feed_items WHERE feed_id IN (feed01,feed02,feed03,feed04)
    );
    DELETE FROM feed_items WHERE feed_id IN (feed01,feed02,feed03,feed04);
    DELETE FROM feeds WHERE id IN (feed01,feed02,feed03,feed04);
    DELETE FROM bookmarks WHERE id IN (bm01,bm02,bm03,bm04,bm05,bm06,bm07,bm08,bm09,bm10);
    DELETE FROM tags WHERE id IN (tag_programming,tag_kotlin,tag_web,tag_devops,tag_news,tag_design);

    -- ============================================================
    -- Tags
    -- ============================================================
    INSERT INTO tags (id, user_id, name, color) VALUES
        (tag_programming, uid, 'programming', '#3B82F6'),
        (tag_kotlin,      uid, 'kotlin',      '#7C3AED'),
        (tag_web,         uid, 'web',         '#10B981'),
        (tag_devops,      uid, 'devops',      '#F59E0B'),
        (tag_news,        uid, 'news',        '#EF4444'),
        (tag_design,      uid, 'design',      '#EC4899')
    ON CONFLICT DO NOTHING;

    -- ============================================================
    -- Bookmarks
    -- ============================================================
    INSERT INTO bookmarks (id, user_id, url, title, created_at, updated_at) VALUES
        (bm01, uid, 'https://kotlinlang.org/docs/coroutines-overview.html',
            'Kotlin Coroutines Overview',
            now() - interval '12 days', now() - interval '12 days'),
        (bm02, uid, 'https://spring.io/blog/2024/11/spring-boot-4-0',
            'Spring Boot 4.0 Release Notes',
            now() - interval '10 days', now() - interval '10 days'),
        (bm03, uid, 'https://angular.dev/guide/signals',
            'Angular Signals Guide',
            now() - interval '9 days', now() - interval '9 days'),
        (bm04, uid, 'https://www.terraform.io/docs',
            'Terraform Documentation',
            now() - interval '8 days', now() - interval '8 days'),
        (bm05, uid, 'https://docs.docker.com/compose/',
            'Docker Compose Documentation',
            now() - interval '7 days', now() - interval '7 days'),
        (bm06, uid, 'https://graphql.org/learn/',
            'Introduction to GraphQL',
            now() - interval '5 days', now() - interval '5 days'),
        (bm07, uid, 'https://news.ycombinator.com',
            'Hacker News',
            now() - interval '4 days', now() - interval '4 days'),
        (bm08, uid, 'https://blog.jetbrains.com/kotlin/',
            'JetBrains Kotlin Blog',
            now() - interval '3 days', now() - interval '3 days'),
        (bm09, uid, 'https://tailwindcss.com/docs/utility-first',
            'Tailwind CSS Utility-First Fundamentals',
            now() - interval '2 days', now() - interval '2 days'),
        (bm10, uid, 'https://htmx.org/docs/',
            'htmx Documentation',
            now() - interval '1 day', now() - interval '1 day')
    ON CONFLICT DO NOTHING;

    -- ============================================================
    -- Bookmark Tags
    -- ============================================================
    INSERT INTO bookmark_tags (bookmark_id, tag_id) VALUES
        (bm01, tag_programming), (bm01, tag_kotlin),
        (bm02, tag_programming), (bm02, tag_kotlin),
        (bm03, tag_programming), (bm03, tag_web),
        (bm04, tag_devops),
        (bm05, tag_devops),
        (bm06, tag_programming), (bm06, tag_web),
        (bm07, tag_news),
        (bm08, tag_kotlin),
        (bm09, tag_web),         (bm09, tag_design),
        (bm10, tag_web)
    ON CONFLICT DO NOTHING;

    -- ============================================================
    -- Feeds
    -- ============================================================
    INSERT INTO feeds (id, user_id, url, title, description, site_url, last_fetched_at, fetch_interval_minutes) VALUES
        (feed01, uid,
            'https://blog.jetbrains.com/kotlin/feed/',
            'Kotlin Blog',
            'Official Kotlin blog by JetBrains',
            'https://blog.jetbrains.com/kotlin/',
            now() - interval '2 hours', 60),
        (feed02, uid,
            'https://spring.io/blog.atom',
            'Spring Blog',
            'Official Spring Framework blog',
            'https://spring.io/blog',
            now() - interval '3 hours', 60),
        (feed03, uid,
            'https://hnrss.org/newest?points=100',
            'Hacker News Top',
            'Hacker News stories with 100+ points',
            'https://news.ycombinator.com',
            now() - interval '1 hour', 30),
        (feed04, uid,
            'https://feeds.arstechnica.com/arstechnica/index',
            'Ars Technica',
            'Ars Technica - technology news and analysis',
            'https://arstechnica.com',
            now() - interval '4 hours', 60)
    ON CONFLICT DO NOTHING;

    -- ============================================================
    -- Feed Items — Kotlin Blog (feed01)
    -- ============================================================
    INSERT INTO feed_items (feed_id, guid, title, url, content, author, published_at, read_at) VALUES
        (feed01, 'seed-kotlin-1', 'Kotlin 2.2 Released with Context Parameters',
            'https://blog.jetbrains.com/kotlin/2026/02/kotlin-2-2/',
            '<p>We are excited to announce Kotlin 2.2, featuring context parameters, improved K2 compiler performance, and new multiplatform targets.</p>',
            'Kotlin Team', now() - interval '13 days', now() - interval '12 days'),
        (feed01, 'seed-kotlin-2', 'Kotlin Multiplatform: State of the Ecosystem 2026',
            'https://blog.jetbrains.com/kotlin/2026/02/kmp-ecosystem/',
            '<p>An overview of the growing Kotlin Multiplatform ecosystem, including Compose Multiplatform adoption metrics and community library growth.</p>',
            'Svetlana Isakova', now() - interval '11 days', NULL),
        (feed01, 'seed-kotlin-3', 'Coroutines 2.0: Structured Concurrency Improvements',
            'https://blog.jetbrains.com/kotlin/2026/02/coroutines-2/',
            '<p>Kotlin Coroutines 2.0 brings enhanced structured concurrency, improved cancellation propagation, and a new flow debugging API.</p>',
            'Roman Elizarov', now() - interval '8 days', NULL),
        (feed01, 'seed-kotlin-4', 'KotlinConf 2026 Registration Now Open',
            'https://blog.jetbrains.com/kotlin/2026/03/kotlinconf-2026/',
            '<p>KotlinConf 2026 will be held in Amsterdam this May. Early bird registration is now open with workshops on KMP, server-side, and Android.</p>',
            'Kotlin Team', now() - interval '5 days', now() - interval '4 days'),
        (feed01, 'seed-kotlin-5', 'Kotlin Tips: Sealed Interfaces for State Machines',
            'https://blog.jetbrains.com/kotlin/2026/03/sealed-interfaces-tips/',
            '<p>Learn how to model complex state machines using sealed interfaces and when expressions for exhaustive handling in Kotlin.</p>',
            'Anton Arhipov', now() - interval '2 days', NULL),
        (feed01, 'seed-kotlin-6', 'Introducing Kotlin Notebook for IntelliJ IDEA',
            'https://blog.jetbrains.com/kotlin/2026/03/kotlin-notebook/',
            '<p>Kotlin Notebook brings interactive computing to IntelliJ IDEA, enabling data exploration and prototyping directly in your IDE.</p>',
            'Kotlin Team', now() - interval '1 day', NULL);

    -- ============================================================
    -- Feed Items — Spring Blog (feed02)
    -- ============================================================
    INSERT INTO feed_items (feed_id, guid, title, url, content, author, published_at, read_at) VALUES
        (feed02, 'seed-spring-1', 'Spring Boot 4.0.1 Maintenance Release',
            'https://spring.io/blog/2026/02/spring-boot-4-0-1',
            '<p>Spring Boot 4.0.1 is now available with bug fixes for auto-configuration, improved GraalVM native image support, and dependency upgrades.</p>',
            'Phil Webb', now() - interval '12 days', now() - interval '11 days'),
        (feed02, 'seed-spring-2', 'Spring AI 2.0: Production-Ready MCP Support',
            'https://spring.io/blog/2026/02/spring-ai-2-0',
            '<p>Spring AI 2.0 graduates from milestone to GA, bringing stable MCP server and client support, improved tool calling, and RAG abstractions.</p>',
            'Mark Pollack', now() - interval '10 days', now() - interval '9 days'),
        (feed02, 'seed-spring-3', 'Migrating to Spring Framework 7: A Guide',
            'https://spring.io/blog/2026/03/migrating-spring-7',
            '<p>A comprehensive guide to migrating your applications from Spring Framework 6 to 7, covering breaking changes, new features, and best practices.</p>',
            'Juergen Hoeller', now() - interval '7 days', NULL),
        (feed02, 'seed-spring-4', 'Spring Cloud 2026.0 Release Train',
            'https://spring.io/blog/2026/03/spring-cloud-2026',
            '<p>The Spring Cloud 2026.0 release train is available with updates to Gateway, Config, and improved Kubernetes-native support.</p>',
            'Spencer Gibb', now() - interval '4 days', NULL),
        (feed02, 'seed-spring-5', 'Virtual Threads Best Practices in Spring Boot',
            'https://spring.io/blog/2026/03/virtual-threads-best-practices',
            '<p>Best practices for leveraging Project Loom virtual threads in Spring Boot 4.x applications, including common pitfalls and performance tips.</p>',
            'Brian Clozel', now() - interval '1 day', NULL);

    -- ============================================================
    -- Feed Items — Hacker News Top (feed03)
    -- ============================================================
    INSERT INTO feed_items (feed_id, guid, title, url, content, author, published_at, read_at) VALUES
        (feed03, 'seed-hn-1', 'Show HN: I built a self-hosted alternative to Notion',
            'https://news.ycombinator.com/item?id=39001234',
            NULL, NULL, now() - interval '11 days', now() - interval '10 days'),
        (feed03, 'seed-hn-2', 'SQLite as a document database',
            'https://news.ycombinator.com/item?id=39002345',
            NULL, NULL, now() - interval '9 days', NULL),
        (feed03, 'seed-hn-3', 'Why we moved from Kubernetes to a single server',
            'https://news.ycombinator.com/item?id=39003456',
            NULL, NULL, now() - interval '7 days', NULL),
        (feed03, 'seed-hn-4', 'The architecture of a modern startup (2026)',
            'https://news.ycombinator.com/item?id=39004567',
            NULL, NULL, now() - interval '5 days', now() - interval '4 days'),
        (feed03, 'seed-hn-5', 'Postgres 17 performance benchmarks',
            'https://news.ycombinator.com/item?id=39005678',
            NULL, NULL, now() - interval '3 days', NULL),
        (feed03, 'seed-hn-6', 'Ask HN: What are you building in 2026?',
            'https://news.ycombinator.com/item?id=39006789',
            NULL, NULL, now() - interval '2 days', NULL),
        (feed03, 'seed-hn-7', 'Rust vs Go for backend services in 2026',
            'https://news.ycombinator.com/item?id=39007890',
            NULL, NULL, now() - interval '1 day', NULL);

    -- ============================================================
    -- Feed Items — Ars Technica (feed04)
    -- ============================================================
    INSERT INTO feed_items (feed_id, guid, title, url, content, author, published_at, read_at) VALUES
        (feed04, 'seed-ars-1', 'AMD unveils next-gen Zen 6 architecture with 30% IPC gains',
            'https://arstechnica.com/gadgets/2026/02/amd-zen6/',
            '<p>AMD has officially revealed the Zen 6 architecture at its Tech Day event, promising a 30% improvement in instructions per clock along with significant power efficiency gains.</p>',
            'Andrew Cunningham', now() - interval '13 days', now() - interval '12 days'),
        (feed04, 'seed-ars-2', 'The hidden costs of cloud computing: a 2026 retrospective',
            'https://arstechnica.com/information-technology/2026/03/cloud-costs/',
            '<p>As companies mature their cloud strategies, many are discovering that the promise of reduced infrastructure costs has not materialized as expected.</p>',
            'Sean Gallagher', now() - interval '9 days', NULL),
        (feed04, 'seed-ars-3', 'EU Digital Markets Act enforcement begins with first major fines',
            'https://arstechnica.com/tech-policy/2026/03/dma-enforcement/',
            '<p>The European Commission has issued its first significant fines under the Digital Markets Act, targeting gatekeeper platforms for non-compliance.</p>',
            'Ashley Belanger', now() - interval '6 days', now() - interval '5 days'),
        (feed04, 'seed-ars-4', 'NASA confirms Artemis III crew landing date for late 2027',
            'https://arstechnica.com/science/2026/03/artemis-iii-date/',
            '<p>After years of delays, NASA has set a firm date for the Artemis III crewed lunar landing, with hardware integration now on track.</p>',
            'Eric Berger', now() - interval '3 days', NULL),
        (feed04, 'seed-ars-5', 'Linux 6.14 brings major improvements to Btrfs and io_uring',
            'https://arstechnica.com/gadgets/2026/03/linux-6-14/',
            '<p>The latest Linux kernel release includes significant performance improvements to the Btrfs filesystem and expanded io_uring capabilities.</p>',
            'Jim Salter', now() - interval '1 day', NULL);

    -- ============================================================
    -- Feed Item Tags (tag a few items)
    -- ============================================================
    INSERT INTO feed_item_tags (feed_item_id, tag_id)
    SELECT fi.id, tag_kotlin FROM feed_items fi WHERE fi.guid LIKE 'seed-kotlin-%'
    ON CONFLICT DO NOTHING;

    INSERT INTO feed_item_tags (feed_item_id, tag_id)
    SELECT fi.id, tag_programming FROM feed_items fi WHERE fi.guid IN ('seed-kotlin-1','seed-spring-2','seed-hn-5')
    ON CONFLICT DO NOTHING;

    INSERT INTO feed_item_tags (feed_item_id, tag_id)
    SELECT fi.id, tag_news FROM feed_items fi WHERE fi.guid LIKE 'seed-hn-%'
    ON CONFLICT DO NOTHING;

    INSERT INTO feed_item_tags (feed_item_id, tag_id)
    SELECT fi.id, tag_devops FROM feed_items fi WHERE fi.guid IN ('seed-hn-3','seed-ars-2')
    ON CONFLICT DO NOTHING;

END;
$$;

COMMIT;
SQL

echo "Seed data inserted successfully."
echo ""
echo "Summary:"
$PSQL -c "SELECT 'tags' AS entity, count(*) FROM tags WHERE user_id = '00000000-0000-0000-0000-000000000001'
    UNION ALL SELECT 'bookmarks', count(*) FROM bookmarks WHERE user_id = '00000000-0000-0000-0000-000000000001' AND deleted_at IS NULL
    UNION ALL SELECT 'feeds', count(*) FROM feeds WHERE user_id = '00000000-0000-0000-0000-000000000001' AND deleted_at IS NULL
    UNION ALL SELECT 'feed_items', count(*) FROM feed_items fi JOIN feeds f ON fi.feed_id = f.id WHERE f.user_id = '00000000-0000-0000-0000-000000000001'
    ORDER BY entity;"

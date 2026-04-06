package org.sightech.memoryvault.logging

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudWatchLogServiceTest {

    private val cwClient = mockk<CloudWatchLogsClient>()
    private lateinit var service: CloudWatchLogService

    @BeforeEach
    fun setUp() {
        service = CloudWatchLogService(cwClient, "/memoryvault/app")
    }

    private fun completeResponse(vararg rows: List<ResultField>): GetQueryResultsResponse {
        return GetQueryResultsResponse.builder()
            .status(QueryStatus.COMPLETE)
            .results(rows.toList())
            .build() as GetQueryResultsResponse
    }

    private fun runningResponse(): GetQueryResultsResponse {
        return GetQueryResultsResponse.builder()
            .status(QueryStatus.RUNNING)
            .results(emptyList<List<ResultField>>())
            .build() as GetQueryResultsResponse
    }

    @Test
    fun `getLogs returns parsed log entries`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build() as StartQueryResponse

        val row = listOf(
            ResultField.builder().field("@timestamp").value("2026-04-05T10:00:00.000Z").build() as ResultField,
            ResultField.builder().field("level").value("INFO").build() as ResultField,
            ResultField.builder().field("logger").value("o.s.m.feed.FeedService").build() as ResultField,
            ResultField.builder().field("message").value("Feed synced").build() as ResultField,
            ResultField.builder().field("thread").value("main").build() as ResultField
        )

        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns completeResponse(row)

        val logs = service.getLogs(null, null, 10)
        assertEquals(1, logs.size)
        assertEquals("INFO", logs[0].level)
        assertEquals("Feed synced", logs[0].message)
        assertEquals("o.s.m.feed.FeedService", logs[0].logger)
    }

    @Test
    fun `getLogs filters by level in query`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build() as StartQueryResponse
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns completeResponse()

        service.getLogs("ERROR", null, 10)

        verify {
            cwClient.startQuery(match<StartQueryRequest> {
                it.queryString().contains("ERROR")
            })
        }
    }

    @Test
    fun `getLogs filters by logger in query`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build() as StartQueryResponse
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns completeResponse()

        service.getLogs(null, "FeedService", 10)

        verify {
            cwClient.startQuery(match<StartQueryRequest> {
                it.queryString().contains("FeedService")
            })
        }
    }

    @Test
    fun `getLogs returns empty list when no results`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build() as StartQueryResponse
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns completeResponse()

        val logs = service.getLogs(null, null, null)
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `getLogs polls until query completes`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build() as StartQueryResponse
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returnsMany listOf(
            runningResponse(),
            runningResponse(),
            completeResponse()
        )

        val logs = service.getLogs(null, null, 10)
        assertTrue(logs.isEmpty())

        verify(exactly = 3) { cwClient.getQueryResults(any<GetQueryResultsRequest>()) }
    }
}

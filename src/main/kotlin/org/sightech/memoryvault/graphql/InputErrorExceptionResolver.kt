package org.sightech.memoryvault.graphql

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.sightech.memoryvault.feed.service.OpmlTooLargeException
import org.sightech.memoryvault.security.InvalidYoutubeUrlException
import org.sightech.memoryvault.security.UnsafeUrlException
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/** Maps specific user-input rejections to BAD_REQUEST errors that keep their message. */
@Component
class InputErrorExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        if (ex is UnsafeUrlException || ex is InvalidYoutubeUrlException || ex is OpmlTooLargeException) {
            return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(ex.message ?: "Invalid input")
                .build()
        }
        return null
    }
}

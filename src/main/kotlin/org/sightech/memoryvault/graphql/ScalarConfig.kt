package org.sightech.memoryvault.graphql

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.schema.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Configuration
class ScalarConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer {
        return RuntimeWiringConfigurer { builder ->
            builder
                .scalar(uuidScalar())
                .scalar(instantScalar())
        }
    }

    private fun uuidScalar(): GraphQLScalarType {
        return GraphQLScalarType.newScalar()
            .name("UUID")
            .coercing(object : Coercing<UUID, String> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String {
                    return (dataFetcherResult as UUID).toString()
                }

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): UUID {
                    return UUID.fromString(input as String)
                }

                override fun parseLiteral(input: graphql.language.Value<*>, variables: CoercedVariables, graphQLContext: GraphQLContext, locale: Locale): UUID {
                    return UUID.fromString((input as StringValue).value)
                }
            })
            .build()
    }

    private fun instantScalar(): GraphQLScalarType {
        return GraphQLScalarType.newScalar()
            .name("Instant")
            .coercing(object : Coercing<Instant, String> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String {
                    return (dataFetcherResult as Instant).toString()
                }

                override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Instant {
                    return Instant.parse(input as String)
                }

                override fun parseLiteral(input: graphql.language.Value<*>, variables: CoercedVariables, graphQLContext: GraphQLContext, locale: Locale): Instant {
                    return Instant.parse((input as StringValue).value)
                }
            })
            .build()
    }
}

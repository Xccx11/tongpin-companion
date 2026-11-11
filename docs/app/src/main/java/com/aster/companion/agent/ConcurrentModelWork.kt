package com.aster.companion.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** Runs independent model-bound work concurrently while preserving input order. */
object ConcurrentModelWork {
    suspend fun <Input, Output> map(
        inputs: List<Input>,
        block: suspend (Input) -> Output
    ): List<Output> = supervisorScope {
        inputs.map { input ->
            async(Dispatchers.IO) { block(input) }
        }.awaitAll()
    }

    suspend fun <Input, Output> mapCatching(
        inputs: List<Input>,
        block: suspend (Input) -> Output
    ): List<Result<Output>> = supervisorScope {
        inputs.map { input ->
            async(Dispatchers.IO) {
                try {
                    Result.success(block(input))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }
            }
        }.awaitAll()
    }
}

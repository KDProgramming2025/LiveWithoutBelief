/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories.menu

import info.lwb.core.common.Result
import info.lwb.core.domain.MenuRepository
import info.lwb.core.model.MenuItem
import info.lwb.data.network.MenuApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Concrete implementation of [MenuRepository] that delegates to a Retrofit-backed [MenuApi].
 *
 * Responsibilities:
 *  - Fetch the remote menu structure and map transport DTOs to domain [MenuItem] models.
 *  - Expose loading, success and error states via a cold [Flow] of [Result].
 *  - Provide a refresh hook which can later be extended to support local caching / persistence.
 *
 * Error Handling Strategy:
 *  Specific network / serialization related exceptions ([IOException], [HttpException],
 *  [SerializationException]) are caught and surfaced as [Result.Error] without swallowing stack
 *  traces. Other exceptions will propagate (fail fast) rather than being hidden behind a generic
 *  catch block, aligning with Detekt's guidance to avoid overly broad exception handling.
 */
class MenuRepositoryImpl(private val api: MenuApi) : MenuRepository {
    override fun getMenuItems(): Flow<Result<List<MenuItem>>> = flow {
        emit(Result.Loading)
        try {
            val items = api.getMenu().items.map { it.toDomain() }
            emit(Result.Success(items))
        } catch (io: IOException) {
            emit(Result.Error(io))
        } catch (http: HttpException) {
            emit(Result.Error(http))
        } catch (ser: SerializationException) {
            emit(Result.Error(ser))
        }
    }

    override suspend fun refreshMenu() = withContext(Dispatchers.IO) {
        // For now, nothing to persist; future: cache locally.
        runCatching { api.getMenu() }
        Unit
    }
}

private fun info.lwb.data.network.MenuItemDto.toDomain() = MenuItem(id, title, label, order, iconPath, createdAt)

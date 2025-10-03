/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.repositories.menu

import info.lwb.core.common.Result
import info.lwb.core.domain.MenuRepository
import info.lwb.core.model.MenuItem
import info.lwb.data.network.MenuApi
import info.lwb.data.repo.db.MenuDao
import info.lwb.data.repo.db.MenuItemEntity
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
class MenuRepositoryImpl(private val api: MenuApi, private val menuDao: MenuDao) : MenuRepository {
    override fun getMenuItems(): Flow<Result<List<MenuItem>>> = flow {
        val dbFlow = menuDao.observeMenu()
        var initial = true
        dbFlow.collect { entities ->
            if (initial) {
                initial = false
                val empty = entities.isEmpty()
                if (empty) {
                    emit(Result.Loading)
                } else {
                    emit(Result.Success(entities.map { it.toDomain() }))
                }
                // Perform remote sync inline so we can emit an error if first launch offline.
                val remoteFailure = withContext(Dispatchers.IO) {
                    runCatching {
                        fetchRemoteAndPersist()
                    }.exceptionOrNull()
                }
                if (remoteFailure != null && empty) {
                    emit(Result.Error(remoteFailure))
                }
            } else {
                emit(Result.Success(entities.map { it.toDomain() }))
            }
        }
    }

    override suspend fun refreshMenu() = withContext(Dispatchers.IO) {
        refreshInternal()
    }

    private suspend fun fetchRemoteAndPersist() {
        val response = api.getMenu()
        val ordered = response.items
            .map { it.toDomain() }
            .sortedWith(
                compareBy<MenuItem> { it.order }
                    .thenBy { it.title },
            )
        menuDao.clearAll()
        menuDao.insertAll(ordered.map { it.toEntity() })
    }

    private suspend fun refreshInternal() {
        // Called by explicit refresh. Do not emit errors here; UI retains prior state and may show snackbar.
        runCatching { fetchRemoteAndPersist() }
    }
}

// Mapping helpers

private fun MenuItemEntity.toDomain() = MenuItem(id, title, label, order, iconPath, createdAt)

private fun MenuItem.toEntity() = MenuItemEntity(id, title, label, order, iconPath, createdAt)

private fun info.lwb.data.network.MenuItemDto.toDomain() = MenuItem(id, title, label, order, iconPath, createdAt)

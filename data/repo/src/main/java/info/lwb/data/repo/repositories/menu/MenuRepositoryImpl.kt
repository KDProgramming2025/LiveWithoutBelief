/*
 * SPDX-License-Identifier: Apache-2.0
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

class MenuRepositoryImpl(
    private val api: MenuApi,
) : MenuRepository {
    override fun getMenuItems(): Flow<Result<List<MenuItem>>> = flow {
        emit(Result.Loading)
        try {
            val res = api.getMenu()
            val items = res.items.map { it.toDomain() }
            emit(Result.Success(items))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun refreshMenu() = withContext(Dispatchers.IO) {
        // For now, nothing to persist; future: cache locally.
        runCatching { api.getMenu() }
        Unit
    }
}

private fun info.lwb.data.network.MenuItemDto.toDomain() =
    MenuItem(id, title, label, order, iconPath, createdAt)

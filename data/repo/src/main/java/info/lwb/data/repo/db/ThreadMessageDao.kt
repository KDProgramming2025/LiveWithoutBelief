/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.data.repo.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Data access for discussion thread messages associated with an annotation. */
@Dao
interface ThreadMessageDao {
    /** Observe all messages in ascending chronological order for an annotation. */
    @Query("SELECT * FROM thread_messages WHERE annotationId = :annotationId ORDER BY createdAt ASC")
    fun observeMessages(annotationId: String): Flow<List<ThreadMessageEntity>>

    /** Upsert (add or replace) a message row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(message: ThreadMessageEntity)
}

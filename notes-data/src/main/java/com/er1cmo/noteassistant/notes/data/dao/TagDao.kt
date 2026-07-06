package com.er1cmo.noteassistant.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.er1cmo.noteassistant.notes.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY normalized_name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY normalized_name ASC")
    fun listTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    fun getTagById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE normalized_name = :normalizedName LIMIT 1")
    fun getTagByNormalizedName(normalizedName: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTags(tags: List<TagEntity>): List<Long>

    @Update
    fun updateTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    fun deleteTagById(id: Long): Int
}

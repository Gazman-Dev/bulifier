package com.bulifier.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SchemaDao {

    @Insert
    suspend fun addSchema(schema: Schema)

    @Insert
    suspend fun _addSchemas(schema: List<Schema>)

    @Insert
    suspend fun _addSettings(schema: List<SchemaSettings>)

    @Query("DELETE FROM schemas where project_id = :projectId")
    suspend fun deleteAllSchemas(projectId: Long)

    @Query("DELETE FROM schema_settings where project_id = :projectId")
    suspend fun deleteAllSchemasSettings(projectId: Long)

    @Transaction
    suspend fun addSchemas(schemas: List<Schema>, settings: List<SchemaSettings>) {
        val projectId = schemas.firstOrNull()?.projectId ?: return

        deleteAllSchemas(projectId)
        deleteAllSchemasSettings(projectId)
        _addSchemas(schemas)
        _addSettings(settings)
    }

    @Query("SELECT * FROM schemas where schema_name = :schemaName and project_id = :projectId order by schema_id")
    suspend fun getSchema(schemaName: String, projectId: Long): List<Schema>

    @Query("SELECT * FROM schema_settings where schema_name = :schemaName and project_id = :projectId")
    suspend fun getSettings(schemaName: String, projectId: Long): SchemaSettings?

    @Query("SELECT * FROM schema_settings where schema_name in (:schemas) and project_id = :projectId")
    suspend fun getSettings(schemas: List<String>, projectId: Long): List<SchemaSettings>

    @Query("SELECT distinct schema_name FROM schemas where project_id = :projectId order by schema_name")
    suspend fun getSchemaNames(projectId: Long): Array<String>

    @Query(
        """SELECT distinct schemas.schema_name, schema_settings.purpose 
         FROM schemas 
         join schema_settings on schemas.schema_name = schema_settings.schema_name 
         where schemas.project_id = :projectId and schema_settings.visible_for_agent and schema_settings.purpose != 'TBA'
            order by schemas.schema_name"""
    )
    suspend fun getSchemaPurposes(projectId: Long): List<SchemaPurpose>

    data class SchemaPurpose(
        @ColumnInfo(name = "schema_name")
        val schemaName: String,
        @ColumnInfo(name = "purpose")
        val purpose: String
    )
}
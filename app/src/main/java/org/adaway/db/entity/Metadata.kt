package org.adaway.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A key value record for small pieces of state that must commit atomically with the data they
 * describe.
 */
@Entity(tableName = "metadata")
class Metadata {
    @PrimaryKey
    @ColumnInfo(name = "key")
    var key: String = ""

    @ColumnInfo(name = "value")
    var value: String = ""
}

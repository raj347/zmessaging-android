/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.model

import android.database.Cursor
import com.waz.db.Col._
import com.waz.db.Dao

case class KeyValueData(key: String, value: String)

object KeyValueData {

  implicit object KeyValueDataDao extends Dao[KeyValueData, String] {
    val Key = text('key, "PRIMARY KEY")(_.key)
    val Value = text('value)(_.value)

    override val idCol = Key
    override val table = Table("KeyValues", Key, Value)
    override def apply(implicit cursor: Cursor): KeyValueData = KeyValueData(Key, Value)
  }
}

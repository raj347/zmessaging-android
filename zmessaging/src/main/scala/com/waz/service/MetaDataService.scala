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
package com.waz.service

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.waz.ZLog._
import com.waz.api.OtrClientType
import com.waz.sync.client._
import com.waz.utils.{LoggedTry, returning}

import scala.util.Try

class MetaDataService(context: Context) {
  private implicit val logTag: LogTag = logTagFor[MetaDataService]

  lazy val metaData = LoggedTry {
    import scala.collection.JavaConverters._

    val ai = context.getPackageManager.getApplicationInfo(context.getPackageName, PackageManager.GET_META_DATA)
    returning(Option(ai.metaData).getOrElse(new Bundle)) { meta =>
      verbose(s"meta keys: ${meta.keySet().asScala.toSeq}")
    }
  } .getOrElse(new Bundle)

  lazy val appVersion = Try(context.getPackageManager.getPackageInfo(context.getPackageName, 0).versionCode).getOrElse(0)

  lazy val localyticsSenderId = Option(metaData.getString("localytics.gcm"))

  lazy val spotifyClientId = Option(metaData.getString("spotify.api.id")).fold(MetaDataService.DefaultSpotifyClientId)(SpotifyClientId)

  lazy val internalBuild = metaData.getBoolean("INTERNAL", false)

  lazy val audioLinkEnabled = metaData.getBoolean("AUDIOLINK_ENABLED", false)

  // rough check for device type, used in otr client info
  lazy val deviceClass = {
    val dm = context.getResources.getDisplayMetrics
    val minSize = 600 * dm.density
    if (dm.heightPixels >= minSize && dm.widthPixels >= minSize) OtrClientType.TABLET else OtrClientType.PHONE
  }

  lazy val deviceModel = {
    import android.os.Build._
    s"$MANUFACTURER $MODEL"
  }

  lazy val localBluetoothName =
    Try(Option(BluetoothAdapter.getDefaultAdapter.getName).getOrElse("")).getOrElse("")

  val cryptoBoxDirName = "otr"
}

object MetaDataService {
  val DefaultSpotifyClientId = SpotifyClientId("f85bc93df1ff4c3781a7bcbf524c1f5b")
}

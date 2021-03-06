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
package com.waz.sync.handler

import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.content.ConversationStorage
import com.waz.model.GenericContent.LastRead
import com.waz.model._
import com.waz.service.MetaDataService
import com.waz.sync.SyncResult
import com.waz.sync.otr.OtrSyncHandler
import org.threeten.bp.Instant

import scala.concurrent.Future

class LastReadSyncHandler(selfUserId: UserId, convs: ConversationStorage, metadata: MetaDataService, convSync: ConversationsSyncHandler, msgsSync: MessagesSyncHandler, otrSync: OtrSyncHandler) {
  import com.waz.threading.Threading.Implicits.Background
  private implicit val tag: LogTag = logTagFor[LastReadSyncHandler]

  def postLastRead(convId: ConvId, time: Instant): Future[SyncResult] = {
    verbose(s"postLastRead($convId, $time)")

    convs.get(convId) flatMap {
      case Some(conv) if conv.lastRead.isAfter(time) => // no need to send this msg as lastRead was already advanced
        Future successful SyncResult.Success
      case Some(conv) =>
        val msg = GenericMessage(Uid(), LastRead(conv.remoteId, time))
        otrSync.postOtrMessage(ConvId(selfUserId.str), RConvId(selfUserId.str), msg) map (_.fold(SyncResult(_), { _ => SyncResult.Success }))
      case None =>
        Future successful SyncResult(ErrorResponse.internalError(s"No conversation found for id: $convId"))
    }
  }
}

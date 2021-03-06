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
package com.waz.znet

import com.waz.api.impl.ErrorResponse
import com.waz.znet.ResponseConsumer.{FileConsumer, ByteArrayConsumer, JsonConsumer, StringConsumer}
import com.waz.cache.CacheService
import com.koushikdutta.async.http.{Headers => KoushHeaders}

case class Response(
  status: Response.Status,
  body: ResponseContent = EmptyResponse,
  headers: Response.Headers = Response.EmptyHeaders
)

object Response {

  object Status {
    val Success = 200
    val Created = 201
    val NoResponse = 204
    val MovedTemporarily = 302
    val SeeOther = 303
    val BadRequest = 400
    val Unauthorized = 401
    val Forbidden = 403
    val NotFound = 404
    val RateLimiting = 420
    val LoginRateLimiting = 429
    val Conflict = 409
    val PreconditionFailed = 412

    def isFatal(status: Int) = status != Unauthorized && status != RateLimiting && status / 100 == 4
  }

  object ErrorStatus {
    def unapply(status: Status) = !status.isSuccess
  }
  object ServerErrorStatus {
    def unapply(status: Status) = status match {
      case HttpStatus(s, _) => s / 100 == 5
      case _ => false
    }
  }
  object ClientErrorStatus {
    def unapply(status: Status) = status match {
      case HttpStatus(s, _) => s / 100 == 4
      case _ => false
    }
  }
  object SuccessStatus {
    def unapply(status: Status) = status.isSuccess
  }
  object SuccessHttpStatus {
    def unapply(status: Status) = status match {
      case s: HttpStatus => s.isSuccess
      case _ => false
    }
  }


  sealed trait Status {
    def isSuccess: Boolean
    def msg: String
    val status: Int

    def isFatal = Status.isFatal(status)
  }

  case class HttpStatus(status: Int, msg: String = "") extends Status {
    override def isSuccess: Boolean = status / 100 == 2
  }

  /**
   * Response status indicating some internal exception happening during request or response processing.
   * This should generally indicate a bug in our code and we don't know if the request was processed by server.
   */
  case class InternalError(msg: String, cause: Option[Throwable] = None, httpStatus: Option[HttpStatus] = None) extends Status {
    override val isSuccess: Boolean = false
    override val status: Int = ErrorResponse.InternalErrorCode
  }

  /**
   * Response status indicating internet connection problem.
   */
  case class ConnectionError(msg: String) extends Status {
    override val isSuccess: Boolean = false
    override val status: Int = ErrorResponse.ConnectionErrorCode
  }

  case object Cancelled extends Status {
    override val msg = "Cancelled by user"
    override val isSuccess = false
    override val status: Int = ErrorResponse.CancelledCode
  }

  case object ClientClosed extends Status {
    override val msg = "ZNetClient has been closed"
    override val isSuccess = false
    override val status: Int = 603
  }


  trait ResponseBodyDecoder {
    def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent]
  }

  object DefaultResponseBodyDecoder extends ResponseBodyDecoder {
    val TextContent = "text/.*".r
    val JsonContent = "application/json.*".r
    val ImageContent = "image/.*".r

    override def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent] =
      contentType match {
        case JsonContent() => new JsonConsumer(contentLength)
        case TextContent() => new StringConsumer(contentLength)
        case _ => new ByteArrayConsumer(contentLength, contentType)
      }
  }

  def CacheResponseBodyDecoder(cache: CacheService) = new ResponseBodyDecoder {
    import DefaultResponseBodyDecoder._
    val InMemoryThreshold = 24 * 1024

    override def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent] =
      contentType match {
        case JsonContent() => new JsonConsumer(contentLength)
        case TextContent() => new StringConsumer(contentLength)
        case _ if contentLength > InMemoryThreshold => new FileConsumer(contentType)(cache)
        case _ => new ByteArrayConsumer(contentLength, contentType)
      }
  }

  val EmptyHeaders = new Headers

  class Headers(headers: KoushHeaders = new KoushHeaders()) {
    import scala.collection.JavaConverters._
    lazy val map = headers.getMultiMap

    def apply(key: String): Option[String] = Option(headers.get(key))

    def foreach(key: String)(f: String => Unit): Unit = Option(headers.getAll(key)) foreach { _.iterator().asScala foreach f }

    override def toString: String = s"Headers[${map.entrySet().asScala map { e => e.getKey -> e.getValue.asScala }}]"
  }

  object Headers {
    def apply(entries: (String, String)*) = {
      val headers = new KoushHeaders()
      entries foreach { case (key, value) => headers.add(key, value) }
      new Headers(headers)
    }
  }
}

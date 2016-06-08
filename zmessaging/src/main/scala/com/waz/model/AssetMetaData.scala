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

import java.io.{File, FileInputStream, InputStream}

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever._
import android.net.Uri
import com.waz.ZLog.LogTag
import com.waz.service.assets.MetaDataRetriever
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp
import org.threeten.bp.Duration

import scala.util.Try
import com.waz.utils._

sealed abstract class AssetMetaData(val jsonTypeTag: Symbol)
object AssetMetaData {

  implicit lazy val AssetMetaDataEncoder: JsonEncoder[AssetMetaData] = new JsonEncoder[AssetMetaData] {
    override def apply(data: AssetMetaData): JSONObject = JsonEncoder { o =>
      o.put("type", data.jsonTypeTag.name)
      data match {
        case Empty => // nothing to add
        case Video(dimensions, duration) =>
          o.put("dimensions", JsonEncoder.encode(dimensions))
          o.put("duration", duration.toMillis)
        case Audio(duration, loudness) =>
          o.put("duration", duration.toMillis)
          o.put("loudness", JsonEncoder.arrNum(loudness))
        case Image(dimensions, tag) =>
          o.put("dimensions", JsonEncoder.encode(dimensions))
          tag.foreach(o.put("tag", _))
      }
    }
  }

  implicit lazy val AssetMetaDataDecoder: JsonDecoder[AssetMetaData] = new JsonDecoder[AssetMetaData] {
    import JsonDecoder._

    override def apply(implicit o: JSONObject): AssetMetaData = decodeSymbol('type) match {
      case 'empty => Empty
      case 'video =>
        Video(opt[Dim2]('dimensions).getOrElse(Dim2(0, 0)), Duration.ofMillis('duration))
      case 'audio =>
        Audio(Duration.ofMillis('duration), decodeFloatSeq('loudness))
      case 'image =>
        Image(JsonDecoder[Dim2]('dimensions), decodeOptString('tag))
      case other =>
        throw new IllegalArgumentException(s"unsupported meta data type: $other")
    }
  }

  trait HasDuration {
    val duration: Duration
  }
  object HasDuration {
    def unapply(meta: HasDuration): Option[Duration] = Some(meta.duration)
  }

  trait HasDimensions {
    val dimensions: Dim2
  }
  object HasDimensions {
    def unapply(meta: HasDimensions): Option[Dim2] = Some(meta.dimensions)
  }

  case class Video(dimensions: Dim2, duration: Duration) extends AssetMetaData('video) with HasDimensions with HasDuration
  case class Image(dimensions: Dim2, tag: Option[String]) extends AssetMetaData('image) with HasDimensions
  case class Audio(duration: Duration, loudness: Seq[Float]) extends AssetMetaData('audio) with HasDuration
  case object Empty extends AssetMetaData('empty)

  object Video {

    def apply(file: File): Either[String, Video] = MetaDataRetriever(file)(apply(_))

    def apply(context: Context, uri: Uri): Either[String, Video] = MetaDataRetriever(context, uri)(apply(_))

    def apply(retriever: MediaMetadataRetriever): Either[String, Video] = {
      def retrieve[A](k: Int, tag: String, convert: String => A) =
        Option(retriever.extractMetadata(k)).toRight(s"$tag ($k) is null")
          .flatMap(s => Try(convert(s)).toRight(t => s"unable to convert $tag ($k) of value '$s': ${t.getMessage}"))

      for {
        width    <- retrieve(METADATA_KEY_VIDEO_WIDTH, "video width", _.toInt)
        height   <- retrieve(METADATA_KEY_VIDEO_HEIGHT, "video height", _.toInt)
        rotation <- retrieve(METADATA_KEY_VIDEO_ROTATION, "video rotation", _.toInt)
        dim       = if (rotation / 90 % 2 == 0) Dim2(width, height) else Dim2(height, width)
        duration <- retrieve(METADATA_KEY_DURATION, "duration", s => bp.Duration.ofMillis(s.toLong))
      } yield AssetMetaData.Video(dim, duration)
    }
  }

  object Audio {
    private implicit val Tag: LogTag = "AssetMetaData.Audio"

    def apply(file: File): Option[Audio] = MetaDataRetriever(file)(apply(_))

    def apply(context: Context, uri: Uri): Option[Audio] = MetaDataRetriever(context, uri)(apply(_))

    def apply(retriever: MediaMetadataRetriever): Option[Audio] = for {
      duration <- Option(retriever.extractMetadata(METADATA_KEY_DURATION))
      millis <- LoggedTry(bp.Duration.ofMillis(duration.toLong)).toOption
    } yield Audio(millis, Seq.empty) // TODO: extract loudness info
  }

  object Image {
    val Empty = Image(Dim2.Empty, None)

    def apply(file: File): Option[Image] = apply(new FileInputStream(file))

    def apply(context: Context, uri: Uri): Option[Image] = apply(context.getContentResolver.openInputStream(uri))

    def apply(stream: => InputStream): Option[Image] = Managed(stream).acquire { is =>
      val opts = new BitmapFactory.Options
      opts.inJustDecodeBounds = true
      BitmapFactory.decodeStream(is, null, opts)
      if (opts.outWidth == 0 && opts.outHeight == 0) None
      else Some(Image(Dim2(opts.outWidth, opts.outHeight), None))
    }
  }
}
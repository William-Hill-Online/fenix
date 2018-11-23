package com.williamhill.fenix.server.es.trx

import com.williamhill.fenix.server.messages.FenixMessage
import com.williamhill.fenix.server.messages._
import com.williamhill.rt.chronos.api.Incident
import play.api.libs.json.JsValue

import scala.util.Try

class IncidentTrx() extends (Incident => Try[List[FenixMessage]]) {

  override def apply(incident: Incident): Try[List[FenixMessage]] = incident match {
    case Incident("pds", "1.1", _, _, _, _, _) => PdsIncident(incident)
    case _ => UnknownIncident(incident)
  }

}

object PdsIncident extends (Incident => Try[List[FenixMessage]]) {

  val evFields = Set("order", "displayed", "isInPlay", "status", "settled", "startDateTime", "active")
  val mkFields = Set("order", "displayed", "name", "status", "handicap", "marketGroup", "blurb", "sort", "eachWay", "eachWayPlaces", "eachWayFactorNum", "eachWayFactorDen", "collectionId", "hasInPlay", "active", "settled", "channels", "cashoutAvailable")
  val ouFields = Set("order", "displayed", "status", "currentPriceNum", "currentPriceDen", "cashoutPriceNum", "cashoutPriceDen", "name", "scoreHome", "scoreAway", "fbResult", "settled")

  def apply(incident: Incident): Try[List[FenixMessage]] = {
    val pdsLevel = incident.metadata getOrElse ("pdsLevel", "0") toInt
    val pdsType = incident.metadata getOrElse ("pdsType", "0") toInt
    val data = incident.payload.as[Map[String, JsValue]]
    val entityId = pdsTopic(data("id").as[String])

    pdsType match {
      case 1 => handleInsert(pdsLevel, entityId, data)
      case 2 => handleUpdate(pdsLevel, incident.metadata getOrElse ("pdsUpdatedFields", "") split "," toSet, entityId, data)
      case 3 => handleDelete(pdsLevel, entityId, data)
      // Other cases
      case _ => UnknownIncident(incident)
    }
  }

  private def handleInsert(lvl: Int, id: String, data: Map[String, JsValue]): Try[List[FenixMessage]] = lvl match {
    case 4 => Try(List(FenixCreateEntityCmd(id, data filterKeys evFields)))
    case 5 => Try(List(
      FenixCreateEntityCmd(id, data filterKeys mkFields),
      FenixAddCmd(pdsTopic(getParentAncestor(data) + "_links"), "mk", Seq(id))
    ))
    case 6 => Try(List(
      FenixCreateEntityCmd(id, data filterKeys ouFields),
      FenixAddCmd(pdsTopic(getParentAncestor(data) + "_links"), "ou", Seq(id))
    ))
    case any => Try(List())
  }

  private def handleUpdate(lvl: Int, changedFields: Set[String], id: String, data: Map[String, JsValue]): Try[List[FenixMessage]] = {
    val relevantFields = lvl match {
      case 4 => evFields
      case 5 => mkFields
      case 6 => ouFields
      case any => Set[String]()
    }
    val fieldsToUpdate = relevantFields intersect changedFields
    if (fieldsToUpdate.nonEmpty)
      Try(List(FenixMergeEntityCmd(id, data filterKeys relevantFields, preserve = true)))
    else
      Try(List())
  }

  private def handleDelete(lvl: Int, id: String, data: Map[String, JsValue]): Try[List[FenixMessage]] = lvl match {
    case 4 => Try(List(FenixClearCmd(id)))
    case 5 => Try(List(
      FenixClearCmd(id),
      FenixDelCmd(pdsTopic(getParentAncestor(data) + "_links"), "markets", Seq(id))
    ))
    case 6 => Try(List(
      FenixClearCmd(id),
      FenixDelCmd(pdsTopic(getParentAncestor(data) + "_links"), "outcomes", Seq(id))
    ))
    case any => Try(List())
  }

  private def getParentAncestor(data: Map[String, JsValue]): String =
    data.get("ancestors").get.as[Seq[String]].head

  private def pdsTopic(topic: String): String =
    "pds/" + topic
}

object UnknownIncident extends (Incident => Try[List[FenixMessage]]) {
  def apply(incident: Incident): Try[List[FenixMessage]] = {
    Try(List.empty[FenixMessage])
  }
}
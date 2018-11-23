package com.williamhill.fenix.server.channel

class SubscriptionsMap(subscriptionMap: Map[String, Map[String, Set[String]]]) {

  def withSubscription(topic: String, client: (String, String)): SubscriptionsMap = new SubscriptionsMap(
    subscriptionMap.getOrElse(topic, Map.empty[String, Set[String]]) match {
      case actual_routers: Map[String, Set[String]] => actual_routers.getOrElse(client._1, Set.empty[String]) match {
        case actual_sessions: Set[String] =>
          val sessions = actual_sessions + client._2
          val routers = actual_routers + (client._1 -> sessions)
          subscriptionMap + (topic -> routers)
      }
    }
  )

  def withoutSubscription(topic: String, client: (String, String)): SubscriptionsMap = new SubscriptionsMap(
    subscriptionMap.getOrElse(topic, Map.empty[String, Set[String]]) match {
      case actual_routers: Map[String, Set[String]] => actual_routers.getOrElse(client._1, Set.empty[String]) match {
        case actual_sessions: Set[String] =>
          val sessions = actual_sessions - client._2
          val routers = if (sessions.isEmpty) actual_routers - client._1 else actual_routers + (client._1 -> sessions)
          if (routers.isEmpty) subscriptionMap - topic else subscriptionMap + (topic -> routers)
      }
    }
  )

  def isSubscribed(topic: String, client: (String, String)): Boolean = subscriptionMap.get(topic) match {
    case Some(c) => c.get(client._1) match {
      case Some(s) => s.contains(client._2)
      case None => false
    }
    case None => false
  }

  def size(): Int = subscriptionMap.size

  def topics(): List[String] = subscriptionMap.keySet.toList

  def getSubscriberGroups(topic: String): List[String] = getSubscribersByGroup(topic).keySet.toList

  def getSubscribers(topic: String): List[(String, String)] = getSubscribersByGroup(topic).toList.flatMap(x => x._2.map(y => (x._1, y)))

  def getSubscribersByGroup(topic: String): Map[String, Set[String]] = subscriptionMap.getOrElse(topic, Map.empty[String, Set[String]])

  override def toString() = subscriptionMap.toString
}

object SubscriptionsMap {
  def apply(initial: Map[String, Map[String, Set[String]]] = Map.empty[String, Map[String, Set[String]]]) = new SubscriptionsMap(initial)
}

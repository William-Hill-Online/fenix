package com.williamhill.fenix.server.util

object MapUtil {

  /**
   * Calculate a delta between two maps
   *
   * @param data     Initial map
   * @param input    Target map
   * @param preserve Whether to preserve or not keys on the initial map that are no longer present in the target map
   * @return Tuple which first element is the created/updated keys and the second element is the list of keys to remove (in case preserve == false)
   */
  def calculateDelta(data: Map[String, Any], input: Map[String, Any], preserve: Boolean): MapDelta =
    MapDelta(
      input.filter { case (key, value) => !data.get(key).contains(value) },
      if (preserve) List() else data.keySet.diff(input.keySet).toList
    )

  case class MapDelta(updates: Map[String, Any], deletes: List[String])

}

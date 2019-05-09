Fenix
======

Introduction
------------
Fenix is distributed cache. However it's' universal the main ambition is to provide high scalable replacement for Diffusion. 
Fenix currently support low-level TCP interface and the same functionality will be exposed by Websocket interaface.

TCP Protocol
------------
Each frame transmitted is followed by the TCP terminal character that is configured in the config file: fenix.conf under the property 'fenix.tcp.protocol.terminal', by default is the charachter '\n' ('\u000D').
Each frame is encoded as a JSON array where the first element is always a Number and represents the type of Operation.

|Operation Code|Operation Name|Descriptionn|Arguments|Example|
|--------------|--------------|------------|---------|-------|
| 1   | FenixCreateEntityCmd | Create a new Key/Value entity into the cache | entityId: String, data: Map\[String, Any\] |\[1,"OB_EV54952","{"eventId":"OB_EV54952","homeScore":"0"}"\] |
| 2   | FenixUpdateCmd | Update a specific field of an entity | entityId: String, field: String, value: Any | \[2,"OB_EV54952","homeScore","1"\]   |
| 3   | FenixRemoveCmd | Remove a Key/Value entity from the cache | entityId: String | \[3,"OB_EV54952"\] |
| 4   | FenixAddCmd    | Add a value to a List field of an entity. If the field is not a list after this operation it is automatically converted into a Seq\[Any\] | entityId: String, field: String, values: Set\[Any\] | \[4,"OB_EV54952","homePlayers",\["Edgar Davids","Alessandro Del Piero","Luis Figo"\]\] |
| 5   | FenixDelCmd    | Remove a value from a List field of the specified entity | entityId: String, field: String, values: Set\[Any\] | \[5,"OB_EV54952","homePlayers",\["Alessandro Del Piero"\]\] |
| 6   | FenixClearCmd  | Clear the Value of a specific entity. Differently from FenixRemoveCmd, this operation set the value of the entity to an empty map but it doesn't remove the entity | entityId: String | \[6,"OB_EV54952"\] |
| 7   | FenixMergeEntityCmd  | Merges the actual entity with the provided Key/Value map. Whether to preserve or not former keys is optional. | entityId: String, data: Map\[String, Any\], preserve: Boolean | \[7,"OB_EV54952",{"eventId":"OB_EV54952","homeScore":"2"}, false\] |
| 10  | FenixGetEntityReq  | Given the entityId returns the value associated to that entityId | entityId: String | \[10,"OB_EV54952"\] |
| 11  | FenixGetEntityResp | Is the response message for request operation code 10 | offset: Long, entityId: String, data: Map\[String, Any\] | [11,1001,"OB_EV54952","{"eventId":"OB_EV54952","homeScore":"2"}"\] |
| 12  | FenixGetEntityFieldReq | Request the value of a specific field of an entity | entityId: String, field: String | \[12,"OB_EV54952","homeScore"\] |
| 13  | FenixGetEntityFieldResp | Response message for oepration code 12 | offset: Long, entityId: String, field: String, value: Any | \[13,1002,"OB_EV54952","homeScore","2"\] |
| 20  | FenixTopicSubscribe | Subscribes for event associated to a specific topic. A topic can be an entityId or a field of an entityId (entityId/field) | topic: String | \[20,"OB_EV54952"\] or \[20,"OB_EV54952/homeScore"\] |
| 21  | FenixTopicSubscribeAck |Ackowledge message associated to a subscribe request op. code 20 | topic: String | \[21,"OB_EV54952"\] or \[21,"OB_EV54952/homeScore"\] |
| 22  | FenixTopicUnsubscribe | Remove the subscription from a topic | topic: String | \[22,"OB_EV54952"\] or \[22,"OB_EV54952/homeScore"\] |
| 23  | FenixTopicUnsubscribeAck | Ackowledge message associated to a unsubscribe request op. code 21 | topic: String | \[23,"OB_EV54952"\] or \[23,"OB_EV54952/homeScore"\] |
| 101 | FenixCreateEntityEvent | Notification event associated to command op. code 1 | offset: Long, entityId: String, data: Map\[String, Any\] |\[101,1002,"OB_EV54952","{"eventId":"OB_EV54952","homeScore":"2"}"\] |
| 102 | FenixUpdateEvent | Notification event associated to command op. code 2 | offset: Long, entityId: String, field: String, value: Any | \[102,1003,"OB_EV54952","homeScore","3"\]   |
| 103 | FenixRemoveEvent | Notification event associated to command op. code 3 | offset: Long, entityId: String | \[103,1004,"OB_EV54952"\] |
| 104 | FenixAddEvent    | Notification event associated to command op. code 4 | offset: Long, entityId: String, field: String, values: Set\[Any\] | \[104,1005,"OB_EV54952","homePlayers",\["Edgar Davids","Alessandro Del Piero","Luis Figo"\]\] |
| 105 | FenixDelEvent    | Notification event associated to command op. code 5 | offset: Long, entityId: String, field: String, values: Set\[Any\] | \[105,1006,"OB_EV54952","homePlayers",\["Luis Figo"\]\] |
| 106 | FenixClearEvent  | Notification event associated to command op. code 6 | offset: Long, entityId: String | \[106,1007,"OB_EV54952"\] |
| 107 | FenixMergeEntityEvent  | Notification event associated to command op. code 7 containing the keys to be updated with their values and (optionally) the keys to be deleted| offset: Long, entityId: String, updates: Map\[String, Any\], deletes: List\[String\] | \[107,1007,"OB_EV54952",{"homeScore":"3"},\["homePlayers"\]\] |

Examples
--------

## Connect to Fenix (Using telnet)
```scala
$>telnet $FENIX_HOST $FENIX_TCP_PORT
```

## Create an entity
Entity name: "OB_EV54952"
Entity field: "homePlayers", value: "0711DJR"
```json
[1,"OB_EV54952",{"homeScore":"0"}]
```

## Fetch an entity
```json
[10,"OB_EV54952"]
[11,0,"OB_EV54952",{"homeScore":"0"}] //--> RESPONSE
```

## Subscribe to updates for OB_EV54952
```json
[20,"OB_EV54952"]
[21,"OB_EV54952"]
```

## Add elements to normal field (Transform it into array field)
```json
[4,"OB_EV54952","homePlayers",["Luis Figo","Alessandro Del Piero","Zlatan Ibrahimović"]]
[104,1,"OB_EV54952","homePlayers",["Luis Figo","Alessandro Del Piero","Zlatan Ibrahimović"]] //--> NOTIFICATION AFTER SUBSCRIPTION TO OB_EV54952
```

## Remove elements from array field
```json
[5,"OB_EV54952","homePlayers",["Luis Figo"]]
[105,2,"OB_EV54952","homePlayers",["Luis Figo"]] //--> NOTIFICATION AFTER SUBSCRIPTION TO OB_EV54952
[10,"OB_EV54952"] //<-- FETCH REQUEST
[11,2,"OB_EV54952",{"homePlayers":["Zlatan Ibrahimović","Alessandro Del Piero"]}] //--> FETCH RESPONSE
```

## Delete a field
```json
[2,"OB_EV54952","homeScore","0"] //<-- SET A NEW FIELD FOR ENTITY OB_EV54952
[102,3,"OB_EV54952","homeScore","0"] //--> EVENT NOTIFICATION
[10,"OB_EV54952"] //<-- FETCH REQUEST
[11,3,"OB_EV54952",{"homePlayers":["Zlatan Ibrahimović","Alessandro Del Piero"],"homeScore":"0"}] //--> FETCH RESPONSE
[3,"OB_EV54952","homeScore"] //<-- REMOVE FIELD COMMAND
[103,4,"OB_EV54952","homeScore"] //--> EVENT NOTIFICATION
[10,"OB_EV54952"] //<-- FETCH REQUEST
[11,4,"OB_EV54952",{"homePlayers":["Zlatan Ibrahimović","Alessandro Del Piero"]}] //--> FETCH RESPONSE
```

## Merge with a key/value map (preserving existing keys not present in the key/value map)
```json
[7,"OB_EV54952",{"homeScore":"0","awayScore":"1"},true] //<-- MERGE
[107,5,"OB_EV54952",{"homeScore":"0","awayScore":"1"}] //--> EVENT NOTIFICATION
[10,"OB_EV54952"] //<-- FETCH REQUEST
[11,5,"OB_EV54952",{"homePlayers":["Zlatan Ibrahimović","Alessandro Del Piero"],"homeScore":"0","awayScore":"1"}] //<-- FETCH RESPONSE
```

## Merge with a snapshot (deleting existing keys not present in the snapshot)
```json
[7,"OB_EV54952",{"homeScore":"0","awayScore":"1"}] //<-- OVERRIDE WITH SNAPSHOT
[107,6,"OB_EV54952",{"awayScore":"1"},["homePlayers"]] //--> EVENT NOTIFICATION
[10,"OB_EV54952"] //<-- FETCH REQUEST
[11,6,"OB_EV54952",{"homeScore":"0","awayScore":"1"}] //<-- FETCH RESPONSE
```

## Clear an entity
```json
[6,"OB_EV54952"] //<-- CLEAR ENTITY OB_EV54952 REQUEST
[106,7,"OB_EV54952"] //--> EVENT NOTIFICATION
[10,"OB_EV54952"] //<-- FETCH REQUEST
[11,7,"OB_EV54952",{}] //--> FETCH RESPONSE
```

Docker
------
In order to create a docker container simply run:
```bash
$>sbt docker:publishLocal
```
This will create:
```bash
REPOSITORY                            TAG                 IMAGE ID            CREATED             VIRTUAL SIZE
fenix                           1.0.0-SNAPSHOT      b0cc1dee032d        3 seconds ago       707.3 MB
```

The container can be started as simple as:
```bash
$>docker run --name=fenix fenix:1.0.0-SNAPSHOT
[INFO] [11/14/2015 17:06:40.341] [main] [akka.remote.Remoting] Starting remoting
[INFO] [11/14/2015 17:06:40.515] [main] [akka.remote.Remoting] Remoting started; listening on addresses :[akka.tcp://fenix@127.0.0.1:2551]
[INFO] [11/14/2015 17:06:40.535] [main] [akka.cluster.Cluster(akka://fenix)] Cluster Node [akka.tcp://fenix@127.0.0.1:2551] - Starting up...
[INFO] [11/14/2015 17:06:40.646] [main] [akka.cluster.Cluster(akka://fenix)] Cluster Node [akka.tcp://fenix@127.0.0.1:2551] - Registered cluster JMX MBean [akka:type=Cluster]
[INFO] [11/14/2015 17:06:40.648] [main] [akka.cluster.Cluster(akka://fenix)] Cluster Node [akka.tcp://fenix@127.0.0.1:2551] - Started up successfully
[INFO] [11/14/2015 17:06:40.734] [akkaCluster-akka.actor.default-dispatcher-16] [akka.cluster.Cluster(akka://fenix)] Cluster Node [akka.tcp://fenix@127.0.0.1:2551] - Node [akka.tcp://fenix@127.0.0.1:2551] is JOINING, roles []
2015-11-14 17:06:40 INFO  Main$:23 - fenix starting
[INFO] [11/14/2015 17:06:41.719] [akkaCluster-akka.actor.default-dispatcher-2] [akka.cluster.Cluster(akka://fenix)] Cluster Node [akka.tcp://fenix@127.0.0.1:2551] - Leader is moving node [akka.tcp://akkaCluster@127.0.0.1:2551] to [Up]
```

## Docker Environment Variables

* FENIX_HOST    - host to use for binding the remote TCP cluster communication
* FENIX_PORT    - port to use for binding the remote TCP cluster communication
* FENIX_SERVERS - list of seed nodes to be contacted to join the cluster in the format <HOST>:<PORT>
* MIN_MEMBERS  - minimum number of cluster nodes to contact before to start the execution

## Packaging with

* sbt universal:packageZipTarball (see http://www.scala-sbt.org/sbt-native-packager/formats/universal.html)

package com.williamhill.fenix.server.channel.messages

import com.williamhill.fenix.server.channel.SubscriptionsMap

trait ChannelMessage {
}

/**
 * Channel Subscription Management
 */

case class ChannelSubscribe(entityId: String, clientId: (String, String)) extends ChannelMessage

case class ChannelUnsubscribe(entityId: String, clientId: (String, String)) extends ChannelMessage

case class ChannelSubscriptionMapReq() extends ChannelMessage

case class ChannelSubscriptionMapResp(subscriptions: SubscriptionsMap) extends ChannelMessage

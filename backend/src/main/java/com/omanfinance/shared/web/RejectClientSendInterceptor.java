package com.omanfinance.shared.web;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

/**
 * Rejects every {@code SEND} frame from a STOMP client, so Spring answers it with an {@code ERROR}
 * frame. In {@code stomp_contract.yaml} clients only {@code SUBSCRIBE}.
 *
 * <p>Without it, the simple broker forwards a client {@code SEND} to {@code /topic/**} to every
 * subscriber, so any browser could push a fake market status or fake prices to all the others. A
 * {@code SEND} to {@code /app/**} would be dropped without telling the client.
 *
 * <p>The backend publishes through {@code SimpMessagingTemplate}, which does not pass this
 * interceptor.
 */
class RejectClientSendInterceptor implements ChannelInterceptor {

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
      throw new MessageDeliveryException(
          message,
          "Clients can only SUBSCRIBE; SEND to " + accessor.getDestination() + " is not allowed");
    }
    return message;
  }
}

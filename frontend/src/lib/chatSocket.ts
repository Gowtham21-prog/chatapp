import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { MessageResponse, ReadReceiptEvent, TypingEvent, PresenceUpdateMessage, NotificationResponse } from "@/types";

const WS_URL = import.meta.env.VITE_WS_URL ?? "http://localhost:8080/ws";

type MessageHandler = (message: MessageResponse) => void;
type TypingHandler = (event: TypingEvent) => void;
type ReceiptHandler = (event: ReadReceiptEvent) => void;
type PresenceHandler = (event: PresenceUpdateMessage) => void;
type NotificationHandler = (event: NotificationResponse) => void;
type ErrorHandler = (message: string) => void;
type ConnectionHandler = (connected: boolean) => void;

/**
 * Thin wrapper around @stomp/stompjs configured for this backend's
 * conventions (Authorization header on CONNECT, /user/queue/* private
 * destinations, /topic/presence/{userId} for presence). Kept as a plain
 * class rather than baked into a React hook so it can be instantiated once
 * per login session and reused across every component that needs it,
 * instead of reconnecting per-component-mount.
 */
export class ChatSocket {
  private client: Client;
  private messageHandlers = new Set<MessageHandler>();
  private typingHandlers = new Set<TypingHandler>();
  private deliveryReceiptHandlers = new Set<ReceiptHandler>();
  private readReceiptHandlers = new Set<ReceiptHandler>();
  private notificationHandlers = new Set<NotificationHandler>();
  private errorHandlers = new Set<ErrorHandler>();
  private connectionHandlers = new Set<ConnectionHandler>();
  private presenceSubscriptions = new Map<string, StompSubscription>();
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null;

  constructor(accessToken: string) {
    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.client.onConnect = () => {
      this.connectionHandlers.forEach((h) => h(true));
      this.subscribePrivateQueues();
      this.startHeartbeat();
    };
    this.client.onDisconnect = () => {
      this.connectionHandlers.forEach((h) => h(false));
      this.stopHeartbeat();
    };
    this.client.onStompError = (frame) => {
      this.errorHandlers.forEach((h) => h(frame.headers["message"] ?? "WebSocket error"));
    };
  }

  connect() {
    this.client.activate();
  }

  disconnect() {
    this.stopHeartbeat();
    this.client.deactivate();
  }

  private subscribePrivateQueues() {
    this.client.subscribe("/user/queue/messages", (frame: IMessage) => {
      const message = JSON.parse(frame.body) as MessageResponse;
      this.messageHandlers.forEach((h) => h(message));
    });
    this.client.subscribe("/user/queue/typing", (frame: IMessage) => {
      const event = JSON.parse(frame.body) as TypingEvent;
      this.typingHandlers.forEach((h) => h(event));
    });
    this.client.subscribe("/user/queue/delivery-receipts", (frame: IMessage) => {
      const event = JSON.parse(frame.body) as ReadReceiptEvent;
      this.deliveryReceiptHandlers.forEach((h) => h(event));
    });
    this.client.subscribe("/user/queue/read-receipts", (frame: IMessage) => {
      const event = JSON.parse(frame.body) as ReadReceiptEvent;
      this.readReceiptHandlers.forEach((h) => h(event));
    });
    this.client.subscribe("/user/queue/errors", (frame: IMessage) => {
      const body = JSON.parse(frame.body) as { message: string };
      this.errorHandlers.forEach((h) => h(body.message));
    });
    this.client.subscribe("/user/queue/notifications", (frame: IMessage) => {
      const notification = JSON.parse(frame.body) as NotificationResponse;
      this.notificationHandlers.forEach((h) => h(notification));
    });
  }

  private startHeartbeat() {
    const send = () => {
      if (this.client.connected) {
        this.client.publish({ destination: "/app/presence.heartbeat" });
      }
    };
    send();
    this.heartbeatInterval = setInterval(send, 30000);
  }

  private stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }

  subscribePresence(userId: string, handler: PresenceHandler): () => void {
    const sub = this.client.subscribe(`/topic/presence/${userId}`, (frame: IMessage) => {
      handler(JSON.parse(frame.body) as PresenceUpdateMessage);
    });
    this.presenceSubscriptions.set(userId, sub);
    return () => {
      sub.unsubscribe();
      this.presenceSubscriptions.delete(userId);
    };
  }

  sendMessage(payload: {
    conversationId: string;
    content: string | null;
    messageType: "TEXT" | "IMAGE" | "FILE";
    attachmentUrl?: string | null;
    attachmentName?: string | null;
    attachmentSizeBytes?: number | null;
    attachmentMimeType?: string | null;
  }) {
    this.client.publish({ destination: "/app/chat.send", body: JSON.stringify(payload) });
  }

  sendTyping(conversationId: string, userId: string, typing: boolean) {
    this.client.publish({
      destination: "/app/chat.typing",
      body: JSON.stringify({ conversationId, userId, typing }),
    });
  }

  markDelivered(messageId: string) {
    this.client.publish({ destination: "/app/chat.delivered", body: JSON.stringify(messageId) });
  }

  markRead(messageId: string) {
    this.client.publish({ destination: "/app/chat.read", body: JSON.stringify(messageId) });
  }

  onMessage(handler: MessageHandler) {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }
  onTyping(handler: TypingHandler) {
    this.typingHandlers.add(handler);
    return () => this.typingHandlers.delete(handler);
  }
  onDeliveryReceipt(handler: ReceiptHandler) {
    this.deliveryReceiptHandlers.add(handler);
    return () => this.deliveryReceiptHandlers.delete(handler);
  }
  onReadReceipt(handler: ReceiptHandler) {
    this.readReceiptHandlers.add(handler);
    return () => this.readReceiptHandlers.delete(handler);
  }
  onNotification(handler: NotificationHandler) {
    this.notificationHandlers.add(handler);
    return () => this.notificationHandlers.delete(handler);
  }
  onError(handler: ErrorHandler) {
    this.errorHandlers.add(handler);
    return () => this.errorHandlers.delete(handler);
  }
  onConnectionChange(handler: ConnectionHandler) {
    this.connectionHandlers.add(handler);
    return () => this.connectionHandlers.delete(handler);
  }
}

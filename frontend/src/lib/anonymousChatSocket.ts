import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { AnonymousChatEvent } from "@/types";

const WS_URL = import.meta.env.VITE_WS_URL ?? "http://localhost:8080/ws";

type EventHandler = (event: AnonymousChatEvent) => void;
type ConnectionHandler = (connected: boolean) => void;

/**
 * Separate class from ChatSocket (rather than a shared base with a mode
 * flag) because the two auth schemes and destination sets genuinely don't
 * overlap - trying to unify them would mean conditional branches
 * throughout, which is worse than two small focused classes.
 */
export class AnonymousChatSocket {
  private client: Client;
  private eventHandlers = new Set<EventHandler>();
  private connectionHandlers = new Set<ConnectionHandler>();

  constructor(anonymousToken: string) {
    this.client = new Client({
      webSocketFactory: () => new SockJS(WS_URL) as unknown as WebSocket,
      connectHeaders: { mode: "anonymous", Authorization: `Bearer ${anonymousToken}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.client.onConnect = () => {
      this.connectionHandlers.forEach((h) => h(true));
      this.client.subscribe("/user/queue/anon-events", (frame: IMessage) => {
        const event = JSON.parse(frame.body) as AnonymousChatEvent;
        this.eventHandlers.forEach((h) => h(event));
      });
    };
    this.client.onDisconnect = () => this.connectionHandlers.forEach((h) => h(false));
  }

  connect() {
    this.client.activate();
  }

  disconnect() {
    this.client.deactivate();
  }

  sendMessage(content: string) {
    this.client.publish({ destination: "/app/anon.send", body: JSON.stringify({ content }) });
  }

  sendTyping(typing: boolean) {
    this.client.publish({ destination: "/app/anon.typing", body: JSON.stringify({ typing }) });
  }

  onEvent(handler: EventHandler) {
    this.eventHandlers.add(handler);
    return () => this.eventHandlers.delete(handler);
  }

  onConnectionChange(handler: ConnectionHandler) {
    this.connectionHandlers.add(handler);
    return () => this.connectionHandlers.delete(handler);
  }
}

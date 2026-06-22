package dev.drawethree.essdash.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.drawethree.essdash.util.Redaction;
import io.javalin.http.sse.SseClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class SseManager {

    /** A connected client plus whether it belongs to a read-only DEMO account (IPs masked). */
    private record Client(SseClient sse, boolean demo) {}

    private final CopyOnWriteArrayList<Client> clients = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Logger logger;

    public SseManager(Logger logger) {
        this.logger = logger;
    }

    public void addClient(SseClient client, boolean demo) {
        client.keepAlive();
        Client wrapper = new Client(client, demo);
        clients.add(wrapper);
        client.onClose(() -> clients.remove(wrapper));
    }

    public void broadcast(String event, Map<String, Object> data) {
        if (clients.isEmpty()) return;
        String fullJson = serialize(data);
        if (fullJson == null) return;

        String demoJson = null; // built lazily, only if a demo client is connected
        for (Client client : clients) {
            try {
                if (client.demo()) {
                    if (demoJson == null) demoJson = serialize(maskIps(data));
                    if (demoJson != null) client.sse().sendEvent(event, demoJson);
                } else {
                    client.sse().sendEvent(event, fullJson);
                }
            } catch (Exception ignored) {
                clients.remove(client);
            }
        }
    }

    public int clientCount() {
        return clients.size();
    }

    private String serialize(Map<String, Object> data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            logger.warning("SSE serialize error: " + e.getMessage());
            return null;
        }
    }

    /** Copy of the event payload with IP addresses masked out of every string value (e.g. the
     *  raw console line a player-join produces), for delivery to demo clients. */
    private Map<String, Object> maskIps(Map<String, Object> data) {
        Map<String, Object> copy = new LinkedHashMap<>(data.size());
        for (Map.Entry<String, Object> e : data.entrySet()) {
            Object v = e.getValue();
            copy.put(e.getKey(), v instanceof String s ? Redaction.maskText(s) : v);
        }
        return copy;
    }
}

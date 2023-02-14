package BCR.BRC;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class BCRSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> GUISessions = new TreeMap<>();
    private final Map<String, WebSocketSession> WSSessions = new TreeMap<>();
    private final Map<String, List<String>> WSToGUIsConnections = new TreeMap<>();
    private final Map<String, JSONObject> WSMappings = new TreeMap<>();

    private TextMessage buildTextMessage(String type){
        JSONObject message = new JSONObject();
        message.put("sender", "SpringBootServer");
        message.put("senderIdentity", "SERVER");
        message.put("type", type);

        return new TextMessage(message.toString());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // dopo aver creato la connessione, chiede il tipo di utente
        TextMessage identificationMessage = buildTextMessage("identificationRequest");
        session.sendMessage(identificationMessage);
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // rimuove la sessione dalla lista in cui è presente
        boolean removed = false;

        // ci si aspetta che siano collegati in media meno servizi WS che GUIs
        for(Map.Entry<String, WebSocketSession> entry : WSSessions.entrySet()){
            if(entry.getValue().equals(session)){
                WSSessions.remove(entry.getKey());
                WSMappings.remove(entry.getKey());
                WSToGUIsConnections.remove(entry.getKey());
                removed = true;
                break;
            }
        }

        if(!removed) {
            String name = "";
            for (Map.Entry<String, WebSocketSession> entry : GUISessions.entrySet()) {
                if (entry.getValue().equals(session)) {
                    name = entry.getKey();
                    GUISessions.remove(name);
                    break;
                }
            }
            for(List<String> GUIsList : WSToGUIsConnections.values()){
                GUIsList.remove(name);
            }


        }

        super.afterConnectionClosed(session, status);
    }

    /*
            >>>>>>>>> EX SETUP <<<<<<<<<<
        JSON da WasteService per mapping = {
            sender : "WasteService",
            senderIdentity : "WS"
            type : "mapping",
            roomDimensions : [
                M : int,
                N : int,
            ],
            garbageTypes : [
                {
                    name : "plastic",
                    properties : {
                        coords : [
                            {x:, y:},
                            {x:, y:},
                        ],
                        capacity : {
                            max : float,
                            curr :  float,
                        }
                    }
                },
            ],
            home : {
                coords : [
                    {x:, y:},
                    {x:, y:},
                ]
            },
            loadArea : {
                coords : [
                    {x:, y:},
                    {x:, y:},
                ]
            },
            TTPos : [
                {x:, y:},
                {x:, y:}
            ],
        }
        ---> deve essere mandato uguale alle GUI

         ////////////////\\\\\\\\\\\\\\\\\\
        |/////////// OBSOLETO \\\\\\\\\\\\\|
         \\\\\\\\\\\\\\\\//////////////////
        JSON da WasteService per update = {
            sender : "WasteService",
            senderIdentity : "WS"
            type : "TTupdate",
            TTPos : [
                {x:, y:},
                {x:, y:}
            ],
            garbageTypes : [
                {
                    name : "Plastic",
                    capacity : {
                        max:
                        curr:
                    }
                }
            ]
        }
        ---> deve essere mandato uguale alle GUI

        JSON per identificazione = {
            sender : "",
            type : "identification"
            senderIdentity : "GUI" | "WS"
        }

        JSON per richiesta setup = {
            sender : "",
            senderIdentity : "GUI",
            type : "setupRequest",
            setupRequested : "WasteService 1"
        }
    */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JSONObject payload = new JSONObject(message);

        // Quando una GUI chiede un mapping
        if(payload.getString("type").equals("mappingRequest")){
            String requestedWS = payload.getString("mappingRequested");
            String senderGUI = payload.getString("sender");

            // controlla se la WS è già registrata
            if(WSSessions.containsKey(requestedWS)){
                // controlla se esiste già un mapping
                if(WSMappings.containsKey(requestedWS)){
                    // invia il setup trovato alla GUI
                    session.sendMessage(new TextMessage(WSMappings.get(requestedWS).toString()));
                }
                // se non esiste, viene richiesta
                // la GUI viene notificata successivamente
                else{
                    TextMessage mappingMessage = buildTextMessage("mappingRequest");
                    WSSessions.get(requestedWS).sendMessage(mappingMessage);
                }

                // la GUI viene aggiunta alla lista di GUIs legate al WS
                if(!WSToGUIsConnections.containsKey(requestedWS)) {
                    WSToGUIsConnections.put(requestedWS, new CopyOnWriteArrayList<>());
                }
                WSToGUIsConnections.get(requestedWS).add(senderGUI);
            }else{
                TextMessage noWSMessage = buildTextMessage("WSNotFound");
                session.sendMessage(noWSMessage);
            }
        }
        // Quando un WS invia un setup o un update
        else if(payload.getString("type").equals("mapping")){
            String senderWS = payload.getString("sender");

            // salva o aggiorna lo stato attuale
            WSMappings.put(senderWS, payload);

            // controlla se sia necessario inviare un aggiornamento a qualche GUI
            if(WSToGUIsConnections.containsKey(senderWS)){
                for(String GUIName : WSToGUIsConnections.get(senderWS)){
                    GUISessions.get(GUIName).sendMessage(new TextMessage(payload.toString()));
                }
            }
        }
        // Quando qualcuno cerca di identificarsi
        else if(payload.getString("type").equals("identification")){
            String senderName = payload.getString("sender");

            // sceglie in che lista inserire la sessione
            if(payload.getString("senderIdentity").equals("WS")){
                WSSessions.put(senderName, session);
            }else if(payload.getString("senderIdentity").equals("GUI")){
                GUISessions.put(senderName, session);
            }
        }
    }
}

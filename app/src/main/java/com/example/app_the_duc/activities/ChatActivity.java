package com.example.app_the_duc.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.ChatMessage;
import com.example.app_the_duc.models.ChatResponse;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.widget.TextView;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> chatHistory;
    private EditText edtMsg;
    private static final String CHAT_PREFS = "chat_prefs";
    private static final String CHAT_KEY = "ai_chat_history";
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerView = findViewById(R.id.recyclerChat);
        edtMsg = findViewById(R.id.edtMessage);
        Button btnSend = findViewById(R.id.btnSend);

        chatHistory = loadChatHistory();
        adapter = new ChatAdapter(chatHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        scrollToBottom();

        btnSend.setOnClickListener(v -> {
            String msg = edtMsg.getText().toString().trim();
            if (msg.isEmpty()) return;
            addMessage("user", msg);
            edtMsg.setText("");
            sendToAI(msg);
        });
    }

    private void addMessage(String sender, String msg) {
        chatHistory.add(new ChatMessage(sender, msg, System.currentTimeMillis()));
        adapter.notifyItemInserted(chatHistory.size() - 1);
        saveChatHistory();
        scrollToBottom();
    }

    private void sendToAI(String msg) {
        String userId = Prefs.getUserId(this);
        ApiService api = ApiClient.getClient().create(ApiService.class);
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("message", msg);
        body.put("context", new HashMap<>());
        api.chat(body).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    addMessage("ai", response.body().aiResponse);
                } else {
                    addMessage("ai", "Lỗi AI hoặc kết nối: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                addMessage("ai", "AI không phản hồi: " + t.getMessage());
            }
        });
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            int itemCount = adapter.getItemCount();
            if (itemCount > 0) {
                recyclerView.smoothScrollToPosition(itemCount - 1);
            }
        });
    }

    private List<ChatMessage> loadChatHistory() {
        String json = getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE).getString(CHAT_KEY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
        try {
            return gson.fromJson(json, type);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveChatHistory() {
        String json = gson.toJson(chatHistory);
        getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE).edit().putString(CHAT_KEY, json).apply();
    }
}

// ---------- Adapter phía dưới -------------

class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> list;
    private static final int TYPE_USER = 0;
    private static final int TYPE_AI = 1;
    ChatAdapter(List<ChatMessage> l) { list = l; }
    @Override
    public int getItemViewType(int position) {
        return "ai".equals(list.get(position).sender) ? TYPE_AI : TYPE_USER;
    }
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserHolder(v);
        } else {
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
            return new AIHolder(v);
        }
    }
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((MsgHolder)holder).txtMsg.setText(list.get(position).message);
    }
    @Override
    public int getItemCount() { return list.size(); }
    static abstract class MsgHolder extends RecyclerView.ViewHolder {
        TextView txtMsg;
        MsgHolder(View v) { super(v); txtMsg = v.findViewById(R.id.txtMessage); }
    }
    static class UserHolder extends MsgHolder { UserHolder(View v) { super(v); } }
    static class AIHolder extends MsgHolder { AIHolder(View v) { super(v); } }
}



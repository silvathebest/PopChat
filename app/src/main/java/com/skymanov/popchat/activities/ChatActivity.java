package com.skymanov.popchat.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.skymanov.popchat.adapters.ChatAdapter;
import com.skymanov.popchat.databinding.ActivityChatBinding;
import com.skymanov.popchat.models.ChatMessage;
import com.skymanov.popchat.models.User;
import com.skymanov.popchat.network.ApiClient;
import com.skymanov.popchat.network.ApiService;
import com.skymanov.popchat.utilities.Constants;
import com.skymanov.popchat.utilities.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessages();
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)
        );

        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void sendMessage() {
        if (binding.inputMessage.getText().toString().trim().isEmpty()) return;

        HashMap<String, Object> message = new HashMap<>();

        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());

        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversionId != null) {
            updateConversion(binding.inputMessage.getText().toString());
        } else {
            HashMap<String, Object> conversion = new HashMap<>();

            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());

            addConversion(conversion);
        }
        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            } catch (Exception e) {
                showToast(e.getMessage());
            }
        }

        binding.inputMessage.setText(null);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class)
                .sendMessage(Constants.getRemoteMsgHeaders(), messageBody).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");

                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);
                                showToast(error.getString("error"));
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    showToast("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(receiverUser.id)
                .addSnapshotListener(ChatActivity.this, (value, error) -> {
                    if (error != null) return;

                    if (value != null) {
                        if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                            int availability = Objects.requireNonNull(value.getLong(Constants.KEY_AVAILABILITY)).intValue();
                            isReceiverAvailable = availability == 1;
                        }
                        receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                        if (receiverUser.image == null) {
                            receiverUser.image = value.getString(Constants.KEY_IMAGE);
                            chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                            chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                        }
                    }

                    if (isReceiverAvailable) {
                        binding.textAvailability.setVisibility(View.VISIBLE);
                    } else {
                        binding.textAvailability.setVisibility(View.GONE);
                    }

                });
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    @SuppressLint("NotifyDataSetChanged")
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) return;

        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }

            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));

            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeChanged(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }

        binding.progressbar.setVisibility(View.GONE);

        if (conversionId == null) checkForConversion();
    };

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }
    }

    public void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
        binding.imageInfo.setOnClickListener(v -> {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Do you want to create report?");
            // Set up the input
            final EditText input = new EditText(this);
            alert.setView(input);
            alert.setPositiveButton("Ok", (dialog, whichButton) -> createPdf(input.getText().toString().trim()));

            alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
            });

            alert.show();

        });
    }

    private void createPdf(String keyword) {
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(14);
        paint.setStyle(Paint.Style.FILL);

        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "chatReport.pdf");
        PdfDocument pdfDocument = new PdfDocument();
        AtomicLong totalTime = new AtomicLong();

        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(receiverUser.id)
                .get()
                .addOnCompleteListener(snapshotTask -> {
                    if (snapshotTask.isSuccessful() && snapshotTask.getResult() != null) {
                        totalTime.set(snapshotTask.getResult().getLong(Constants.KEY_TOTAL_TIME));
                    }
                });

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {

                        int totalMessage = 0;
                        int totalMessageCountByKeyword = 0;
                        while (totalMessage < Objects.requireNonNull(task.getResult()).getDocuments().size()) {
                            totalMessage++;
                        }

                        for (DocumentSnapshot documentSnapshot : task.getResult().getDocuments()) {
                            String message = documentSnapshot.getString(Constants.KEY_MESSAGE);
                            if (!message.contains(keyword)) continue;
                            totalMessageCountByKeyword++;
                        }

                        try (FileOutputStream out = new FileOutputStream(file)) {
                            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(500, totalMessageCountByKeyword * 30 + 120, 1).create();
                            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                            Canvas c = page.getCanvas();
                            c.drawText("Name: " + receiverUser.name, 20, 20, paint);
                            c.drawText("Total message count: " + totalMessage, 20, 40, paint);
                            c.drawText("Total time spent online: " + totalTime, 20, 60, paint);
                            c.drawText("All messages by keyword", 20, 100, paint);

                            int y = 120;
                            for (DocumentSnapshot documentSnapshot : task.getResult().getDocuments()) {
                                String message = documentSnapshot.getString(Constants.KEY_MESSAGE);

                                assert message != null;
                                if (!message.contains(keyword)) continue;

                                String date = documentSnapshot.getDate(Constants.KEY_TIMESTAMP).toString();

                                c.drawText(receiverUser.name + ": ", 20, y, paint);
                                c.drawText(documentSnapshot.getString(Constants.KEY_MESSAGE), receiverUser.name.length() * 8 + 20, y, paint);
                                c.drawText(date, (receiverUser.name.length() + message.length()) * 8 + 20, y, paint);
                                y += 20;
                            }

                            c.save();
                            pdfDocument.finishPage(page);
                            pdfDocument.writeTo(out);
                            out.flush();

                            showToast("Report saved to " + file.getPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                            showToast("Error: " + e.getMessage());
                        } finally {
                            pdfDocument.close();
                        }
                    } else {
                        showToast("This user didn't write any message yet");
                    }
                });
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void updateConversion(String message) {
        DocumentReference documentReference = database
                .collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);

        documentReference.update(Constants.KEY_LAST_MESSAGE, message, Constants.KEY_TIMESTAMP, new Date());

    }

    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            checkForConversionRemotely(preferenceManager.getString(Constants.KEY_USER_ID), receiverUser.id);
            checkForConversionRemotely(receiverUser.id, preferenceManager.getString(Constants.KEY_USER_ID));
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}
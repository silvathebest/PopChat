package com.skymanov.popchat.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.skymanov.popchat.databinding.ActivityChatBinding;
import com.skymanov.popchat.models.User;
import com.skymanov.popchat.utilities.Constants;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private User receiverUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
    }

    public void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.imageInfo.setOnClickListener(v -> {

        });
    }
}
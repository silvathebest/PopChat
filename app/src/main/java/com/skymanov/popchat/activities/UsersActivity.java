package com.skymanov.popchat.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.skymanov.popchat.adapters.UsersAdapter;
import com.skymanov.popchat.databinding.ActivityUsersBinding;
import com.skymanov.popchat.listeners.UserListener;
import com.skymanov.popchat.models.User;
import com.skymanov.popchat.utilities.Constants;
import com.skymanov.popchat.utilities.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class UsersActivity extends BaseActivity implements UserListener {
    private ActivityUsersBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        getUsers();
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
    }

    private void getUsers() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS).get().addOnCompleteListener(task -> {
            loading(false);
            String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);

            if (task.isSuccessful() && task.getResult() != null) {
                List<User> users = new ArrayList<>();

                for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                    if (currentUserId.equals(queryDocumentSnapshot.getId())) continue;

                    User user = new User();
                    user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME);
                    user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL);
                    user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE);
                    user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                    user.id = queryDocumentSnapshot.getId();

                    users.add(user);
                }

                if (users.size() > 0) {
                    UsersAdapter usersAdapter = new UsersAdapter(users, this);
                    binding.userRecyclerView.setAdapter(usersAdapter);
                    binding.userRecyclerView.setVisibility(View.VISIBLE);
                } else {
                    showErrorMessage();
                }

            } else {
                showErrorMessage();
            }
        });
    }

    private void showErrorMessage() {
        binding.textErrorMessage.setText(String.format("%s", "No user available"));
        binding.textErrorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.progressbar.setVisibility(View.VISIBLE);
            return;
        }
        binding.progressbar.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}
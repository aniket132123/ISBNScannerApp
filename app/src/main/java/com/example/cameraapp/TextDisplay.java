package com.example.cameraapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.camera.core.ExperimentalGetImage;

@ExperimentalGetImage public class TextDisplay extends MainActivity {
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_display);
        textView = findViewById(R.id.textView);
        Button backButton = findViewById(R.id.back_button);
//        if (!reviewCollection.isEmpty())
            textView.setText(getReviews());
//        else textView.setText("");
        backButton.setOnClickListener(v -> {
            Intent returnIntent = new Intent(TextDisplay.this, MainActivity.class);
            startActivity(returnIntent);
        });
    }
}

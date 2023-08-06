package com.example.cameraapp;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;



@ExperimentalGetImage public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer{
    private PreviewView previewView;
    private TextView otherTextView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private BarcodeScannerOptions options;
    private ExecutorService backgroundSearch;
    private static String bookTitle;
    private static String bookAuthor;
    private String barcodeValue;
    private ArrayList<String> reviewCollection;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_camera);
        otherTextView = findViewById(R.id.otherTextView);
        previewView = findViewById(R.id.previewView);
        previewView.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
        Button takePictureButton = findViewById(R.id.button_capture);
        Button searchButton = findViewById(R.id.button_search);
        ExecutorService backgroundSearch = Executors.newSingleThreadExecutor();


        options =
                new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_ALL_FORMATS
                )
                .enableAllPotentialBarcodes()
                .build();
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                if(checkForPermission()){
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    startCamera(cameraProvider);
                } else {
                    requestPermission();
                }
            } catch (ExecutionException | InterruptedException ignored) {}
        }, getExecutor());

        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableCamera();

            }
        });

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backgroundSearch.execute(() -> {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Search Initiated", Toast.LENGTH_SHORT).show());
                    getInfoFromISBN(barcodeValue);
                    storyGraphBookResults();
                    barnesAndNobleBookResults();
                    kirkusBookResults();
                    googleBooksResults();
//                    otherTextView.setText(reviewCollection.toString());
                    Intent searchIntent = new Intent(MainActivity.this, TextDisplay.class);
                    startActivity(searchIntent);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Search Complete", Toast.LENGTH_SHORT).show());
                });

            }
        });
    }
    private Executor getExecutor(){
        return ContextCompat.getMainExecutor(this);
    }
    @SuppressLint("RestrictedApi")
    private void startCamera(@NonNull ProcessCameraProvider cameraProvider){
        cameraProvider.unbindAll();
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.createSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), this);

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
    }
    private boolean checkForPermission() {
        int permissionCheck = checkSelfPermission(android.Manifest.permission.CAMERA);
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission Granted");
        } else {
            Log.d(TAG, "Permission Required!");
        }
        return ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission(){
        ActivityCompat.
                requestPermissions(MainActivity.this, new String[]{
                        android.Manifest.permission.CAMERA
                }, 1);
    }

    private void enableCamera(){
        imageCapture.takePicture(
                getExecutor(),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        //Toast.makeText(MainActivity.this, "Photo captured!", Toast.LENGTH_SHORT).show();
                        analyze(image);
                        image.close();
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Error capturing photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy){
        Image mediaImage = imageProxy.getImage();

        if(mediaImage != null){
            InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            //barcode recognition
            BarcodeScanner scanner = BarcodeScanning.getClient(options);


            Task<List<Barcode>> result = scanner.process(image)
                    .addOnSuccessListener(barcodes -> {

                        if (barcodes.size() > 0) {
                            barcodeValue = barcodes.get(0).getRawValue();
                            Toast.makeText(MainActivity.this, barcodeValue, Toast.LENGTH_LONG).show();
                        }
                        else barcodeValue = "none";


                    })
                    .addOnFailureListener(
                            e -> Toast.makeText(MainActivity.this, "Process Failed", Toast.LENGTH_SHORT).show());
            }
        }

    //grabs information from an ISBN number
    private void getInfoFromISBN(String barcodeString){
        reviewCollection = new ArrayList<>();
        Document doc;
        try {
            doc = Jsoup.connect("https://www.isbnsearcher.com/books/" + barcodeString)
                    .timeout(5000)
                    .get();
            Elements title = doc.select("h1");
            Elements author = doc.getElementsByAttributeValueContaining("href", "author");
            bookTitle = title.text();
            bookAuthor = author.text();

        } catch (IOException ignore){}
    }

    //primarily here to grab customer reviews
    private void storyGraphBookResults(){
        Document doc;
        try {
            String modifiedBookTitle = bookTitle.replace(" ", "%20");
            String modifiedAuthorName = bookAuthor.replace(" ", "%20");

            doc = Jsoup.connect("https://app.thestorygraph.com/browse?search_term=" + modifiedBookTitle + modifiedAuthorName)
                    .timeout(10000)
                    .get();

            Elements links = doc.select("a[href]");
            String bookCode = links.get(14).attr("href");
            doc = Jsoup.connect("https://app.thestorygraph.com/" + bookCode.replace("/books", "book_reviews") + "?written_explanations_only=true")
                    .timeout(10000)
                    .get();
            Elements reviews = doc.getElementsByClass("trix-content review-explanation");
            for(Element review : reviews){
                reviewCollection.add(review.text());
            }
        } catch (Exception ignored) {}
    }
    //primarily here to grab editorial reviews
    private void barnesAndNobleBookResults(){
        Document doc;
        try {
            String modifiedBookTitle = bookTitle.replace(" ", "+");
            String modifiedAuthorName = bookAuthor.replace(" ", "+");


            doc = Jsoup.connect("https://www.google.com/search?q=" + modifiedBookTitle + "+" + modifiedAuthorName + "+barnes+and+noble+book+review")
                    .timeout(10000)
                    .get();
            Elements links = doc.getElementsByAttributeValueContaining("href", "https://www.barnesandnoble.com/");

            doc = Jsoup.connect(links.get(0).attr("href"))
                    .timeout(10000)
                    .get();

            //checks for a block quote containing an editorial review
            Elements editorialReview = doc.getElementsByTag("blockquote");
            reviewCollection.add(editorialReview.text());

            // //searches for a generic overview
            Elements reviewsSection = doc.getElementsByClass( "overview-cntnt");
            for(Element review : reviewsSection){
                reviewCollection.add(review.text());
            }
        }
        catch (Exception ignored) {}
    }

    private void kirkusBookResults(){
        Document doc;
        try {
            String modifiedBookTitle = bookTitle.replace(" ", "-").toLowerCase();
            String modifiedAuthorName = bookAuthor.replace(" ", "-").toLowerCase();
            doc = Jsoup.connect("https://www.kirkusreviews.com/book-reviews/"+ modifiedAuthorName + "/" + modifiedBookTitle + "/")
                    .timeout(10000)
                    .get();
            Elements reviewStuff = doc.getElementsByClass("first-alphabet book-content text-left");

            reviewCollection.add(reviewStuff.first().text());
        } catch (Exception ignored) {}
    }

    private void googleBooksResults(){
        Document doc;
        try {
            String modifiedBookTitle = bookTitle.replace(" ", "-").toLowerCase();
            String modifiedAuthorName = bookAuthor.replace(" ", "-").toLowerCase();
            doc = Jsoup.connect("https://www.google.com/search?q=" + modifiedBookTitle + "+" + modifiedAuthorName + "+google+books+book+review")
                    .timeout(10000)
                    .get();

            Elements links = doc.getElementsByAttributeValueContaining("href", "https://books.google.com");

            doc = Jsoup.connect(links.first().attr("href"))
                    .timeout(10000)
                    .get();
            Elements reviewLink = doc.getElementsByAttributeValueContaining("href", "=reviews");

            doc = Jsoup.connect(reviewLink.attr("href"))
                    .timeout(10000)
                    .get();
            Elements reviews = doc.getElementsByClass("single-review");
            for (Element review : reviews) {
                String modifiedReview = review.text().replace("Review:", "");
                reviewCollection.add(modifiedReview.replace("Read full review", ""));
            }

        } catch (Exception ignored) {}
    }

    public String getReviews() {
        if (reviewCollection != null){
            return reviewCollection.toString();
        } else return "guh";
    }
}
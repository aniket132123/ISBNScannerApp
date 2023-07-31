package com.example.cameraapp;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;



@ExperimentalGetImage public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer{
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private Button takePictureButton;
    private TextView textView;
    private BarcodeScannerOptions options;
    private static String bookTitle;
    private static String bookAuthor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        previewView = findViewById(R.id.previewView);
        textView = findViewById(R.id.textView);
        previewView.setPreferredImplementationMode(PreviewView.ImplementationMode.SURFACE_VIEW);
        takePictureButton = findViewById(R.id.button_capture);

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
            } catch (ExecutionException | InterruptedException e) {}
        }, getExecutor());

        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableCamera();
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
                        for(Barcode barcode : barcodes){
                            textView.setText(barcode.getRawValue());
                            Toast.makeText(MainActivity.this, barcode.getRawValue(), Toast.LENGTH_SHORT).show();
//                            getInfoFromISBN(barcode.toString());
                        }
//                            storyGraphBookResults();
//                            barnesAndNobleBookResults();
//                            kirkusBookResults();
//                            googleBooksResults();
                    })
                    .addOnFailureListener(
                            e -> Toast.makeText(MainActivity.this, "Process Failed", Toast.LENGTH_SHORT).show());
            }
        }

    //grabs information from an ISBN number
    private void getInfoFromISBN(String barcodeString){
        Document doc;
        try {
            doc = Jsoup.connect("https://www.isbnsearcher.com/books/" + barcodeString)
                    .timeout(5000)
                    .get();
            Elements title = doc.select("h1");
            Elements author = doc.getElementsByAttributeValueContaining("href", "author");
            bookTitle = title.text();
            bookAuthor = author.text();

        } catch (IOException e){
            Toast.makeText(MainActivity.this, "Book could not be found.", Toast.LENGTH_SHORT).show();
        }
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
                System.out.println(review.text());
                System.out.println(" ");
            }
        } catch (Exception e) {}
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
            System.out.println(editorialReview.text());

            // //searches for a generic overview
            Elements reviewsSection = doc.getElementsByClass( "overview-cntnt");
            for(Element review : reviewsSection){
                System.out.println(review.text());
                System.out.println(" ");
            }
        }
        catch (Exception e) {}
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

            System.out.println(reviewStuff.first().text());
        } catch (Exception e) {}
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
                System.out.println(modifiedReview.replace("Read full review", ""));
                System.out.println("");
            }
        } catch (Exception e) {}
    }

    }
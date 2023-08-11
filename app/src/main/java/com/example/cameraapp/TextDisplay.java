package com.example.cameraapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.camera.core.ExperimentalGetImage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalGetImage public class TextDisplay extends MainActivity {
    private TextView textView;
    private EditText inputTextAuthor;
    private EditText inputTextBook;
    private static String bookTitle;
    private static String bookAuthor;
    private ArrayList<String> reviewCollection;
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_display);
        textView = findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());
        inputTextAuthor = findViewById(R.id.inputTextAuthor);
        inputTextBook = findViewById(R.id.inputTextBook);
        Button backButton = findViewById(R.id.back_button);
        Button textSearchButton = findViewById(R.id.textSearchButton);
        ExecutorService backgroundSearch = Executors.newSingleThreadExecutor();
        backgroundSearch.execute(() -> {
            Intent receiverIntent = getIntent();
            String receivedValue = receiverIntent.getStringExtra("KEY_SENDER");
            getInfoFromISBN(receivedValue);
            storyGraphBookResults();
            barnesAndNobleBookResults();
            kirkusBookResults();
            googleBooksResults();
            textView.setText(reviewCollection.toString());
        });

        backButton.setOnClickListener(v -> {
            Intent returnIntent = new Intent(TextDisplay.this, MainActivity.class);
            startActivity(returnIntent);
        });

        textSearchButton.setOnClickListener(v -> {
            textView.setText(" ");
            bookTitle = inputTextBook.getText().toString();
            bookAuthor = inputTextAuthor.getText().toString();
            backgroundSearch.execute(() -> {
                storyGraphBookResults();
                barnesAndNobleBookResults();
                kirkusBookResults();
                googleBooksResults();
                textView.setText(reviewCollection.toString());
            });
        });

    }
    private void getInfoFromISBN(String barcodeString){
        reviewCollection = new ArrayList<>();
        Document doc;
        try {
            doc = Jsoup.connect("https://www.abebooks.com/servlet/SearchResults?kn="+ barcodeString +"&sts=t&cm_sp=SearchF-_-topnav-_-Results&ds=20")
                    .timeout(5000)
                    .userAgent("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
                    .get();
            Elements title = doc.getElementsByAttributeValueContaining("data-cy", "listing-title");
            Elements author = doc.getElementsByTag("strong");
            bookTitle = title.get(0).text();
            bookAuthor = author.get(0).text().trim();

            if(bookAuthor.contains(",")){
                StringBuilder tempBookAuthor = new StringBuilder();
                for(int i = bookAuthor.indexOf(",") + 1; i < bookAuthor.length(); i++){
                    tempBookAuthor.append(bookAuthor.charAt(i));
                }
                if (tempBookAuthor.charAt(0) == ' ') {
                    tempBookAuthor.setCharAt(0, '\0');
                }
                tempBookAuthor.append(" ");
                for(int i = 0; i < bookAuthor.indexOf(","); i++){
                    tempBookAuthor.append(bookAuthor.charAt(i));
                }
                bookAuthor = tempBookAuthor.toString().trim();
            }
        } catch (IOException | IndexOutOfBoundsException ignore){
            reviewCollection.add("Book could not be found.");
        }
    }

    //primarily here to grab customer reviews
    private void storyGraphBookResults(){
        Document doc;
        try {
            String modifiedBookTitle = bookTitle.replace(" ", "%20");
            String modifiedAuthorName = bookAuthor.replace(" ", "%20");

            doc = Jsoup.connect("https://app.thestorygraph.com/browse?search_term=" + modifiedBookTitle + "%20" + modifiedAuthorName)
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
}

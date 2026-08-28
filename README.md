# ISBN Review Aggregator

An Android app that scans a book's ISBN barcode and instantly pulls together reviews from multiple review sites into a single aggregated view — no more manually checking StoryGraph, Barnes & Noble, Google Books, and Kirkus one by one.

## Features

- **Barcode scanning** — Point your camera at any book's ISBN barcode using Google ML Kit for fast, reliable recognition.
- **Multi-source aggregation** — Pulls reviews from StoryGraph, Barnes & Noble, Google Books, and Kirkus, combining them into one screen in under 10 seconds.
- **Manual search failsafe** — If a barcode won't scan or isn't recognized, type in the book title instead and get the same aggregated results just as fast.

## How It Works

1. **Scan** — The app captures a barcode using the device camera and decodes the ISBN with Google ML Kit.
2. **Fetch** — A Jsoup-based web scraper queries each supported review site for that ISBN (or title, in manual mode).
3. **Normalize** — Ratings and comments in different formats are parsed and standardized into a common review structure.
4. **Aggregate** — Results from all sources are merged and displayed together on a single screen.

## Tech Stack

- **Language:** Java
- **Platform:** Android
- **Barcode Scanning:** Google ML Kit
- **Web Scraping:** Jsoup
- **Sources:** StoryGraph, Barnes & Noble, Google Books, Kirkus

## Why I Built This

Checking multiple review sites for one book can be slow and repetitive so I made this app that cuts that down from a few minutes of manual searching to just a few seconds, with a fallback for books that may not scan cleanly.

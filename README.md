# Welcome to Bulifier

[![Watch the Demo](https://img.shields.io/badge/Watch-Demo-red)](https://www.youtube.com/watch?v=Q0iQKEnIRtI&t=2s)  
[![Get it on Google Play](https://img.shields.io/badge/Get_it_on-Google_Play-green)](https://play.google.com/store/apps/details?id=com.bulifier)  
[![Visit Website](https://img.shields.io/badge/Visit-Website-blue)](https://bulifier.com/)  
[![Release Notes](https://img.shields.io/badge/Release-Notes-orange)](https://chatgpt.com/c/RELEASE_NOTES.md)

Bulifier is an **open-source**, **AI-powered mobile IDE** that shifts software development into a **bullet-point-driven workflow**:

1.  **Mobile-First Coding** – Seamlessly build, edit, and commit to your projects directly from a phone or tablet.
2.  **Bullet Points as a Language** – Represent and refine your application’s logic as bullet points. The AI then generates and updates the raw source code under the hood.

## Why Bullet Points?

Bulifier is about **changing your perspective** on how you interact with code. You can capture the essence of your application in concise bullet points, making it easier for everyone—including AI—to read, reason, and refine, ensuring your project remains aligned with your goals.

----------

## Key Features

1.  **Mobile IDE**

    -   Develop on-the-go with robust Git integration (clone, pull, push, commit, etc.).
    -   Write and maintain your application logic without needing a full desktop environment.
2.  **Bullet-Point Development**

    -   Outline your ideas in natural-language bullet points.
    -   Let the AI take care of creating or updating the underlying source code.
3.  **Agentic Flow**

    -   Invoke the **Agent** to execute multiple commands in a single operation.
    -   Whether you’re adding a new feature, reorganizing project structure, or refining logic, the Agent selects the best schemas and context automatically.
4.  **Sync**

    -   **Two Sync Actions**:
        -   Generate or update **raw code** from bullet points.
        -   Update **bullet points** based on the current raw code.
    -   Sync only the components you’ve changed, or apply a broader update to everything.

----------

## The Play Store Version

You can also experience Bulifier as a ready-to-use app from the Google Play Store. This version uses a **Firebase Real-time Database** as an AI proxy, providing:

-   **Excellent Offline Support** – Keep working even when you’re offline.
-   **Faster Parallel Execution** – Especially noticeable when using the Agentic Flow.
-   **All Open-Source Business Logic** – Everything critical remains open source here in this repository.

If you prefer a more direct approach or want to experiment with your own keys, you can stick to the open-source version in this repo. Either way, all of Bulifier’s core features are at your fingertips.

----------

## Getting Started

1.  **Install from the [Google Play Store](https://play.google.com/store/apps/details?id=com.bulifier).**
2.  **Or build from this repo**:
    -   Clone the project
    -   Open in Android Studio
    -   Run the demo module
    -   Start exploring Bulifier

----------

## Technical Details

-   **AI Requests**
    -   **Open Source**: Direct API calls with your own keys (e.g., GPT, Claude).
    -   **Play Store**: Via Firebase Real-time Database as a backend proxy to safeguard keys.
-   **Bullet-Point to Code** – The bullet-point layer is your main interface; Bulifier handles the conversions to raw files automatically.
-   **Open Source & Licensing** – Bulifier is published under the Apache License 2.0. It uses a modified version of JGit under the Eclipse Distribution License 1.0.
-  **Schemas**
   Schemas are the prompt engineering, it powers the agent and allows you to call them manually. You can also create new schemas or update the existing one.
    -   **`update-bullet-with-raw`**  
        Uses your existing raw files to update the corresponding bullet points. This way, if code is changed manually or elsewhere, your bullet-point representation stays accurate.

    -   **`update-raw-with-bullet`**  
        Converts or updates raw files based on the latest bullet points. Ideal for generating new files or merging new logic back into existing code.

    -   **`debulify-file`**  
        Translates bullet points into code at the file level. Perfect for situations where you only want to convert (or reconvert) specific sections or modules of your project.

    -   **`rebulify-file`**  
        Adjusts bullet points by reading the current state of a file’s code, ensuring your high-level outline matches the latest version of that file.

----------

## Contributing

We welcome feedback and contributions:

-   Submit issues for bugs or feature requests
-   Open pull requests to enhance the platform or fix problems

----------

## License

Licensed under the **Apache License 2.0** by **Gazman Dev LLC**.

Includes a modified version of **JGit** (under the Eclipse Distribution License 1.0). For more details, see the [Eclipse JGit project](https://www.eclipse.org/jgit/).

----------

**Thanks for checking out Bulifier!** We aim to offer a _fresh, AI-centric perspective_ on coding—one that feels natural for developers and fosters collaboration, all while being fully accessible on mobile devices. Your feedback and ideas are always welcome.
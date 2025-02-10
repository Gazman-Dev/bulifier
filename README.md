# Welcome to Bulifier

[![Watch the Demo](https://img.shields.io/badge/Watch-Demo-red)](https://www.youtube.com/watch?v=ZZbraX6OFMg&ab_channel=Bulifier)  
[![Get it on Google Play](https://img.shields.io/badge/Get_it_on-Google_Play-green)](https://play.google.com/store/apps/details?id=com.bulifier)  
[![Visit Website](https://img.shields.io/badge/Visit-Website-blue)](https://bulifier.com/)  
[![Release Notes](https://img.shields.io/badge/Release-Notes-orange)](https://chatgpt.com/c/RELEASE_NOTES.md)

Bulifier is an **open-source**, **AI-powered mobile IDE** that shifts software development into a **bullet-point-driven workflow**. With the latest updates in version 2.3+, Bulifier now supports binary file handling and enhanced JavaScript integration – all designed to streamline your coding experience on mobile devices.

1. **Mobile-First Coding** – Seamlessly build, edit, and commit to your projects directly from a phone or tablet.
2. **Bullet Points as a Language** – Represent and refine your application’s logic as bullet points. The AI then generates and updates the raw source code under the hood.

## Why Bullet Points?

Bulifier is about **changing your perspective** on how you interact with code. Capture the essence of your application in concise bullet points, making it easier for everyone – including AI – to read, reason, and refine, ensuring your project stays perfectly aligned with your goals.

----------

## New in v2.3+: Binary File Support

Starting with version 2.3+, Bulifier now supports binary files. This major update resolves deletion issues and eliminates the need for git configuration files. You can now display images, fonts, and other binary assets directly on your device, enriching your mobile development experience.  
[Read more about binary support](https://bulifier.com/posts/001/)

----------

## Key Features

1. **Mobile IDE**
    - Develop on-the-go with robust Git integration (clone, pull, push, commit, etc.).
    - Write and maintain your application logic without a full desktop environment.

2. **Bullet-Point Development**
    - Outline your ideas in natural-language bullet points.
    - Let the AI handle creating or updating the underlying source code.

3. **Agentic Flow**
    - Invoke the **Agent** to execute multiple commands in a single operation.
    - Whether you’re adding new features, reorganizing your project structure, or refining logic, the Agent automatically selects the best schemas and context.

4. **Sync**
    - **Two Sync Actions**:
        - Generate or update **raw code** from bullet points.
        - Update **bullet points** based on the current raw code.
    - Choose to sync only the components you’ve changed or apply a broader update across the project.

5. **Binary File Support (v2.3+)**
    - Display images, fonts, and other binary files directly on your device.
    - Overcome issues with file deletion and simplify configuration management.
    - **No more need for git config files.**

6. **Templates and Schema Customization**
    - Kickstart your projects with ready-to-use templates—including a JS template—available in both the open source and Play Store versions.
    - Templates provide startup files for your project, setting the foundation for your development.
    - Deep schema customization lets you tailor AI requests to suit the specific needs of each template, making it a powerful tool to optimize your workflow.

----------

## The Play Store Version

Experience Bulifier as a ready-to-use app from the [Google Play Store](https://play.google.com/store/apps/details?id=com.bulifier). This version uses a **Firebase Real-time Database** as an AI proxy for enhanced performance and includes:

- **Excellent Offline Support** – Keep working even without an internet connection.
- **Faster Parallel Execution** – Especially effective when using Agentic Flow.
- **Exclusive JavaScript Integration:**
    - **Run JS Code on Device:** Execute JavaScript code natively on Android.
    - **NPM Dependencies:** Manage NPM packages with our dedicated Bulifier-NPM client.
    - **Real-Time Console Logs:** Monitor JS execution live.
    - **Project Sharing:** Export entire projects as zipped files for sharing via Android’s native sharing functionality or Git.
- **Pre-Built Templates:**
    - Utilize pre-built project templates (including the JS template) available in both versions for rapid project startup and schema customization.

**Note:** While the advanced JS integration features – such as on-device JS execution, NPM dependency management, and live console logs – are exclusive to the Play Store version, the core templates and schema customization functionality remain fully open source.

**Open Source Policy Update:**  
Our new policy ensures that **all AI-related business logic remains fully open source**. However, advanced features like on-device JS execution and Firebase proxying for AI requests are exclusive to the Play Store app.  
[Learn more about our open source policy changes](https://bulifier.com/posts/open-source-policy-update/)

If you prefer a more direct approach or wish to experiment with your own API keys, you can stick to the open-source version available in this repository. Either way, all of Bulifier’s core features are at your fingertips.

----------

## Getting Started

1. **Install from the [Google Play Store](https://play.google.com/store/apps/details?id=com.bulifier).**
2. **Or build from this repo:**
    - Clone the project.
    - Open in Android Studio.
    - Run the demo module.
    - Start exploring Bulifier.

----------

## Technical Details

- **AI Requests:**
    - **Open Source:** Direct API calls with your own keys (e.g., GPT, Claude).
    - **Play Store:** Via Firebase Real-time Database as a backend proxy to safeguard keys.
- **Bullet-Point to Code:**  
  The bullet-point layer serves as your main interface; Bulifier automatically handles the conversion to raw files.
- **Open Source & Licensing:**  
  Bulifier is published under the Apache License 2.0 and includes a modified version of JGit (under the Eclipse Distribution License 1.0).
- **Schemas:**  
  Schemas are the prompt engineering behind the AI Agent. They power operations and can be customized or extended:
    - **`update-bullet-with-raw`**: Uses raw files to update bullet points.
    - **`update-raw-with-bullet`**: Converts bullet points into raw files.
    - **`debulify-file`**: Translates bullet points into code at the file level.
    - **`rebulify-file`**: Updates bullet points based on a file’s current code.

----------

## Contributing

We welcome your feedback and contributions:
- Submit issues for bugs or feature requests.
- Open pull requests to enhance the platform or fix problems.

----------

## License

Licensed under the **Apache License 2.0** by **Gazman Dev LLC**.

Includes a modified version of **JGit** (under the Eclipse Distribution License 1.0). For more details, see the [Eclipse JGit project](https://www.eclipse.org/jgit/).

----------

**Thanks for checking out Bulifier!**  
We aim to offer a _fresh, AI-centric perspective_ on coding—one that feels natural for developers and fosters collaboration, all while being fully accessible on mobile devices. Your feedback and ideas are always welcome.

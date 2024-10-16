# Bulifier

*Watch the demo on [YouTube](https://www.youtube.com/watch?v=Q0iQKEnIRtI&t=2s)*

[Release Notes](RELEASE_NOTES.md) | [Known Bugs](KNOWN_BUGS.md)

---

Bulifier is an open-source project that leverages AI to revolutionize software development, introducing a new intermediary language based on bullet points. The project aims to bridge the gap between human logic and AI-generated code, making mobile-based development a reality.

## Key Concepts

- **Bulify**: Convert your ideas into structured bullet points.
- **Debulify**: Transform bullet points into various output formats (e.g., Python, Java, or prose).
- **Rebulify**: Update existing bullet point files with new features or modifications.
- **Schema Management**: Create and update schemas to customize Bulifier for your specific needs.
- **Git Integration**: Manage your code repositories directly within Bulifier using Git commands.

Bulifier envisions a future where developers focus on high-level logic while AI handles the code generation, allowing software development to shift from laptops to mobile devices.

## Feature: Git Support

Bulifier now includes integrated Git support, enabling you to manage your code repositories seamlessly within the app. By leveraging a forked version of JGit—converted into a Gradle project and made compatible with Android—you can perform essential Git operations without leaving Bulifier.

### Supported Git Features

- **Clone**: Clone remote repositories to your local device.
- **Pull & Push**: Synchronize your local repository with remote repositories.
- **Checkout Tags and Branches**: Switch between different branches and tags effortlessly.
- **Commit & Reset**: Commit your changes and undo any unsaved changes, resetting to the current local branch.

*Note: Conflict resolution is not supported in this version.*

## Feature: Schema Management

Bulifier allows you to update existing schemas and create new ones, unlocking a whole new realm of possibilities:

- **Personalized Project Scopes**: Incorporate your project scope directly into the schema for tailored content generation.
- **Diverse Content Generation**: Create schemas for various content types like books, documentation, and more.
- **Customizable Developer Schemas**: Pre-defined schemas for code writing can be updated to suit your needs.

### How to Use Schema Management

1. **Manual Creation**: Create new schema files manually for custom content types.
2. **Bulk Updates**: Use the update-schema schema to modify all schemas at once.
3. **Schema Customization**: Tailor the update-schema schema itself for even more flexibility.

## Getting Started & Contributing

This is a fully functional Android project. Start by running the demo module.

We welcome contributions! A great place to start is by checking out the [Known Bugs](KNOWN_BUGS.md) file. Feel free to submit PRs for small changes or create an issue to discuss larger modifications.

## License

Bulifier is released under the Apache License 2.0 by Gazman Dev LLC.

This project includes code from JGit, which is licensed under the Eclipse Distribution License (EDL) 1.0. For more information about JGit and its license, please refer to the [JGit project](https://www.eclipse.org/jgit/).
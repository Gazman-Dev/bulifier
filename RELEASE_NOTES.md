# Bulifier Release Notes

## Release 2.4.02

### New Features and Improvements

#### Model Selection in `bulifier.yaml`

- **Added Support for Model Selection**:
    - You can now specify the model directly in your `bulifier.yaml` configuration file.
        - Supported models include:
            - `gpt-4o`
            - `gpt-4o-mini`
            - `o1-mini`
- **Important Note on `o1-mini`**:
    - The `o1-mini` model is a significant addition as it is a thinking model.
        - It does not support system messages, so all prompts now consist solely of user sections.

#### Transition to Batch Schemas

- **Unified Schema Processing**:
    - Previously, Bulifier used both file-level and batch schemas for generating prompts.
    - **Now, all schemas are batch schemas**, enabling the processing and updating of multiple files at once.
- **Schema Updates**:
    - **Removed File-Level Schemas**:
        - The following file-level schemas have been removed:
            - `debulify-file`
            - `rebulify-file`
            - `update-bullet-with-raw`
            - `update-raw-with-bullet`
            - `update-schema`
    - **New and Enhanced Batch Schemas**:
        - **`bulify`**:
            - Now both creates and updates bullet point files in batch mode.
        - **`bulify_native`**:
            - Similar to `bulify` but operates on native files.
        - **`debulify`**:
            - Processes pairs of bullet and raw files, outputting updated or new raw files in batch.
        - **`debulify_native`**:
            - *Coming Soon*: Will sync raw files to bullet point files in batch mode.

#### Redesigned JS Console Screen

- **Complete Overhaul**:
    - The JavaScript console screen has been entirely redesigned with several new features:
        - Displays developer, system, and vendor logs.
        - Provides options to:
            - Clear webview, project, and app cache.
            - Start, stop, and restart the webview.
            - Run the AI agent directly from the console.

#### Improved NPM Support

- **Enhanced Dependency Resolution**:
    - Implemented a file candidates strategy to search the build folder of the tar file.
        - This approach finds the most compatible browser version of dependencies.
- **New Dependency Keyword**:
    - **`module`**:
        - You can use this keyword in the dependencies file to add `type="module"` to `<script>` tags.
        - Managed at the individual dependency level for greater control.

#### Parsing Enhancements

- **Improved Parsing Mechanisms**:
    - Worked extensively on parsing due to hallucination issues with the `o1-mini` model.
    - Added unit tests and centralized all parsers in a dedicated location.
    - Covered many more cases now, significantly improving reliability.

#### Git Integration

- **Auto Commit Feature**:
    - **Streamlined Git Workflow**:
        - Introduced an auto-commit option in the Git menu.
            - When enabled, agent and sync actions will automatically commit changes before running.
        - Makes it easy to revert changes directly from the Git menu if necessary.

## Release 2.2.19

- Updated items order in jobs scheduler, added `created` field and using that instead the
  `last_update`
- Added a revert view screen for git. You can revert to any commit from ui list
- Added autocommit option for agent, commit before agent is running
- Changed all the git notifications
    - added the branch to top toolbar
    - added color indication for progress, success and error
    - Removed the option to manually creating folders and files
- Added a new git_roots.settings file to manage sync with git. Only folders in the list are deleted
  before exporting db files to local git repo. This solve all the issues with deleting files
    - Added a shortcut to add folders to git_roots from folders menu. It will auto figure if child
      folders needs to be removed or parent folder already added and this request is redundant

## Release 2.1.05

- Updated schemas
    - updated bulify schema
        - added examples
        - using the name from the ai output instead of relying on settings
        - upending .bul to what ever ai gives back like game.py.bul
    - updated debulify schema
        - now it can generate output with multiple file types
            - no longer relying solely on output file extension from settings
            - if the file is with the structure of "xxxx.xxx.bul" it will remove the bul and use the
              remaining extension
- Improved ui
- Removed bottom bar
- Moved git to top bar
- Added clear action button to invoke AI
- Update GIT
    - clone as is - made it easier to work with existing projects by cloning them as is
    - Adjusted the project sync functionality - Instead of deleting all the local files
      it will delete only the files in folders used by the project

## Release 2.0.17

- switched to costume navigation system to better support cross module navigation
- Added hilt
- Temporary disabled the retry and reapply functionality since it is not fully implemented
    - To retry a query just start a new one
- Fixed a lot of bugs!

## Release 1.1

**New Features:**

- Introduced the ability to rebulify a single file.
- Added this release notes page. 😉
- Enabled full-path folder creation, eliminating the need to create packages one by one.
- Simplified AI interaction to a single button for schema prediction, with manual adjustment
  options.
- Added path information to the Job Scheduling screen.
- Enabled the option to secure specific model fields as passwords for easier screen sharing.

**Bug Fixes:**

- Resolved an issue where jobs from other projects appeared on the jobs screen.

## Release 1.0

Hello Bulifier - Project launched!
